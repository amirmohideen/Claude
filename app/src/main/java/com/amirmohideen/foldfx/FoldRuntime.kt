package com.amirmohideen.foldfx

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.amirmohideen.foldfx.core.FieldByAngle
import com.amirmohideen.foldfx.core.FoldEvent
import com.amirmohideen.foldfx.core.FoldState
import com.amirmohideen.foldfx.core.FoldTrace
import com.amirmohideen.foldfx.core.FoldTracker
import com.amirmohideen.foldfx.core.GyroFoldTracker
import com.amirmohideen.foldfx.core.SensorStats
import com.amirmohideen.foldfx.core.audio.AudioSource
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Everything that outlives a single screen: the fold sensors, what they have measured, and the
 * sound engine. Lives as long as the app process. Call its methods from the main thread unless
 * noted otherwise.
 */
class FoldRuntime(context: Context) : FoldSensors.Listener {
    val sensors = FoldSensors(context)
    val haptics = Haptics(context)

    /** Follows phones whose hinge sensor reports every angle, like the Pixel Folds. */
    val tracker = FoldTracker()

    /** Follows phones whose hinge sensor only reports stops, like Samsung's, with the gyroscope. */
    val gyroTracker = GyroFoldTracker()
    val hingeStats = SensorStats()
    val gyroStats = SensorStats()
    val field = FieldByAngle()
    val trace = FoldTrace()

    private val audio = AudioEngine(context)
    private val prefs = context.getSharedPreferences("fold", Context.MODE_PRIVATE)
    private val listeners = CopyOnWriteArrayList<(FoldEvent) -> Unit>()
    private val handler = Handler(Looper.getMainLooper())
    private var scene: AudioSource? = null
    private var foreground = false

    /**
     * Whether the hinge sensor only reports stops. Samsung's reports 0, 90 and 180 to apps and keeps
     * the real angle for its own; any reading between stops proves a sensor reports every angle.
     */
    @Volatile
    var hingeReportsStopsOnly = sensors.hinge?.let {
        it.resolution >= COARSE_RESOLUTION || Build.MANUFACTURER.equals("samsung", ignoreCase = true)
    } ?: false
        private set

    /** True when the fold is being followed with the gyroscope. */
    val usingGyro: Boolean get() = hingeReportsStopsOnly && sensors.gyroscope != null

    // Closing the phone sends the app to the background while the last creak is still sounding,
    // so sound and sensors stop a moment later rather than cutting it off.
    private val shutDown = Runnable {
        audio.stop()
        sensors.stop()
        saveCalibration()
    }

    init {
        prefs.getString(CALIBRATION_KEY, null)
            ?.split(',')
            ?.mapNotNull { it.toFloatOrNull() }
            ?.let { gyroTracker.restore(it.toFloatArray()) }
    }

    /** The clock sensor readings are stamped with. */
    fun now(): Long = SystemClock.elapsedRealtimeNanos()

    /** Where the hinge is now. Safe to call from any thread. */
    fun state(): FoldState = stateAt(now())

    fun stateAt(nowNanos: Long): FoldState =
        if (usingGyro) gyroTracker.stateAt(nowNanos) else tracker.stateAt(nowNanos)

    /** Takes one hinge reading, from the sensor or the on-screen stand-in. Safe to call from any thread. */
    fun feed(timeNanos: Long, angle: Float) = onHinge(timeNanos, angle)

    override fun onHinge(timeNanos: Long, degrees: Float) {
        if (hingeReportsStopsOnly && degrees % STOP_DEGREES != 0f) hingeReportsStopsOnly = false
        val event = tracker.onSample(timeNanos, degrees)
        val gyroEvent = gyroTracker.onHinge(timeNanos, degrees)
        hingeStats.add(timeNanos, degrees, tracker.stateAt(timeNanos).velocity)
        if (!usingGyro) record(timeNanos)
        (if (usingGyro) gyroEvent else event)?.let { e -> listeners.forEach { it(e) } }
    }

    override fun onGyro(timeNanos: Long, x: Float, y: Float, z: Float) {
        gyroTracker.onGyro(timeNanos, x, y, z)
        if (usingGyro) {
            gyroStats.add(timeNanos, 0f, gyroTracker.stateAt(timeNanos).velocity)
            record(timeNanos)
        }
    }

    override fun onMagnet(timeNanos: Long, x: Float, y: Float, z: Float) {
        field.add(stateAt(timeNanos).angle, x, y, z)
    }

    private fun record(timeNanos: Long) {
        val state = stateAt(timeNanos)
        trace.add(timeNanos, state.angle, state.velocity)
    }

    /** Listens for slams, on the sensor thread. Returns a function that stops listening. */
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
        sensors.start(this)
        scene?.let(audio::play)
    }

    fun onBackground() {
        foreground = false
        handler.postDelayed(shutDown, TAIL_MS)
    }

    private fun saveCalibration() {
        prefs.edit().putString(CALIBRATION_KEY, gyroTracker.calibration().joinToString(",")).apply()
    }

    private companion object {
        const val TAIL_MS = 1500L
        const val COARSE_RESOLUTION = 10f
        const val STOP_DEGREES = 45f
        const val CALIBRATION_KEY = "gyro-calibration"
    }
}
