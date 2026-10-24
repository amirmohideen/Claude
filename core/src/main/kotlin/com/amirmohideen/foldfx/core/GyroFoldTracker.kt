package com.amirmohideen.foldfx.core

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

/**
 * Follows the fold with the gyroscope, for phones whose hinge sensor only reports stops (Samsung
 * reports just 0, 90 and 180 to apps).
 *
 * The gyroscope sits in one half of the phone, so it feels the fold whenever that half swings.
 * The hinge's stops anchor the angle, and each time the hinge passes a stop they teach which axis
 * the hinge turns on and which way is closing. Safe to call from any thread.
 */
class GyroFoldTracker(private val slamSpeed: Float = 150f) {

    /** One time the hinge passed a stop, and what the gyroscope felt on the way. */
    data class Transition(
        val from: Float,
        val to: Float,
        /** Rotation the gyroscope felt about the hinge axis in the moments before. */
        val gyroDegrees: Float,
        /** Where the gyroscope put the angle just before the stop corrected it. */
        val estimateBefore: Float,
        /** How long the phone had been still when the stop arrived; about 0 if it was still moving. */
        val quietMs: Float,
    )

    private val times = LongArray(CAPACITY)
    private val xRates = FloatArray(CAPACITY)
    private val yRates = FloatArray(CAPACITY)
    private var head = 0
    private var size = 0

    private var posture = Float.NaN
    private var angle = FoldTracker.FLAT_DEGREES
    private var axis = AXIS_Y
    private val axisVotes = floatArrayOf(0f, 0f)
    private var signVote = 0f
    private var guessedSign = 0f
    private var hasGyro = false
    private var lastGyroTime = 0L
    private var lastMovingTime = 0L
    private var rate = 0f
    private var smoothedRate = 0f
    private var stillSeconds = 0f
    private var missedLastSwing = false
    private val transitions = ArrayDeque<Transition>()

    /** The last stop the hinge reported, or NaN before the first. */
    val lastStop: Float
        @Synchronized get() = posture

    /** True when the hinge moved but the gyroscope barely felt it: the other half was swung. */
    val missedSwing: Boolean
        @Synchronized get() = missedLastSwing

    /** True once a hinge stop has confirmed which way is closing. */
    val calibrated: Boolean
        @Synchronized get() = signVote != 0f

    val axisName: String
        @Synchronized get() = if (axis == AXIS_X) "x" else "y"

    @Synchronized
    fun transitions(): List<Transition> = transitions.toList()

    /** What has been learned, to keep between launches: x-axis votes, y-axis votes, closing-direction votes. */
    @Synchronized
    fun calibration() = floatArrayOf(axisVotes[0], axisVotes[1], signVote)

    @Synchronized
    fun restore(calibration: FloatArray) {
        if (calibration.size != 3) return
        axisVotes[0] = calibration[0]
        axisVotes[1] = calibration[1]
        signVote = calibration[2]
        axis = pickAxis()
    }

    /** One gyroscope reading, in radians per second, stamped on the same clock as the hinge. */
    @Synchronized
    fun onGyro(timeNanos: Long, x: Float, y: Float, @Suppress("UNUSED_PARAMETER") z: Float) {
        val xd = x * RAD_TO_DEG
        val yd = y * RAD_TO_DEG
        times[head] = timeNanos
        xRates[head] = xd
        yRates[head] = yd
        head = (head + 1) % CAPACITY
        if (size < CAPACITY) size++

        val dt = if (hasGyro) ((timeNanos - lastGyroTime) / NANOS_PER_SECOND).coerceIn(0f, MAX_GAP_SECONDS) else 0f
        hasGyro = true
        lastGyroTime = timeNanos
        rate = if (axis == AXIS_X) xd else yd
        smoothedRate += (rate - smoothedRate) * (1f - exp(-dt / SMOOTHING_SECONDS))
        if (abs(rate) > MOVING_SPEED) lastMovingTime = timeNanos

        // Until a stop confirms it: from flat the hinge can only close, and from shut only open.
        if (signVote == 0f && abs(rate) > GUESS_SPEED) {
            if (posture == FLAT) guessedSign = -sign(rate)
            if (posture == SHUT) guessedSign = sign(rate)
        }
        val direction = closingSign()
        if (direction != 0f) angle += direction * rate * dt
        angle = when (posture) {
            FLAT -> angle.coerceIn(MIN_WHILE_FLAT, FLAT)
            SHUT -> angle.coerceIn(SHUT, MAX_WHILE_SHUT)
            else -> angle.coerceIn(SHUT, FLAT)
        }

        // Gyroscopes drift a little, so at rest the angle settles onto the stop the hinge reports.
        stillSeconds = if (abs(rate) < STILL_SPEED) stillSeconds + dt else 0f
        if (stillSeconds > SETTLE_AFTER_SECONDS && (posture == FLAT || posture == SHUT)) {
            angle += (posture - angle) * (1f - exp(-dt / SETTLE_SECONDS))
        }
    }

    /** One hinge reading; returns a slam if this stop ended one. */
    @Synchronized
    fun onHinge(timeNanos: Long, value: Float): FoldEvent? {
        val previous = posture
        posture = value
        if (previous.isNaN()) {
            angle = value
            return null
        }
        if (value == previous) return null
        val closing = value < previous

        val window = timeNanos - FELT_WINDOW_NANOS
        val feltX = integrate(window, timeNanos, AXIS_X)
        val feltY = integrate(window, timeNanos, AXIS_Y)
        axisVotes[AXIS_X] += abs(feltX)
        axisVotes[AXIS_Y] += abs(feltY)
        axis = pickAxis()
        val felt = if (axis == AXIS_X) feltX else feltY
        missedLastSwing = abs(felt) < MIN_FELT_DEGREES
        if (!missedLastSwing) {
            val vote = if (closing) -sign(felt) else sign(felt)
            signVote += vote * min(abs(felt), 90f)
        }
        transitions.addLast(
            Transition(previous, value, felt, angle, quietMs = (timeNanos - lastMovingTime) / 1_000_000f),
        )
        while (transitions.size > MAX_TRANSITIONS) transitions.removeFirst()

        angle = when {
            value == FLAT || value == SHUT -> value
            // The gyroscope missed the swing, so all that's known is the hinge is part-way.
            missedLastSwing -> if (closing) MISSED_CLOSING else MISSED_OPENING
            closing -> min(angle, FLAT - 2f)
            else -> max(angle, SHUT + 2f)
        }

        val peak = peakSpeed(timeNanos - SLAM_WINDOW_NANOS, timeNanos)
        return when {
            value == FLAT && peak >= slamSpeed -> FoldEvent.Slam(HingeEnd.FLAT, peak)
            value == SHUT && peak >= slamSpeed -> FoldEvent.Slam(HingeEnd.SHUT, peak)
            else -> null
        }
    }

    @Synchronized
    fun stateAt(nowNanos: Long): FoldState {
        if (!hasGyro || nowNanos - lastGyroTime > STALE_NANOS) return FoldState(angle, 0f)
        // Ignore the slight sway of a hand holding the phone.
        val speed = max(abs(smoothedRate) - DEADZONE, 0f)
        val direction = when {
            closingSign() != 0f -> closingSign() * sign(smoothedRate)
            posture == FLAT -> -1f
            posture == SHUT -> 1f
            else -> sign(smoothedRate)
        }
        return FoldState(angle, direction * speed)
    }

    private fun closingSign() = if (signVote != 0f) sign(signVote) else guessedSign

    // The hinge runs along the long side, y, unless the stops clearly say otherwise.
    private fun pickAxis() = if (axisVotes[AXIS_X] > axisVotes[AXIS_Y] * 1.5f) AXIS_X else AXIS_Y

    /** Degrees turned about [axisIndex] between two times. */
    private fun integrate(fromNanos: Long, toNanos: Long, axisIndex: Int): Float {
        var total = 0f
        var previousTime = 0L
        for (i in 0 until size) {
            val index = (head - size + i + CAPACITY) % CAPACITY
            val t = times[index]
            if (t < fromNanos || t > toNanos) {
                previousTime = t
                continue
            }
            if (previousTime != 0L) {
                val dt = ((t - previousTime) / NANOS_PER_SECOND).coerceIn(0f, MAX_GAP_SECONDS)
                total += (if (axisIndex == AXIS_X) xRates[index] else yRates[index]) * dt
            }
            previousTime = t
        }
        return total
    }

    private fun peakSpeed(fromNanos: Long, toNanos: Long): Float {
        var peak = 0f
        for (i in 0 until size) {
            val index = (head - size + i + CAPACITY) % CAPACITY
            if (times[index] in fromNanos..toNanos) {
                peak = max(peak, abs(if (axis == AXIS_X) xRates[index] else yRates[index]))
            }
        }
        return peak
    }

    private companion object {
        const val AXIS_X = 0
        const val AXIS_Y = 1
        const val FLAT = 180f
        const val SHUT = 0f
        const val CAPACITY = 2048
        const val RAD_TO_DEG = (180.0 / Math.PI).toFloat()
        const val NANOS_PER_SECOND = 1_000_000_000f
        const val MAX_GAP_SECONDS = 0.05f
        const val SMOOTHING_SECONDS = 0.02f
        const val MOVING_SPEED = 30f
        const val GUESS_SPEED = 8f
        const val STILL_SPEED = 3f
        const val DEADZONE = 3f
        const val SETTLE_AFTER_SECONDS = 0.4f
        const val SETTLE_SECONDS = 0.4f
        const val MIN_WHILE_FLAT = 120f
        const val MAX_WHILE_SHUT = 30f
        const val MISSED_CLOSING = 110f
        const val MISSED_OPENING = 70f
        const val MIN_FELT_DEGREES = 12f
        const val MAX_TRANSITIONS = 10
        const val FELT_WINDOW_NANOS = 2_000_000_000L
        const val SLAM_WINDOW_NANOS = 500_000_000L
        const val STALE_NANOS = 100_000_000L
    }
}
