package com.cocode.measureapp.geometry.frames

import com.cocode.measureapp.geometry.CameraIntrinsics

/** How far the intrinsics of a scene can be trusted. Never upgraded by a later step. */
enum class CalibrationStatus { CALIBRATED, APPROXIMATE, UNAVAILABLE }

/** Where the intrinsics came from. */
enum class IntrinsicsOrigin {
    /**
     * Camera2 `LENS_INTRINSIC_CALIBRATION`. On a device the mapping from its pre-correction array
     * coordinates to the processed stream is ASSUMED (centred aspect crop of the active array,
     * distortion not applied), so such values are approximate, never calibrated.
     */
    LENS_INTRINSIC_CALIBRATION,

    /** Nominal focal length over physical sensor size; ignores the lens's real principal point. */
    FOCAL_LENGTH_AND_SENSOR_SIZE,

    /** Guessed field of view (focal = long image edge); no camera data used. */
    FIELD_OF_VIEW_GUESS,

    /** Explicitly trusted synthetic fixture with a fully specified transform (tests only). */
    TRUSTED_FIXTURE,

    /** Not established for these pixels, e.g. a scene built without the capture-frame transform. */
    UNKNOWN,
}

/** Why intrinsics are not calibrated, kept for diagnostics. Null only for trusted fixtures. */
enum class ProvenanceReason {
    /** Device calibration used, but its mapping onto the processed image was assumed. */
    PROCESSED_IMAGE_MAPPING_ASSUMED,

    /** Device calibration reports nonzero or non-finite skew, which this model cannot represent. */
    CALIBRATION_SKEW_UNSUPPORTED,

    /** Device calibration is too short, non-finite, or has non-positive focal lengths. */
    CALIBRATION_VALUES_INVALID,

    /** Device calibration present, but its array geometry is missing or differs from the active array. */
    CALIBRATION_ARRAY_UNMAPPABLE,

    /** Nominal lens/sensor data only; principal point assumed centred. */
    NOMINAL_LENS_DATA,

    /** No usable camera data; field of view guessed. */
    NO_CAMERA_DATA,

    /** The scene was not produced by the capture-frame transform. */
    FRAME_NOT_ALIGNED,
}

data class CalibrationProvenance(
    val status: CalibrationStatus,
    val origin: IntrinsicsOrigin,
    val reason: ProvenanceReason?,
) {
    /** The same source, recording why a preferred device calibration was rejected. */
    fun because(r: ProvenanceReason) = copy(reason = r)

    companion object {
        /** Trusted synthetic fixture only; no device path produces this. */
        val CALIBRATED = CalibrationProvenance(CalibrationStatus.CALIBRATED, IntrinsicsOrigin.TRUSTED_FIXTURE, null)
        val DEVICE_CALIBRATION_ASSUMED_MAPPING = CalibrationProvenance(
            CalibrationStatus.APPROXIMATE, IntrinsicsOrigin.LENS_INTRINSIC_CALIBRATION,
            ProvenanceReason.PROCESSED_IMAGE_MAPPING_ASSUMED,
        )
        val FOCAL_AND_SENSOR = CalibrationProvenance(
            CalibrationStatus.APPROXIMATE, IntrinsicsOrigin.FOCAL_LENGTH_AND_SENSOR_SIZE, ProvenanceReason.NOMINAL_LENS_DATA,
        )
        val FOV_GUESS = CalibrationProvenance(
            CalibrationStatus.APPROXIMATE, IntrinsicsOrigin.FIELD_OF_VIEW_GUESS, ProvenanceReason.NO_CAMERA_DATA,
        )
        val UNAVAILABLE = CalibrationProvenance(
            CalibrationStatus.UNAVAILABLE, IntrinsicsOrigin.UNKNOWN, ProvenanceReason.FRAME_NOT_ALIGNED,
        )
    }
}

/** Intrinsics in some declared pixel frame, together with their provenance. */
data class ProvenancedIntrinsics(val intrinsics: CameraIntrinsics, val provenance: CalibrationProvenance)
