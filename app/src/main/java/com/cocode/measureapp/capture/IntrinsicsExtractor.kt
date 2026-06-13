package com.cocode.measureapp.capture

import android.hardware.camera2.CameraCharacteristics
import android.util.SizeF
import com.cocode.measureapp.geometry.CameraIntrinsics
import kotlin.math.max

/**
 * Derives pinhole [CameraIntrinsics], in pixels of the captured image, from Camera2 data.
 * Prefers the device's calibrated intrinsics, then focal-length + sensor size, then a
 * coarse FOV fallback. All paths are approximations until verified on a real device.
 */
object IntrinsicsExtractor {
    fun extract(
        characteristics: CameraCharacteristics,
        imageWidth: Int,
        imageHeight: Int,
    ): CameraIntrinsics {
        val calib = characteristics.get(CameraCharacteristics.LENS_INTRINSIC_CALIBRATION)
        val activeArray = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
        if (calib != null && calib.size >= 4 && activeArray != null &&
            activeArray.width() > 0 && activeArray.height() > 0
        ) {
            val sx = imageWidth.toDouble() / activeArray.width()
            val sy = imageHeight.toDouble() / activeArray.height()
            return CameraIntrinsics(
                fx = calib[0].toDouble() * sx,
                fy = calib[1].toDouble() * sy,
                cx = calib[2].toDouble() * sx,
                cy = calib[3].toDouble() * sy,
            )
        }

        val focal = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull()
        val sensor: SizeF? = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        if (focal != null && focal > 0f && sensor != null && sensor.width > 0f && sensor.height > 0f) {
            return CameraIntrinsics(
                fx = focal.toDouble() / sensor.width * imageWidth,
                fy = focal.toDouble() / sensor.height * imageHeight,
                cx = imageWidth / 2.0,
                cy = imageHeight / 2.0,
            )
        }

        // Last resort: assume a typical phone field of view (~focal == long edge in px).
        val f = max(imageWidth, imageHeight).toDouble()
        return CameraIntrinsics(f, f, imageWidth / 2.0, imageHeight / 2.0)
    }
}
