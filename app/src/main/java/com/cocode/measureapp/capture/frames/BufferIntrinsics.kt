package com.cocode.measureapp.capture.frames

import com.cocode.measureapp.geometry.CameraIntrinsics
import com.cocode.measureapp.geometry.frames.CalibrationProvenance
import com.cocode.measureapp.geometry.frames.ProvenanceReason
import com.cocode.measureapp.geometry.frames.ProvenancedIntrinsics

/** Sensor pixel-array size (active or pre-correction). */
data class ActiveArray(val width: Int, val height: Int)

/**
 * Plain copies of the Camera2 characteristics this node needs, so the mapping is pure and
 * testable. Any field may be null when the device does not report it.
 */
data class LensCharacteristics(
    /** `LENS_INTRINSIC_CALIBRATION`: fx, fy, cx, cy, skew in pre-correction array pixels. */
    val intrinsicCalibration: List<Double>? = null,
    val activeArray: ActiveArray? = null,
    val focalLengthMm: Double? = null,
    val sensorWidthMm: Double? = null,
    val sensorHeightMm: Double? = null,
    /** Camera2 `LENS_FACING`. */
    val facing: Int? = null,
    /** Camera2 `SENSOR_ORIENTATION` in degrees. */
    val sensorOrientation: Int? = null,
    /** Camera2 `SENSOR_INFO_TIMESTAMP_SOURCE`. */
    val timestampSource: Int? = null,
    /** Camera2 `SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE`, the frame of the calibration. */
    val preCorrectionArray: ActiveArray? = null,
)

/**
 * Intrinsics in camera-BUFFER pixels (unrotated, uncropped), with provenance. The buffer is
 * modelled as the largest centred region of the active array with the buffer's aspect ratio,
 * uniformly scaled (Camera2 stream cropping at 1x zoom). That mapping and the neglect of lens
 * distortion are ASSUMPTIONS not verified for the shot, so no device source is ever labelled
 * calibrated. Each source is used only if it yields finite positive focal lengths; otherwise the
 * next, less trusted source is taken, recording why the device calibration was rejected.
 */
object BufferIntrinsics {
    fun estimate(lens: LensCharacteristics?, bufferWidth: Int, bufferHeight: Int): ProvenancedIntrinsics {
        require(bufferWidth > 0 && bufferHeight > 0) { "empty buffer ${bufferWidth}x$bufferHeight" }
        val array = lens?.activeArray?.takeIf { it.width > 0 && it.height > 0 }
        val rejected = when (val c = fromCalibration(lens, array, bufferWidth, bufferHeight)) {
            is Calibration.Mapped -> return ProvenancedIntrinsics(c.k, CalibrationProvenance.DEVICE_CALIBRATION_ASSUMED_MAPPING)
            is Calibration.Rejected -> c.reason
            null -> null
        }
        val fallback = fromFocalLength(lens, array, bufferWidth, bufferHeight)
            ?.let { ProvenancedIntrinsics(it, CalibrationProvenance.FOCAL_AND_SENSOR) }
            ?: maxOf(bufferWidth, bufferHeight).toDouble().let { f ->
                ProvenancedIntrinsics(
                    CameraIntrinsics(f, f, bufferWidth / 2.0, bufferHeight / 2.0),
                    CalibrationProvenance.FOV_GUESS,
                )
            }
        return if (rejected == null) fallback else fallback.copy(provenance = fallback.provenance.because(rejected))
    }

    private sealed interface Calibration {
        data class Mapped(val k: CameraIntrinsics) : Calibration
        data class Rejected(val reason: ProvenanceReason) : Calibration
    }

    /** Null when the device reports no calibration at all. */
    private fun fromCalibration(lens: LensCharacteristics?, array: ActiveArray?, w: Int, h: Int): Calibration? {
        val c = lens?.intrinsicCalibration ?: return null
        if (c.size < 5 || c.take(4).any { !it.isFinite() } || !(c[0] > 0.0 && c[1] > 0.0)) {
            return Calibration.Rejected(ProvenanceReason.CALIBRATION_VALUES_INVALID)
        }
        if (!c[4].isFinite() || c[4] != 0.0) return Calibration.Rejected(ProvenanceReason.CALIBRATION_SKEW_UNSUPPORTED)
        val pre = lens.preCorrectionArray
        if (array == null || (pre != null && pre != array)) {
            return Calibration.Rejected(ProvenanceReason.CALIBRATION_ARRAY_UNMAPPABLE)
        }
        return arrayToBuffer(CameraIntrinsics(c[0], c[1], c[2], c[3]), array, w, h)?.let { Calibration.Mapped(it) }
            ?: Calibration.Rejected(ProvenanceReason.CALIBRATION_VALUES_INVALID)
    }

    private fun fromFocalLength(lens: LensCharacteristics?, array: ActiveArray?, w: Int, h: Int): CameraIntrinsics? {
        val f = lens?.focalLengthMm ?: return null
        val sw = lens.sensorWidthMm ?: return null
        val sh = lens.sensorHeightMm ?: return null
        if (!(f > 0.0 && sw > 0.0 && sh > 0.0)) return null
        if (array == null) {
            return CameraIntrinsics(f / sw * w, f / sh * h, w / 2.0, h / 2.0).takeIf { it.isUsable() }
        }
        val k = CameraIntrinsics(f / sw * array.width, f / sh * array.height, array.width / 2.0, array.height / 2.0)
        return arrayToBuffer(k, array, w, h)
    }

    /** Active-array intrinsics -> buffer intrinsics under the ASSUMED centred aspect crop + uniform scale. */
    fun arrayToBuffer(k: CameraIntrinsics, array: ActiveArray, w: Int, h: Int): CameraIntrinsics? {
        if (!k.isUsable()) return null
        val aw = array.width.toDouble(); val ah = array.height.toDouble()
        val bufferAspect = w.toDouble() / h
        val regionW = if (bufferAspect >= aw / ah) aw else ah * bufferAspect
        val regionH = regionW / bufferAspect
        val s = w / regionW
        val offX = (aw - regionW) / 2.0
        val offY = (ah - regionH) / 2.0
        return CameraIntrinsics(k.fx * s, k.fy * s, (k.cx - offX) * s, (k.cy - offY) * s).takeIf { it.isUsable() }
    }
}
