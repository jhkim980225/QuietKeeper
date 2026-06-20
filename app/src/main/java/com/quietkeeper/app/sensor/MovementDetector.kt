package com.quietkeeper.app.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Flags physical movement of the (ideally stationary) measurement device.
 * Accelerometer: magnitude deviating from gravity beyond a threshold.
 * Gyroscope: angular velocity beyond a threshold.
 * Measurement is never interrupted; this only sets [movedFlag].
 */
object MovementDetector : SensorEventListener {
    @Volatile var movedFlag = false
        private set

    private const val ACCEL_THRESHOLD = 1.5f   // m/s^2 deviation from gravity
    private const val GYRO_THRESHOLD = 0.8f     // rad/s

    private var sensorManager: SensorManager? = null

    fun start(ctx: Context) {
        stop()   // idempotent: drop any prior registration before re-registering
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorManager = sm
        sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let {
            sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
        sensorManager = null
    }

    fun reset() { movedFlag = false }

    override fun onSensorChanged(e: SensorEvent) {
        when (e.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val mag = sqrt(e.values[0]*e.values[0] + e.values[1]*e.values[1] + e.values[2]*e.values[2])
                if (abs(mag - SensorManager.GRAVITY_EARTH) > ACCEL_THRESHOLD) movedFlag = true
            }
            Sensor.TYPE_GYROSCOPE -> {
                val rot = sqrt(e.values[0]*e.values[0] + e.values[1]*e.values[1] + e.values[2]*e.values[2])
                if (rot > GYRO_THRESHOLD) movedFlag = true
            }
        }
    }

    override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
}
