package com.cocode.measureapp.geometry

import com.cocode.measureapp.geometry.Tolerances.NORM_EPS
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2

/**
 * Level-overlay tilt relative to a level, square pose, in degrees.
 *
 * Overlay convention: [pitchDeg] is POSITIVE when the camera looks DOWN (toward the
 * floor; +90 = flat, screen up) and negative when it looks up. This is the opposite
 * sign of the engine's diagnostic `cameraTiltDeg` (looking up positive) and the two
 * must not be conflated. [rollDeg] is positive when view-down leans toward view-right.
 */
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
 * Converts physical down in DEVICE axes (x natural right, y natural top, z out of the
 * screen) into the current VIEW axes (x screen-right, y screen-down, z away from the user)
 * for display rotation [rotationDegrees] (0/90/180/270, `Display.getRotation`).
 *
 * `ROTATION_90` means the device was turned 90 degrees counter-clockwise, so its natural
 * top points left and device +x points up. This is the only place display rotation is
 * applied; it must never be applied to an already image-aligned measurement vector.
 */
fun viewDownFromDevice(deviceDown: Vec3, rotationDegrees: Int): Vec3 {
    val d = deviceDown
    return when (Math.floorMod(rotationDegrees, 360)) {
        0 -> Vec3(d.x, -d.y, -d.z)
        90 -> Vec3(-d.y, -d.x, -d.z)
        180 -> Vec3(-d.x, d.y, -d.z)
        270 -> Vec3(d.y, d.x, -d.z)
        else -> throw IllegalArgumentException("display rotation must be a quarter turn: $rotationDegrees")
    }
}

/**
 * Pitch and roll from physical down in VIEW axes (x right, y down, z forward), where
 * `(0, 1, 0)` is upright and level. Returns null (unavailable, never "level") for a
 * zero-length or non-finite vector. See [TiltAngles] for the sign convention.
 */
fun tiltFromGravity(viewDown: Vec3): TiltAngles? {
    if (!viewDown.x.isFinite() || !viewDown.y.isFinite() || !viewDown.z.isFinite()) return null
    val n = viewDown.norm()
    if (!n.isFinite() || n <= NORM_EPS) return null
    val g = viewDown * (1.0 / n)
    val roll = Math.toDegrees(asin(g.x.coerceIn(-1.0, 1.0)))
    val pitch = Math.toDegrees(atan2(g.z, g.y))
    return TiltAngles(pitchDeg = pitch, rollDeg = roll)
}

/** Overlay tilt straight from a device-axis physical-down sample; null when unavailable. */
fun levelTiltFromDeviceDown(deviceDown: Vec3, rotationDegrees: Int): TiltAngles? =
    tiltFromGravity(viewDownFromDevice(deviceDown, rotationDegrees))
