package com.amirmohideen.foldfx.core

/** The last few seconds of hinge readings, for drawing graphs. Safe to call from any thread. */
class FoldTrace(private val capacity: Int = 4096) {

    /** Readings oldest first; only the first [count] entries of each array are used. */
    class Snapshot(
        val times: LongArray,
        val angles: FloatArray,
        val velocities: FloatArray,
        val count: Int,
    )

    private val times = LongArray(capacity)
    private val angles = FloatArray(capacity)
    private val velocities = FloatArray(capacity)
    private var head = 0
    private var size = 0

    @Synchronized
    fun add(timeNanos: Long, angle: Float, velocity: Float) {
        times[head] = timeNanos
        angles[head] = angle
        velocities[head] = velocity
        head = (head + 1) % capacity
        if (size < capacity) size++
    }

    /** Readings taken at or after [startNanos]. */
    @Synchronized
    fun since(startNanos: Long): Snapshot {
        var first = size
        for (i in 0 until size) {
            if (times[(head - size + i + capacity) % capacity] >= startNanos) {
                first = i
                break
            }
        }
        val count = size - first
        val outTimes = LongArray(count)
        val outAngles = FloatArray(count)
        val outVelocities = FloatArray(count)
        for (i in 0 until count) {
            val index = (head - count + i + capacity) % capacity
            outTimes[i] = times[index]
            outAngles[i] = angles[index]
            outVelocities[i] = velocities[index]
        }
        return Snapshot(outTimes, outAngles, outVelocities, count)
    }
}
