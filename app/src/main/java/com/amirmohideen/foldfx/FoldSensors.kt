package com.amirmohideen.foldfx

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread

/** The sensors that feel the fold, all read on one background thread so readings arrive in order. */
class FoldSensors(context: Context) {

    interface Listener {
        /** Hinge angle in degrees. Times are elapsed-realtime nanoseconds. */
        fun onHinge(timeNanos: Long, degrees: Float)

        /** Rotation in radians per second about the phone's x, y and z axes. */
        fun onGyro(timeNanos: Long, x: Float, y: Float, z: Float)

        /** Magnetic field in microtesla along x, y and z. */
        fun onMagnet(timeNanos: Long, x: Float, y: Float, z: Float)
    }

    private val sensorManager: SensorManager? = context.getSystemService(SensorManager::class.java)

    /** Null on phones that don't report a hinge angle. */
    val hinge: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)
    val gyroscope: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    val magnetometer: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    /** Every sensor on the phone, for the sensor lab's report. */
    val all: List<Sensor> = sensorManager?.getSensorList(Sensor.TYPE_ALL).orEmpty()

    private var thread: HandlerThread? = null
    private var listener: SensorEventListener? = null

    fun start(target: Listener) {
        val sensorManager = sensorManager ?: return
        if (listener != null) return
        val thread = HandlerThread("fold-sensors").apply { start() }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val v = event.values
                when (event.sensor.type) {
                    Sensor.TYPE_HINGE_ANGLE -> target.onHinge(event.timestamp, v[0])
                    Sensor.TYPE_GYROSCOPE -> target.onGyro(event.timestamp, v[0], v[1], v[2])
                    Sensor.TYPE_MAGNETIC_FIELD -> target.onMagnet(event.timestamp, v[0], v[1], v[2])
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        val handler = Handler(thread.looper)
        hinge?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_FASTEST, handler) }
        // 200 readings a second, the most Android allows apps without a special permission.
        gyroscope?.let { sensorManager.registerListener(listener, it, GYRO_PERIOD_US, handler) }
        magnetometer?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME, handler) }
        this.thread = thread
        this.listener = listener
    }

    fun stop() {
        listener?.let { sensorManager?.unregisterListener(it) }
        listener = null
        thread?.quitSafely()
        thread = null
    }

    private companion object {
        const val GYRO_PERIOD_US = 5_000
    }
}
