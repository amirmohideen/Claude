package com.amirmohideen.foldfx.core.audio

import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow

/**
 * A door-hinge creak, synthesized live from how fast the hinge moves.
 *
 * A creak is stick-slip friction: the hinge grips, tension builds, it slips with a click, and grips
 * again. Each slip here is a click that rings a set of wood resonances. Moving slowly gives slow,
 * heavy slips: a loud, drawn-out groan. Moving fast lets the hinge glide, so the slips speed up
 * into a short, thin squeak.
 */
class CreakSynth(seed: Int = 0x5EED) {
    private val rng = Rng(seed)
    private val bank = Array(FREQUENCIES.size) { Resonator() }
    private val weights = FloatArray(FREQUENCIES.size)
    private val rateWander = Wander(rng, holdSeconds = 0.045f, glideSeconds = 0.07f)
    private val levelWander = Wander(rng, holdSeconds = 0.06f, glideSeconds = 0.09f)

    private var sampleRate = 48_000f
    private var speedGlide = 0f
    private var attack = 0f
    private var release = 0f
    private var speed = 0f
    private var glidePeak = 0f
    private var glideDecay = 0f
    private var level = 0f
    private var slipInterval = 1f
    private var skipChance = 0f
    private var untilSlip = 0f
    private var burst = 0f
    private var burstDecay = 0f

    fun prepare(sampleRate: Int) {
        this.sampleRate = sampleRate.toFloat()
        val controlRate = this.sampleRate / CONTROL_BLOCK
        speedGlide = onePole(0.03f, controlRate)
        attack = onePole(0.02f, controlRate)
        release = onePole(0.07f, controlRate)
        glideDecay = 1f - onePole(0.6f, controlRate)
        rateWander.prepare(controlRate)
        levelWander.prepare(controlRate)
        burstDecay = 1f - onePole(0.0012f, this.sampleRate)
        for (k in bank.indices) bank[k].tune(FREQUENCIES[k], Q[k], this.sampleRate)
    }

    /** Adds one buffer of creak to [out]. [speed] is the hinge speed in degrees per second; [angle] is where it is. */
    fun render(out: FloatArray, frames: Int, speed: Float, angle: Float) {
        var i = 0
        while (i < frames) {
            val end = min(i + CONTROL_BLOCK, frames)
            updateControls(speed, angle)
            while (i < end) out[i++] += nextSample()
        }
    }

    private fun updateControls(targetSpeed: Float, angle: Float) {
        speed += (targetSpeed - speed) * speedGlide
        // Once the hinge is gliding it keeps gliding while it slows, instead of groaning on the way to a stop.
        glidePeak = maxOf(speed, glidePeak * glideDecay)
        val friction = maxOf(speed, 0.7f * glidePeak)
        val stick = stickiness(angle)
        val onset = smoothstep(1.5f, 8f, speed)
        val falloff = 1f / (1f + (friction / 85f).pow(2f))
        val target = onset * falloff * stick * (1f + 0.35f * levelWander.next())
        level += (target - level) * if (target > level) attack else release

        val brightness = smoothstep(40f, 320f, friction)
        val rate = (24f + 2.1f * friction) * 2f.pow(0.45f * rateWander.next()) * (1.25f - 0.4f * stick)
        slipInterval = sampleRate / rate.coerceIn(15f, 1100f)
        skipChance = 0.15f * (1f - brightness)
        for (k in weights.indices) weights[k] = lerp(DULL[k], BRIGHT[k], brightness) * bank[k].clickGain
    }

    private fun nextSample(): Float {
        var x = 0f
        if (level > SILENT) {
            untilSlip -= 1f
            if (untilSlip <= 0f) {
                untilSlip += slipInterval * (1f + JITTER * rng.bipolar())
                if (rng.next() >= skipChance) {
                    val strength = level * (0.6f + 0.4f * rng.next())
                    x += strength
                    burst = strength * 0.6f
                }
            }
            x += level * HISS * rng.bipolar()
        } else {
            untilSlip = 0f
        }
        if (burst > SILENT) {
            x += burst * rng.bipolar()
            burst *= burstDecay
        }
        var y = 0f
        for (k in bank.indices) y += weights[k] * bank[k].process(x)
        return y * OUTPUT_GAIN
    }

    companion object {
        private const val CONTROL_BLOCK = 16
        private const val SILENT = 1e-4f
        private const val JITTER = 0.18f
        private const val HISS = 0.015f
        private const val OUTPUT_GAIN = 0.13f

        // Wood resonances, and how much each sounds when the creak is dull (slow) or bright (fast).
        private val FREQUENCIES = floatArrayOf(190f, 480f, 1050f, 1750f, 2700f, 3900f)
        private val Q = floatArrayOf(8f, 11f, 14f, 16f, 18f, 20f)
        private val DULL = floatArrayOf(1f, 1f, 0.8f, 0.5f, 0.3f, 0.15f)
        private val BRIGHT = floatArrayOf(0.2f, 0.5f, 0.8f, 1f, 0.9f, 0.6f)

        /**
         * How hard the hinge grips at [angle], from 0.35 to 1. It depends only on the angle, so the
         * same spots creak hardest every time, as they do on a real door.
         */
        fun stickiness(angle: Float): Float {
            val x = angle / 14f
            val cell = floor(x).toInt()
            val t = x - cell
            val s = t * t * (3f - 2f * t)
            return 0.35f + 0.65f * lerp(hash01(cell), hash01(cell + 1), s)
        }

        private fun hash01(i: Int): Float {
            var h = i * 0x27d4eb2d
            h = h xor (h ushr 15)
            h *= 0x2c1b3c6d
            h = h xor (h ushr 12)
            return (h ushr 8) * (1f / (1 shl 24))
        }
    }
}
