package com.cocode.measureapp.capture

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.cocode.measureapp.geometry.Vec3

/**
 * Tracks the device gravity vector and exposes it in the camera frame approximation
 * (x right, y down, z forward). Until a reading arrives it reports straight down,
 * matching a level camera. The exact sensor->camera mapping depends on device
 * orientation and is a TODO to refine on a real device.
 */
class GravityProvider(context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)

    @Volatile
    private var latest = Vec3(0.0, 1.0, 0.0)

    fun start() {
        sensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    /** Latest gravity direction (unit) in the camera frame approximation. */
    fun current(): Vec3 = latest

    override fun onSensorChanged(event: SensorEvent) {
        // Sensor frame is x right, y up, z out of the screen; the camera frame uses
        // y down and z forward, so negate y and z.
        val g = Vec3(
            event.values[0].toDouble(),
            -event.values[1].toDouble(),
            -event.values[2].toDouble(),
        )
        val n = g.norm()
        if (n > 1e-6) latest = g * (1.0 / n)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
