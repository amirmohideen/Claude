package com.amirmohideen.foldfx.core.audio

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** A door slamming into its frame: a low thud, the knock of the wood, and the latch clicking home. */
class SlamVoice(seed: Int = 0x51A3) {
    private val rng = Rng(seed)
    private val knockLow = Resonator()
    private val knockHigh = Resonator()
    private val latch = Resonator()
    private var sampleRate = 48_000f
    private var elapsed = -1
    private var loudness = 0f
    private var phase = 0f

    fun prepare(sampleRate: Int) {
        this.sampleRate = sampleRate.toFloat()
        knockLow.tune(240f, 1.4f, this.sampleRate)
        knockHigh.tune(720f, 2.2f, this.sampleRate)
        latch.tune(3300f, 7f, this.sampleRate)
    }

    /** Starts a slam; [intensity] from 0 (a gentle shut) to 1 (as hard as it gets). */
    fun trigger(intensity: Float) {
        elapsed = 0
        loudness = 0.3f + 0.7f * intensity.coerceIn(0f, 1f)
    }

    /** Adds one buffer of the slam, if one is sounding, to [out]. */
    fun render(out: FloatArray, frames: Int) {
        if (elapsed < 0) return
        for (i in 0 until frames) {
            val s = elapsed / sampleRate
            if (s > DURATION_SECONDS) {
                elapsed = -1
                return
            }
            // Starts high enough for phone speakers to carry, then sinks.
            phase += (80f + 120f * exp(-s / 0.04f)) / sampleRate
            if (phase >= 1f) phase -= 1f
            val body = sin(2f * PI.toFloat() * phase) * (1f - exp(-s / 0.002f)) * exp(-s / 0.12f)
            val noise = rng.bipolar()
            val knock = (knockLow.process(noise) + 0.6f * knockHigh.process(noise)) * exp(-s / 0.028f)
            val click = latch.process(noise * (click(s, 0.032f, 0.006f) + 0.55f * click(s, 0.074f, 0.004f)))
            out[i] += loudness * (BODY_GAIN * body + KNOCK_GAIN * knock + LATCH_GAIN * click)
            elapsed++
        }
    }

    private fun click(s: Float, at: Float, decay: Float) = if (s < at) 0f else exp(-(s - at) / decay)

    private companion object {
        const val DURATION_SECONDS = 0.7f
        const val BODY_GAIN = 1.4f
        const val KNOCK_GAIN = 9f
        const val LATCH_GAIN = 6f
    }
}
