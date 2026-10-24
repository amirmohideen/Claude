package com.amirmohideen.foldfx.core.audio

import com.amirmohideen.foldfx.core.FoldEvent
import com.amirmohideen.foldfx.core.FoldState
import com.amirmohideen.foldfx.core.HingeEnd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class DoorSoundTest {

    private val sampleRate = 48_000
    private val block = 192

    /** Renders [seconds] of the door with the hinge moving at [speed], then [tail] seconds of stillness. */
    private fun renderSweep(speed: Float, seconds: Float, tail: Float = 0.3f): FloatArray {
        var angle = 180f
        var moving = true
        val scene = DoorSoundScene { FoldState(angle, if (moving) -speed else 0f) }
        scene.prepare(sampleRate)
        val total = ((seconds + tail) * sampleRate).toInt() / block * block
        val out = FloatArray(total)
        val buffer = FloatArray(block)
        var frame = 0
        while (frame < total) {
            moving = frame < seconds * sampleRate
            scene.render(buffer, block)
            buffer.copyInto(out, frame)
            if (moving) angle -= speed * block / sampleRate
            frame += block
        }
        return out
    }

    private fun FloatArray.rms(): Float = sqrt(sumOf { (it * it).toDouble() } / size).toFloat()
    private fun FloatArray.peak(): Float = maxOf { abs(it) }

    @Test
    fun `slow folds creak louder than fast ones`() {
        val slow = renderSweep(speed = 20f, seconds = 3f, tail = 0f)
        val fast = renderSweep(speed = 400f, seconds = 0.25f, tail = 0f)
        assertTrue("slow ${slow.rms()} vs fast ${fast.rms()}", slow.rms() > fast.rms() * 2.5f)
    }

    @Test
    fun `stays within full scale and never produces NaN`() {
        for (speed in listOf(3f, 20f, 60f, 150f, 400f, 900f)) {
            val out = renderSweep(speed, seconds = 1f)
            assertTrue("NaN at $speed", out.none { it.isNaN() })
            assertTrue("peak ${out.peak()} at $speed", out.peak() <= 1f)
        }
    }

    @Test
    fun `a still hinge is silent`() {
        val out = renderSweep(speed = 0f, seconds = 1f)
        assertEquals(0f, out.peak(), 1e-6f)
    }

    @Test
    fun `creak dies away after the hinge stops`() {
        val out = renderSweep(speed = 20f, seconds = 1f, tail = 1f)
        val lastTenth = out.copyOfRange(out.size - sampleRate / 10, out.size)
        assertTrue(lastTenth.peak() < 0.001f)
    }

    @Test
    fun `a slam is heard and harder slams are louder`() {
        fun slam(speed: Float): FloatArray {
            val scene = DoorSoundScene { FoldState(180f, 0f) }
            scene.prepare(sampleRate)
            scene.onEvent(FoldEvent.Slam(HingeEnd.FLAT, speed))
            val out = FloatArray(block * 100)
            val buffer = FloatArray(block)
            for (b in 0 until 100) {
                scene.render(buffer, block)
                buffer.copyInto(out, b * block)
            }
            return out
        }
        val soft = slam(160f)
        val hard = slam(900f)
        assertTrue(soft.peak() > 0.05f)
        assertTrue(hard.rms() > soft.rms() * 1.5f)
        assertTrue(hard.peak() <= 1f)
    }

    @Test
    fun `slams at the shut end are ignored by the door`() {
        val scene = DoorSoundScene { FoldState(0f, 0f) }
        scene.prepare(sampleRate)
        scene.onEvent(FoldEvent.Slam(HingeEnd.SHUT, 900f))
        val buffer = FloatArray(block)
        scene.render(buffer, block)
        assertEquals(0f, buffer.peak(), 0f)
    }

    @Test
    fun `stickiness stays in range and repeats by angle`() {
        for (a in 0..180) {
            val s = CreakSynth.stickiness(a.toFloat())
            assertTrue(s in 0.35f..1f)
            assertEquals(s, CreakSynth.stickiness(a.toFloat()), 0f)
        }
    }
}
