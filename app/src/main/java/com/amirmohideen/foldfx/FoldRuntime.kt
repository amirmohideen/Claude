package com.amirmohideen.foldfx

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.amirmohideen.foldfx.core.FoldEvent
import com.amirmohideen.foldfx.core.FoldState
import com.amirmohideen.foldfx.core.FoldTrace
import com.amirmohideen.foldfx.core.FoldTracker
import com.amirmohideen.foldfx.core.SensorStats
import com.amirmohideen.foldfx.core.audio.AudioSource
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Everything that outlives a single screen: the hinge sensor, what it has measured, and the
 * sound engine. Lives as long as the app process. Call its methods from the main thread unless
 * noted otherwise.
 */
class FoldRuntime(context: Context) {
    val tracker = FoldTracker()
    val stats = SensorStats()
    val trace = FoldTrace()
    val hinge = HingeSensor(context)
    val haptics = Haptics(context)
    private val audio = AudioEngine(context)
    private val listeners = CopyOnWriteArrayList<(FoldEvent) -> Unit>()
    private val handler = Handler(Looper.getMainLooper())
    private var scene: AudioSource? = null
    private var foreground = false

    // Closing the phone sends the app to the background while the last creak is still sounding,
    // so sound and sensor stop a moment later rather than cutting it off.
    private val shutDown = Runnable {
        audio.stop()
        hinge.stop()
    }

    /** The clock hinge readings are stamped with. */
    fun now(): Long = SystemClock.elapsedRealtimeNanos()

    /** Where the hinge is now. Safe to call from any thread. */
    fun state(): FoldState = tracker.stateAt(now())

    /** Takes one hinge reading, from the sensor or the on-screen stand-in. Safe to call from any thread. */
    fun feed(timeNanos: Long, angle: Float) {
        val event = tracker.onSample(timeNanos, angle)
        val velocity = tracker.stateAt(timeNanos).velocity
        stats.add(timeNanos, angle, velocity)
        trace.add(timeNanos, angle, velocity)
        if (event != null) listeners.forEach { it(event) }
    }

    /** Listens for slams, on whichever thread delivered the reading. Returns a function that stops listening. */
    fun addListener(listener: (FoldEvent) -> Unit): () -> Unit {
        listeners += listener
        return { listeners -= listener }
    }

    /** What to play while the app is in front, or null for silence. */
    fun setScene(scene: AudioSource?) {
        this.scene = scene
        when {
            scene == null -> audio.stop()
            foreground -> audio.play(scene)
        }
    }

    fun onForeground() {
        foreground = true
        handler.removeCallbacks(shutDown)
        hinge.start(::feed)
        scene?.let(audio::play)
    }

    fun onBackground() {
        foreground = false
        handler.postDelayed(shutDown, TAIL_MS)
    }

    private companion object {
        const val TAIL_MS = 1500L
    }
}
