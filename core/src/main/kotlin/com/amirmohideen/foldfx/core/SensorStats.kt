package com.amirmohideen.foldfx.core

import kotlin.math.abs
import kotlin.math.round

/**
 * Measures how a phone's hinge sensor behaves: how often it reports, how fine its steps are,
 * and what range it covers. Feeds the sensor lab's report. Safe to call from any thread.
 */
class SensorStats(private val capacity: Int = 4096) {

    data class Summary(
        val readings: Int,
        /** Most readings seen within any one second. */
        val peakReadingsPerSecond: Int,
        /** Typical gap between readings while the hinge moves. */
        val medianIntervalMs: Float?,
        val p90IntervalMs: Float?,
        /** Smallest change between two readings that was not zero. */
        val smallestStepDegrees: Float?,
        val wholeDegreesOnly: Boolean,
        val minAngle: Float?,
        val maxAngle: Float?,
        val fastestDegreesPerSecond: Float,
    )

    private val times = LongArray(capacity)
    private val angles = FloatArray(capacity)
    private var head = 0
    private var size = 0
    private var total = 0
    private var minAngle = Float.POSITIVE_INFINITY
    private var maxAngle = Float.NEGATIVE_INFINITY
    private var smallestStep = Float.POSITIVE_INFINITY
    private var wholeDegreesOnly = true
    private var fastest = 0f

    @Synchronized
    fun add(timeNanos: Long, angle: Float, velocity: Float) {
        if (size > 0) {
            val step = abs(angle - angles[(head - 1 + capacity) % capacity])
            if (step > 0f && step < smallestStep) smallestStep = step
        }
        times[head] = timeNanos
        angles[head] = angle
        head = (head + 1) % capacity
        if (size < capacity) size++
        total++
        if (angle < minAngle) minAngle = angle
        if (angle > maxAngle) maxAngle = angle
        if (angle != round(angle)) wholeDegreesOnly = false
        if (abs(velocity) > fastest) fastest = abs(velocity)
    }

    @Synchronized
    fun reset() {
        head = 0
        size = 0
        total = 0
        minAngle = Float.POSITIVE_INFINITY
        maxAngle = Float.NEGATIVE_INFINITY
        smallestStep = Float.POSITIVE_INFINITY
        wholeDegreesOnly = true
        fastest = 0f
    }

    @Synchronized
    fun summary(): Summary {
        val start = (head - size + capacity) % capacity
        val intervals = ArrayList<Float>(size)
        var peak = 0
        var windowStart = 0
        for (i in 0 until size) {
            val t = times[(start + i) % capacity]
            if (i > 0) {
                val gapMs = (t - times[(start + i - 1) % capacity]) / 1_000_000f
                // Longer gaps are the hinge resting, not the sensor's pace.
                if (gapMs in 0f..MOVING_GAP_MS) intervals += gapMs
            }
            while (t - times[(start + windowStart) % capacity] >= 1_000_000_000L) windowStart++
            peak = maxOf(peak, i - windowStart + 1)
        }
        intervals.sort()
        return Summary(
            readings = total,
            peakReadingsPerSecond = peak,
            medianIntervalMs = intervals.percentile(0.5f),
            p90IntervalMs = intervals.percentile(0.9f),
            smallestStepDegrees = smallestStep.takeIf { it.isFinite() },
            wholeDegreesOnly = wholeDegreesOnly && total > 0,
            minAngle = minAngle.takeIf { it.isFinite() },
            maxAngle = maxAngle.takeIf { it.isFinite() },
            fastestDegreesPerSecond = fastest,
        )
    }

    private fun List<Float>.percentile(p: Float): Float? =
        if (isEmpty()) null else this[((size - 1) * p).toInt()]

    private companion object {
        const val MOVING_GAP_MS = 500f
    }
}
