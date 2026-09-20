package com.cocode.measureapp.capture

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.cocode.measureapp.capture.gravity.GravitySample
import com.cocode.measureapp.capture.gravity.GravitySampleDecoder
import com.cocode.measureapp.capture.gravity.GravitySampleHistory
import com.cocode.measureapp.capture.gravity.portraitCameraDownOrNull
import com.cocode.measureapp.geometry.Vec3

/**
 * Thin Android adapter over `TYPE_GRAVITY`. Each event is decoded by
 * [GravitySampleDecoder] into physical down in DEVICE axes (x right, y natural top,
 * z out of the screen) with its original `elapsedRealtimeNanos` event timestamp.
 * Missing sensor, no sample yet and invalid vectors are explicit [GravitySample.Unavailable].
 */
class GravityProvider(context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
    private val history = GravitySampleHistory(sensorPresent = sensor != null)

    fun start() {
        sensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    /** Latest reading: physical down in device axes, or why it is unavailable. */
    fun latestSample(): GravitySample = history.latest

    /** Recent valid samples, oldest first, for matching against an exposure timestamp. */
    fun recentSamples(): List<GravitySample.Available> = history.snapshot()

    /**
     * Legacy capture bridge (upright-portrait back-camera approximation, sign-corrected).
     * Node 02 must replace this with its device-to-image transform over [latestSample].
     * The upright placeholder for unavailable data remains only because the current engine
     * API requires a non-null vector; [latestSample] never reports it as a reading.
     */
    @Deprecated("Use latestSample() and the capture-frame transform (node 02).")
    fun current(): Vec3 = history.latest.portraitCameraDownOrNull() ?: Vec3(0.0, 1.0, 0.0)

    override fun onSensorChanged(event: SensorEvent) {
        history.record(GravitySampleDecoder.decode(event.values, event.timestamp))
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
