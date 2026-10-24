package com.amirmohideen.foldfx.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class FoldTraceTest {

    @Test
    fun `returns readings since a time, oldest first`() {
        val trace = FoldTrace(capacity = 8)
        for (i in 0 until 5) trace.add(i * 100L, i * 10f, i.toFloat())
        val snapshot = trace.since(200L)
        assertEquals(3, snapshot.count)
        assertArrayEquals(longArrayOf(200L, 300L, 400L), snapshot.times)
        assertArrayEquals(floatArrayOf(20f, 30f, 40f), snapshot.angles, 0f)
    }

    @Test
    fun `keeps only the newest readings once full`() {
        val trace = FoldTrace(capacity = 4)
        for (i in 0 until 10) trace.add(i.toLong(), i.toFloat(), 0f)
        val snapshot = trace.since(0L)
        assertArrayEquals(longArrayOf(6L, 7L, 8L, 9L), snapshot.times)
    }

    @Test
    fun `nothing recent gives an empty snapshot`() {
        val trace = FoldTrace(capacity = 4)
        trace.add(1L, 0f, 0f)
        assertEquals(0, trace.since(5L).count)
    }
}
