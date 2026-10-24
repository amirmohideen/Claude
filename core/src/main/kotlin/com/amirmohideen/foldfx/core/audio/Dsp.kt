package com.amirmohideen.foldfx.core.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/** Something that produces sound, pulled by the audio thread one buffer at a time. */
interface AudioSource {
    /** Called on the audio thread before the first [render]. */
    fun prepare(sampleRate: Int)

    /** Writes [frames] mono samples into [out], replacing what is there. */
    fun render(out: FloatArray, frames: Int)
}

/** 0 below [edge0], 1 above [edge1], and a smooth S-curve between. */
fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

internal fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

/** Per-step coefficient for a one-pole smoother with time constant [seconds], stepped [rate] times a second. */
internal fun onePole(seconds: Float, rate: Float) = 1f - exp(-1f / (seconds * rate))

/** Small, fast, seedable noise source (xorshift), so renders are repeatable in tests. */
internal class Rng(seed: Int) {
    private var state = if (seed == 0) 0x2545F491 else seed

    private fun nextInt(): Int {
        var x = state
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        state = x
        return x
    }

    /** Uniform in [0, 1). */
    fun next(): Float = (nextInt() ushr 8) * (1f / (1 shl 24))

    /** Uniform in [-1, 1). */
    fun bipolar(): Float = next() * 2f - 1f
}

/** Band-pass filter that rings at one frequency; unity gain at its peak. */
internal class Resonator {
    /** Multiplier that makes a single click set this ringing at about full scale, whatever its pitch. */
    var clickGain = 1f
        private set
    private var b0 = 0f
    private var a1 = 0f
    private var a2 = 0f
    private var x1 = 0f
    private var x2 = 0f
    private var y1 = 0f
    private var y2 = 0f

    fun tune(frequency: Float, q: Float, sampleRate: Float) {
        val w = 2.0 * PI * frequency / sampleRate
        val alpha = sin(w) / (2.0 * q)
        val a0 = 1.0 + alpha
        b0 = (alpha / a0).toFloat()
        a1 = (-2.0 * cos(w) / a0).toFloat()
        a2 = ((1.0 - alpha) / a0).toFloat()
        clickGain = 1f / b0
    }

    fun process(x: Float): Float {
        val y = b0 * (x - x2) - a1 * y1 - a2 * y2
        x2 = x1
        x1 = x
        y2 = y1
        y1 = y
        return y
    }
}

/** A value that drifts randomly within [-1, 1], picking a new target every so often and gliding to it. */
internal class Wander(private val rng: Rng, private val holdSeconds: Float, private val glideSeconds: Float) {
    private var value = 0f
    private var target = 0f
    private var countdown = 0f
    private var holdSteps = 1f
    private var glide = 1f

    /** [rate] is how many times a second [next] will be called. */
    fun prepare(rate: Float) {
        holdSteps = holdSeconds * rate
        glide = onePole(glideSeconds, rate)
    }

    fun next(): Float {
        countdown -= 1f
        if (countdown <= 0f) {
            target = rng.bipolar()
            countdown = holdSteps * (0.5f + rng.next())
        }
        value += (target - value) * glide
        return value
    }
}
