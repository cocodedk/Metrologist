package com.cocode.measureapp.capture

import android.hardware.camera2.CameraCharacteristics
import com.cocode.measureapp.capture.frames.ActiveArray
import com.cocode.measureapp.capture.frames.BufferIntrinsics
import com.cocode.measureapp.capture.frames.LensCharacteristics
import com.cocode.measureapp.geometry.frames.ProvenancedIntrinsics

/**
 * Thin Android adapter: copies the Camera2 values the capture-frame transform needs into a pure
 * [LensCharacteristics]. All mapping, source preference and provenance labelling happen in
 * [BufferIntrinsics]; the result is in camera-BUFFER pixels, not yet in the marking frame.
 * The shot's pre-correction-to-processed-image mapping is not read here, so device
 * calibration stays approximate (see [com.cocode.measureapp.geometry.frames.ProvenanceReason]).
 */
object IntrinsicsExtractor {
    fun read(characteristics: CameraCharacteristics): LensCharacteristics {
        val active = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
        val physical = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        val pre = characteristics.get(CameraCharacteristics.SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE)
        return LensCharacteristics(
            intrinsicCalibration = characteristics.get(CameraCharacteristics.LENS_INTRINSIC_CALIBRATION)
                ?.map { it.toDouble() },
            activeArray = active?.let { ActiveArray(it.width(), it.height()) },
            focalLengthMm = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                ?.firstOrNull()?.toDouble(),
            sensorWidthMm = physical?.width?.toDouble(),
            sensorHeightMm = physical?.height?.toDouble(),
            facing = characteristics.get(CameraCharacteristics.LENS_FACING),
            sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION),
            timestampSource = characteristics.get(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE),
            preCorrectionArray = pre?.let { ActiveArray(it.width(), it.height()) },
        )
    }

    fun extract(characteristics: CameraCharacteristics, bufferWidth: Int, bufferHeight: Int): ProvenancedIntrinsics =
        BufferIntrinsics.estimate(read(characteristics), bufferWidth, bufferHeight)
}
