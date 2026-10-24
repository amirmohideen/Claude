package com.amirmohideen.foldfx.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GyroFoldTrackerTest {

    /**
     * A phone whose gyroscope reads [yPerClosingDegree] degrees per second about y for every degree
     * per second of closing, with hinge stops at 0, 90 and 180 reported when the angle crosses
     * [halfOpenBelow] or [shutBelow].
     */
    private class Phone(
        val tracker: GyroFoldTracker = GyroFoldTracker(),
        val yPerClosingDegree: Float = -1f,
        val halfOpenBelow: Float = 160f,
        val shutBelow: Float = 5f,
    ) {
        var t = 0L
        var angle = 180f
        val events = mutableListOf<FoldEvent>()
        private var stop = 180f

        init {
            tracker.onHinge(t, stop)
        }

        /** Moves the hinge to [target] at [speed] degrees per second, with gyroscope readings at 200 Hz. */
        fun fold(target: Float, speed: Float, swingGyroHalf: Boolean = true) {
            val step = 5_000_000L
            val direction = if (target < angle) -1f else 1f
            while (direction * (target - angle) > 0f) {
                angle = (angle + direction * speed * 0.005f).let { if (direction * (it - target) > 0f) target else it }
                t += step
                val closingRate = if (swingGyroHalf) -direction * speed else 0f
                tracker.onGyro(t, 0f, Math.toRadians((closingRate * yPerClosingDegree).toDouble()).toFloat(), 0f)
                report()
            }
        }

        fun rest(seconds: Float) {
            val steps = (seconds / 0.005f).toInt()
            repeat(steps) {
                t += 5_000_000L
                tracker.onGyro(t, 0f, 0f, 0f)
            }
        }

        private fun report() {
            val now = when {
                angle <= shutBelow -> 0f
                angle < halfOpenBelow -> 90f
                else -> 180f
            }
            if (now != stop) {
                stop = now
                tracker.onHinge(t, now)?.let(events::add)
            }
        }

        fun state() = tracker.stateAt(t)
    }

    @Test
    fun `follows a slow fold from flat before any stop has calibrated it`() {
        val phone = Phone()
        phone.fold(target = 165f, speed = 20f)
        val state = phone.state()
        assertEquals(165f, state.angle, 2f)
        assertEquals(-17f, state.velocity, 2f)
        assertEquals(FoldDirection.CLOSING, state.direction)
    }

    @Test
    fun `passing a stop calibrates direction and axis, then the angle keeps following`() {
        val phone = Phone(yPerClosingDegree = 1f)
        phone.fold(target = 120f, speed = 30f)
        assertTrue(phone.tracker.calibrated)
        assertEquals("y", phone.tracker.axisName)
        assertEquals(120f, phone.state().angle, 3f)
        phone.fold(target = 150f, speed = 30f)
        assertEquals(150f, phone.state().angle, 3f)
        assertEquals(FoldDirection.OPENING, phone.state().direction)
    }

    @Test
    fun `learns the hinge turns on x when that is what the gyroscope feels`() {
        val tracker = GyroFoldTracker()
        tracker.onHinge(0, 180f)
        var t = 0L
        repeat(200) {
            t += 5_000_000L
            tracker.onGyro(t, Math.toRadians(-40.0).toFloat(), 0.01f, 0f)
        }
        tracker.onHinge(t, 90f)
        assertEquals("x", tracker.axisName)
    }

    @Test
    fun `speed follows the gyroscope and stops when it does`() {
        val phone = Phone()
        phone.fold(target = 140f, speed = 200f)
        assertTrue(phone.state().speed > 150f)
        phone.rest(0.1f)
        assertEquals(0f, phone.state().speed, 0f)
    }

    @Test
    fun `a hand's slight sway does not count as folding`() {
        val phone = Phone()
        phone.fold(target = 179f, speed = 2.5f)
        assertEquals(0f, phone.state().speed, 0f)
    }

    @Test
    fun `opening flat fast slams`() {
        val phone = Phone()
        phone.fold(target = 90f, speed = 60f)
        phone.rest(1f)
        phone.fold(target = 180f, speed = 500f)
        val slam = phone.events.single() as FoldEvent.Slam
        assertEquals(HingeEnd.FLAT, slam.end)
        assertTrue(slam.speed > 400f)
    }

    @Test
    fun `opening flat slowly does not slam`() {
        val phone = Phone()
        phone.fold(target = 90f, speed = 60f)
        phone.rest(1f)
        phone.fold(target = 180f, speed = 40f)
        assertTrue(phone.events.isEmpty())
    }

    @Test
    fun `swinging the other half is noticed`() {
        val phone = Phone()
        phone.fold(target = 100f, speed = 60f, swingGyroHalf = false)
        assertTrue(phone.tracker.missedSwing)
        assertEquals(110f, phone.state().angle, 0f)
        assertFalse(phone.tracker.calibrated)
    }

    @Test
    fun `settles back onto the flat stop at rest`() {
        val tracker = GyroFoldTracker()
        tracker.onHinge(0, 180f)
        var t = 0L
        // Turning the whole phone while it stays flat looks like a fold to the gyroscope.
        repeat(100) {
            t += 5_000_000L
            tracker.onGyro(t, 0f, Math.toRadians(-40.0).toFloat(), 0f)
        }
        assertTrue(tracker.stateAt(t).angle < 175f)
        repeat(600) {
            t += 5_000_000L
            tracker.onGyro(t, 0f, 0f, 0f)
        }
        assertEquals(180f, tracker.stateAt(t).angle, 1f)
    }

    @Test
    fun `calibration carries over`() {
        val phone = Phone(yPerClosingDegree = 1f)
        phone.fold(target = 120f, speed = 30f)
        val saved = phone.tracker.calibration()
        val fresh = GyroFoldTracker()
        fresh.restore(saved)
        assertTrue(fresh.calibrated)
    }

    @Test
    fun `without a gyroscope reading the hinge stops are all it knows`() {
        val tracker = GyroFoldTracker()
        assertNull(tracker.onHinge(0, 90f))
        assertEquals(90f, tracker.stateAt(1).angle, 0f)
        assertEquals(0f, tracker.stateAt(1).velocity, 0f)
    }
}
