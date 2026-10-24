package com.amirmohideen.foldfx.core

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

enum class FoldDirection { OPENING, CLOSING, STILL }

/** The two hard stops of the hinge. */
enum class HingeEnd { FLAT, SHUT }

/**
 * The hinge at one moment. [angle] is in degrees, 0 when shut and 180 when flat.
 * [velocity] is in degrees per second, positive while opening.
 */
data class FoldState(val angle: Float, val velocity: Float) {
    val speed: Float get() = abs(velocity)

    val direction: FoldDirection
        get() = when {
            velocity > STILL_SPEED -> FoldDirection.OPENING
            velocity < -STILL_SPEED -> FoldDirection.CLOSING
            else -> FoldDirection.STILL
        }

    companion object {
        /** Below this many degrees per second the hinge counts as still. */
        const val STILL_SPEED = 2f
    }
}

sealed interface FoldEvent {
    /** The hinge reached [end] while moving at [speed] degrees per second. */
    data class Slam(val end: HingeEnd, val speed: Float) : FoldEvent
}

/**
 * Turns raw hinge-angle readings into angle, speed and slams.
 *
 * Hinge sensors report on change, often in whole degrees, so readings arrive unevenly and stop
 * arriving when the hinge stops. Speed is smoothed between readings, and [stateAt] lets it fall
 * toward zero once readings go quiet for longer than the sensor's resolution and pace explain.
 * Safe to call from any thread.
 */
class FoldTracker(
    private val flatAngle: Float = 170f,
    private val shutAngle: Float = 10f,
    private val slamSpeed: Float = 150f,
) {
    private var hasReading = false
    private var lastTime = 0L
    private var lastAngle = FLAT_DEGREES
    private var velocity = 0f
    private var resolution = 1f
    private var pace = 0.02f
    private var flatArmed = true
    private var shutArmed = true

    /** Records one reading and returns a slam if this reading completed one. */
    @Synchronized
    fun onSample(timeNanos: Long, angle: Float): FoldEvent? {
        if (!hasReading) {
            hasReading = true
            lastTime = timeNanos
            lastAngle = angle
            return null
        }
        val dtNanos = timeNanos - lastTime
        if (dtNanos <= 0L) {
            lastAngle = angle
            return null
        }
        val dt = dtNanos / NANOS_PER_SECOND
        val step = angle - lastAngle
        // After a pause the move started somewhere inside the gap, so don't spread it over all of it.
        // A jump of several resolution steps can't have been slow, or the sensor would have reported
        // the steps in between.
        val moveTime = if (abs(step) > 1.5f * resolution) min(dt, 2f * pace) else dt
        val raw = step / min(moveTime, MAX_STEP_SECONDS)
        velocity += (raw - velocity) * (1f - exp(-dt / SMOOTHING_SECONDS))
        if (step != 0f) resolution = min(resolution, max(abs(step), MIN_RESOLUTION))
        // The sensor's fastest reporting pace: drops quickly to short gaps, creeps up slowly.
        pace = if (dt < pace) (pace + dt) / 2f else pace + (min(dt, MAX_PACE) - pace) * 0.02f
        val previous = lastAngle
        lastTime = timeNanos
        lastAngle = angle
        return detectSlam(previous, angle)
    }

    /** The hinge as of [nowNanos], on the same clock as the readings. */
    @Synchronized
    fun stateAt(nowNanos: Long): FoldState {
        if (!hasReading) return FoldState(FLAT_DEGREES, 0f)
        val gap = (nowNanos - lastTime) / NANOS_PER_SECOND - DELIVERY_GRACE_SECONDS
        if (gap <= pace * PACE_SLACK) return FoldState(lastAngle, velocity)
        if (gap > STOPPED_AFTER_SECONDS) return FoldState(lastAngle, 0f)
        // The sensor reports every change of one resolution step, so no reading for this long means
        // the hinge is moving slower than one step per gap, if at all.
        val bound = resolution / gap
        return FoldState(lastAngle, velocity.coerceIn(-bound, bound))
    }

    private fun detectSlam(previous: Float, angle: Float): FoldEvent? {
        if (angle < flatAngle - REARM_DEGREES) flatArmed = true
        if (angle > shutAngle + REARM_DEGREES) shutArmed = true
        if (flatArmed && previous < flatAngle && angle >= flatAngle && velocity >= slamSpeed) {
            flatArmed = false
            return FoldEvent.Slam(HingeEnd.FLAT, velocity)
        }
        if (shutArmed && previous > shutAngle && angle <= shutAngle && -velocity >= slamSpeed) {
            shutArmed = false
            return FoldEvent.Slam(HingeEnd.SHUT, -velocity)
        }
        return null
    }

    companion object {
        const val FLAT_DEGREES = 180f
        private const val NANOS_PER_SECOND = 1_000_000_000f
        private const val SMOOTHING_SECONDS = 0.03f
        private const val MAX_STEP_SECONDS = 0.12f
        private const val DELIVERY_GRACE_SECONDS = 0.01f
        private const val STOPPED_AFTER_SECONDS = 0.25f
        private const val MIN_RESOLUTION = 0.01f
        private const val MAX_PACE = 0.1f
        private const val PACE_SLACK = 1.3f
        private const val REARM_DEGREES = 10f
    }
}
