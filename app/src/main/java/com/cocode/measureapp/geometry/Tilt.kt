package com.cocode.measureapp.geometry

import com.cocode.measureapp.geometry.Tolerances.NORM_EPS
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2

/** Device tilt relative to a level, square pose, in degrees. */
data class TiltAngles(val pitchDeg: Double, val rollDeg: Double) {
    /**
     * True when the device is square to a surface: no sideways roll, and either
     * upright (pitch ~ 0) or flat (pitch ~ +/-90). [toleranceDeg] sets how strict.
     */
    fun isLevel(toleranceDeg: Double = 1.0): Boolean {
        if (abs(rollDeg) > toleranceDeg) return false
        return abs(pitchDeg) <= toleranceDeg || abs(abs(pitchDeg) - 90.0) <= toleranceDeg
    }
}

/**
 * Pitch (lean forward/back) and roll (sideways tilt) from a gravity direction in the
 * camera-frame approximation (x right, y down, z forward), where (0, 1, 0) is upright.
 * A zero-length input falls back to the upright reading.
 */
fun tiltFromGravity(gravity: Vec3): TiltAngles {
    val n = gravity.norm()
    val g = if (n > NORM_EPS) gravity * (1.0 / n) else Vec3(0.0, 1.0, 0.0)
    val roll = Math.toDegrees(asin(g.x.coerceIn(-1.0, 1.0)))
    val pitch = Math.toDegrees(atan2(g.z, g.y))
    return TiltAngles(pitchDeg = pitch, rollDeg = roll)
}
