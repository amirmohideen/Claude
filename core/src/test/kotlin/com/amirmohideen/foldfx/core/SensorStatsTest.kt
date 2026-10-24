package com.amirmohideen.foldfx.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorStatsTest {

    @Test
    fun `measures pace, step size and range`() {
        val stats = SensorStats()
        for (i in 0..100) stats.add(i * 20_000_000L, 90f + i, velocity = 50f)
        val summary = stats.summary()
        assertEquals(101, summary.readings)
        assertEquals(20f, summary.medianIntervalMs!!, 0.01f)
        assertEquals(50, summary.peakReadingsPerSecond)
        assertEquals(1f, summary.smallestStepDegrees!!, 0f)
        assertTrue(summary.wholeDegreesOnly)
        assertEquals(90f, summary.minAngle!!, 0f)
        assertEquals(190f, summary.maxAngle!!, 0f)
        assertEquals(50f, summary.fastestDegreesPerSecond, 0f)
    }

    @Test
    fun `resting gaps do not count toward the pace`() {
        val stats = SensorStats()
        stats.add(0, 10f, 0f)
        stats.add(10_000_000L, 10.5f, 0f)
        stats.add(5_000_000_000L, 11f, 0f)
        val summary = stats.summary()
        assertEquals(10f, summary.medianIntervalMs!!, 0.01f)
        assertFalse(summary.wholeDegreesOnly)
        assertEquals(0.5f, summary.smallestStepDegrees!!, 0f)
    }

    @Test
    fun `reset clears everything`() {
        val stats = SensorStats()
        stats.add(0, 10f, 5f)
        stats.reset()
        val summary = stats.summary()
        assertEquals(0, summary.readings)
        assertNull(summary.medianIntervalMs)
        assertNull(summary.minAngle)
        assertFalse(summary.wholeDegreesOnly)
    }
}
