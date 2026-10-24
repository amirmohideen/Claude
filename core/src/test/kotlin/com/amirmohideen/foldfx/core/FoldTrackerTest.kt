package com.amirmohideen.foldfx.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FoldTrackerTest {

    /** Feeds readings every [intervalMs] while the hinge moves from [from] to [to] at [speed] deg/s. */
    private fun FoldTracker.sweep(
        from: Float,
        to: Float,
        speed: Float,
        intervalMs: Long,
        startMs: Long = 0,
        wholeDegrees: Boolean = false,
    ): Pair<Long, List<FoldEvent>> {
        val events = mutableListOf<FoldEvent>()
        val direction = if (to > from) 1f else -1f
        var t = startMs
        var angle = from
        var lastReported = Float.NaN
        while (direction * (to - angle) > 0f) {
            angle = (angle + direction * speed * intervalMs / 1000f).let { if (direction * (it - to) > 0f) to else it }
            t += intervalMs
            val reported = if (wholeDegrees) kotlin.math.round(angle) else angle
            // On-change sensors stay quiet until the reported value changes.
            if (reported != lastReported) {
                onSample(t * MS, reported)?.let(events::add)
                lastReported = reported
            }
        }
        return t to events
    }

    @Test
    fun `tracks a steady fold`() {
        val tracker = FoldTracker()
        tracker.onSample(0, 180f)
        val (end, _) = tracker.sweep(from = 180f, to = 90f, speed = 30f, intervalMs = 20)
        val state = tracker.stateAt(end * MS)
        assertEquals(90f, state.angle, 0.01f)
        assertEquals(-30f, state.velocity, 3f)
        assertEquals(FoldDirection.CLOSING, state.direction)
    }

    @Test
    fun `slow whole-degree readings still give a speed between steps`() {
        val tracker = FoldTracker()
        tracker.onSample(0, 100f)
        val (end, _) = tracker.sweep(from = 100f, to = 120f, speed = 10f, intervalMs = 10, wholeDegrees = true)
        // Readings arrive only every ~100 ms here; halfway between two of them the hinge is still moving.
        val state = tracker.stateAt((end + 50) * MS)
        assertTrue("speed ${state.velocity}", state.velocity in 5f..20f)
    }

    @Test
    fun `speed falls to zero once readings stop`() {
        val tracker = FoldTracker()
        tracker.onSample(0, 180f)
        val (end, _) = tracker.sweep(from = 180f, to = 120f, speed = 200f, intervalMs = 10)
        assertTrue(tracker.stateAt(end * MS).speed > 150f)
        assertTrue(tracker.stateAt((end + 60) * MS).speed < 50f)
        assertEquals(0f, tracker.stateAt((end + 400) * MS).speed, 0f)
    }

    @Test
    fun `opening flat fast is a slam`() {
        val tracker = FoldTracker()
        tracker.onSample(0, 120f)
        val (_, events) = tracker.sweep(from = 120f, to = 180f, speed = 600f, intervalMs = 16)
        val slam = events.single() as FoldEvent.Slam
        assertEquals(HingeEnd.FLAT, slam.end)
        assertTrue("speed ${slam.speed}", slam.speed > 400f)
    }

    @Test
    fun `opening flat slowly is not a slam`() {
        val tracker = FoldTracker()
        tracker.onSample(0, 120f)
        val (_, events) = tracker.sweep(from = 120f, to = 180f, speed = 40f, intervalMs = 16)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `closing fast is a slam at the shut end`() {
        val tracker = FoldTracker()
        tracker.onSample(0, 60f)
        val (_, events) = tracker.sweep(from = 60f, to = 0f, speed = 500f, intervalMs = 16)
        assertEquals(HingeEnd.SHUT, (events.single() as FoldEvent.Slam).end)
    }

    @Test
    fun `a coarse sensor that jumps straight to flat still slams`() {
        val tracker = FoldTracker()
        tracker.onSample(0, 110f)
        assertNull(tracker.onSample(500 * MS, 110f))
        val event = tracker.onSample(600 * MS, 180f) as FoldEvent.Slam
        assertEquals(HingeEnd.FLAT, event.end)
    }

    @Test
    fun `slams once per arrival and re-arms after moving away`() {
        val tracker = FoldTracker()
        tracker.onSample(0, 120f)
        var (t, events) = tracker.sweep(from = 120f, to = 180f, speed = 600f, intervalMs = 16)
        assertEquals(1, events.size)
        val wobble = tracker.sweep(from = 180f, to = 166f, speed = 600f, intervalMs = 16, startMs = t)
        t = wobble.first
        val back = tracker.sweep(from = 166f, to = 180f, speed = 600f, intervalMs = 16, startMs = t)
        assertTrue("a small wobble should not slam again", back.second.isEmpty())
        val away = tracker.sweep(from = 180f, to = 120f, speed = 300f, intervalMs = 16, startMs = back.first)
        val again = tracker.sweep(from = 120f, to = 180f, speed = 600f, intervalMs = 16, startMs = away.first)
        assertEquals(1, again.second.size)
    }

    @Test
    fun `before any reading the hinge reads flat and still`() {
        val state = FoldTracker().stateAt(123)
        assertEquals(180f, state.angle, 0f)
        assertEquals(0f, state.velocity, 0f)
    }

    private companion object {
        const val MS = 1_000_000L
    }
}
