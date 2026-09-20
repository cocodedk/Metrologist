package com.cocode.measureapp.capture.gravity

import com.cocode.measureapp.geometry.TiltAngles
import com.cocode.measureapp.geometry.levelTiltFromDeviceDown

/** What the level overlay shows: a measured tilt, or an explicit unavailable state. */
sealed interface LevelReading {
    data class Tilt(val angles: TiltAngles) : LevelReading
    data class Unavailable(val reason: GravityUnavailableReason) : LevelReading

    /** Green only for a real, level reading; unavailable is never level. */
    fun isLevel(toleranceDeg: Double = 1.0): Boolean = this is Tilt && angles.isLevel(toleranceDeg)
}

/** Raw provider sample to overlay state for the current display rotation. */
fun levelReadingOf(sample: GravitySample, rotationDegrees: Int): LevelReading = when (sample) {
    is GravitySample.Unavailable -> LevelReading.Unavailable(sample.reason)
    is GravitySample.Available -> levelTiltFromDeviceDown(sample.down, rotationDegrees)
        ?.let { LevelReading.Tilt(it) }
        ?: LevelReading.Unavailable(GravityUnavailableReason.INVALID_VECTOR)
}
