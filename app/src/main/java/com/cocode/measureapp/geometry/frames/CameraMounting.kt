package com.cocode.measureapp.geometry.frames

import com.cocode.measureapp.geometry.Vec3

enum class LensFacing { BACK, FRONT }

/**
 * How the bound camera sits in the phone: device axes (x natural right, y natural top, z out
 * of the screen) to camera-BUFFER axes (x-right, y-down, z-forward in the unrotated buffer).
 *
 * [sensorOrientation] is Camera2 `SENSOR_ORIENTATION`: the clockwise rotation that makes the
 * buffer upright in the device's natural orientation. This assumes the buffer is delivered
 * in sensor orientation (the HAL did not pre-rotate it); [explainsBufferRotation] checks that
 * against the rotation CameraX reported when the capture's target rotation is known.
 * Front-camera handling is derived, not verified on a physical device.
 */
data class CameraMounting(val facing: LensFacing, val sensorOrientation: QuarterTurn) {
    /** Device vector in the upright-in-natural-orientation camera frame. */
    private fun uprightNative(d: Vec3): Vec3 = when (facing) {
        LensFacing.BACK -> Vec3(d.x, -d.y, -d.z)   // lens looks out of the back
        LensFacing.FRONT -> Vec3(-d.x, -d.y, d.z)  // lens looks out of the screen, unmirrored
    }

    /** Device axes -> buffer camera axes. Applied once, before [FrameAlignment.cameraVector]. */
    fun deviceToBuffer(d: Vec3): Vec3 = sensorOrientation.inverse().vector(uprightNative(d))

    /**
     * The buffer rotation CameraX reports for a capture whose target rotation is
     * [targetRotationDegrees] (display `ROTATION_*` in degrees), if the buffer is in sensor
     * orientation.
     */
    fun expectedBufferRotation(targetRotationDegrees: Int): QuarterTurn? {
        val target = QuarterTurn.of(targetRotationDegrees) ?: return null
        return when (facing) {
            LensFacing.BACK -> sensorOrientation.then(target.inverse())
            LensFacing.FRONT -> sensorOrientation.then(target)
        }
    }

    /** False when the reported rotation contradicts the mounting (e.g. a pre-rotated JPEG). */
    fun explainsBufferRotation(reported: QuarterTurn, targetRotationDegrees: Int?): Boolean =
        targetRotationDegrees == null || expectedBufferRotation(targetRotationDegrees) == reported

    companion object {
        /** Camera2 `LENS_FACING_*` values, mirrored so this stays Android-free. */
        const val CAMERA2_FACING_FRONT = 0
        const val CAMERA2_FACING_BACK = 1

        /** Null when facing is external/unknown or the orientation is not a quarter turn. */
        fun of(camera2Facing: Int?, sensorOrientationDegrees: Int?): CameraMounting? {
            val facing = when (camera2Facing) {
                CAMERA2_FACING_BACK -> LensFacing.BACK
                CAMERA2_FACING_FRONT -> LensFacing.FRONT
                else -> return null
            }
            val o = sensorOrientationDegrees?.takeIf { it in 0..270 }?.let { QuarterTurn.of(it) } ?: return null
            return CameraMounting(facing, o)
        }
    }
}
