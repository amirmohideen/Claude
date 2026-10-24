package com.amirmohideen.foldfx.core.audio

import com.amirmohideen.foldfx.core.FoldEvent
import com.amirmohideen.foldfx.core.FoldState
import com.amirmohideen.foldfx.core.HingeEnd
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.tanh

/**
 * Sound for the creaky door: the creak follows the hinge continuously, and opening the phone flat
 * fast slams the door shut. [fold] is read once per audio buffer.
 */
class DoorSoundScene(private val fold: () -> FoldState) : AudioSource {
    private val creak = CreakSynth()
    private val slam = SlamVoice()
    private val slams = ConcurrentLinkedQueue<Float>()

    /** Call from any thread. */
    fun onEvent(event: FoldEvent) {
        if (event is FoldEvent.Slam && event.end == HingeEnd.FLAT) slams.add(slamIntensity(event.speed))
    }

    override fun prepare(sampleRate: Int) {
        creak.prepare(sampleRate)
        slam.prepare(sampleRate)
    }

    override fun render(out: FloatArray, frames: Int) {
        out.fill(0f, 0, frames)
        while (true) slam.trigger(slams.poll() ?: break)
        val state = fold()
        creak.render(out, frames, state.speed, state.angle)
        slam.render(out, frames)
        for (i in 0 until frames) out[i] = tanh(out[i])
    }

    companion object {
        /** How hard a slam at [speed] degrees per second hits, from 0 to 1. */
        fun slamIntensity(speed: Float) = smoothstep(150f, 900f, speed)
    }
}
