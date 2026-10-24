package com.amirmohideen.foldfx.core

import kotlin.math.sqrt

/**
 * Magnetic field strength seen at each fold angle, in 15° buckets. The magnetometer sits in one
 * half, and the magnets and metal in the other half come closer as the phone folds, so the field
 * may reveal the angle whichever half moves. This collects the evidence. Safe to call from any thread.
 */
class FieldByAngle {
    private val min = FloatArray(BUCKETS) { Float.POSITIVE_INFINITY }
    private val max = FloatArray(BUCKETS) { Float.NEGATIVE_INFINITY }

    /** [x], [y] and [z] in microtesla, seen at fold [angle] in degrees. */
    @Synchronized
    fun add(angle: Float, x: Float, y: Float, z: Float) {
        val bucket = (angle / BUCKET_DEGREES + 0.5f).toInt().coerceIn(0, BUCKETS - 1)
        val strength = sqrt(x * x + y * y + z * z)
        if (strength < min[bucket]) min[bucket] = strength
        if (strength > max[bucket]) max[bucket] = strength
    }

    @Synchronized
    fun reset() {
        min.fill(Float.POSITIVE_INFINITY)
        max.fill(Float.NEGATIVE_INFINITY)
    }

    /** "angle: low–high µT" for each angle seen, widest open first. */
    @Synchronized
    fun summary(): List<String> = (BUCKETS - 1 downTo 0)
        .filter { min[it].isFinite() }
        .map { "${(it * BUCKET_DEGREES).toInt()}°: ${min[it].toInt()}–${max[it].toInt()} µT" }

    private companion object {
        const val BUCKET_DEGREES = 15f
        const val BUCKETS = 13
    }
}
