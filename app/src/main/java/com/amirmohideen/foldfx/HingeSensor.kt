package com.amirmohideen.foldfx

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread

/** The phone's hinge-angle sensor, read on its own thread. */
class HingeSensor(context: Context) {
    private val sensorManager: SensorManager? = context.getSystemService(SensorManager::class.java)

    /** Null on phones that don't report a hinge angle. */
    val sensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)

    private var thread: HandlerThread? = null
    private var listener: SensorEventListener? = null

    /** Starts delivering readings, as (timestamp in elapsed-realtime nanos, degrees), on a background thread. */
    fun start(onReading: (Long, Float) -> Unit) {
        val sensor = sensor ?: return
        val sensorManager = sensorManager ?: return
        if (listener != null) return
        val thread = HandlerThread("hinge-angle").apply { start() }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) = onReading(event.timestamp, event.values[0])
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_FASTEST, Handler(thread.looper))
        this.thread = thread
        this.listener = listener
    }

    fun stop() {
        listener?.let { sensorManager?.unregisterListener(it) }
        listener = null
        thread?.quitSafely()
        thread = null
    }
}
