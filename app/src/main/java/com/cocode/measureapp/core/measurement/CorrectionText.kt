package com.cocode.measureapp.core.measurement

import com.cocode.measureapp.geometry.MeasurementFailureReason
import com.cocode.measureapp.geometry.MeasurementOutcome

/** The recovery the marking flow offers first for a failure; Back and Retake always remain. */
enum class RecoveryAction { REMARK, SETTINGS, RETAKE }

/**
 * User-facing correction text for the shared failure vocabulary. The producer's [detail]
 * (marker rule, both methods' eligibility explanations, calibration note) is kept verbatim
 * after the headline, so the specific cause is never replaced by a generic message.
 */
object CorrectionText {
    fun headline(reason: MeasurementFailureReason): String = when (reason) {
        MeasurementFailureReason.INVALID_OBJECT_CORNERS ->
            "Adjust the object corners and measure again."
        MeasurementFailureReason.INVALID_STICK_CORNERS ->
            "Adjust the stick box so it outlines the reference stick, then measure again."
        MeasurementFailureReason.INVALID_REFERENCE_DIMENSIONS ->
            "Correct the reference stick size in Settings; your photo and marks are kept."
        MeasurementFailureReason.METADATA_UNAVAILABLE ->
            "This photo lacks the camera data needed to measure it; retake the photo."
        MeasurementFailureReason.UNSUPPORTED_GEOMETRY ->
            "This view cannot be measured as marked; check the wall/floor choice and the marks, " +
                "or retake from a moderate angle."
        MeasurementFailureReason.NUMERICAL_FAILURE ->
            "The calculation was unstable; adjust the marks or retake from a moderate angle."
    }

    /** Headline followed by the producer's specific explanation, when it has one. */
    fun message(failure: MeasurementOutcome.Failure): String {
        val detail = failure.detail?.trim().orEmpty()
        return if (detail.isEmpty()) headline(failure.reason) else "${headline(failure.reason)} $detail"
    }

    fun action(reason: MeasurementFailureReason): RecoveryAction = when (reason) {
        MeasurementFailureReason.INVALID_OBJECT_CORNERS,
        MeasurementFailureReason.INVALID_STICK_CORNERS,
        MeasurementFailureReason.UNSUPPORTED_GEOMETRY,
        MeasurementFailureReason.NUMERICAL_FAILURE -> RecoveryAction.REMARK
        MeasurementFailureReason.INVALID_REFERENCE_DIMENSIONS -> RecoveryAction.SETTINGS
        MeasurementFailureReason.METADATA_UNAVAILABLE -> RecoveryAction.RETAKE
    }
}
