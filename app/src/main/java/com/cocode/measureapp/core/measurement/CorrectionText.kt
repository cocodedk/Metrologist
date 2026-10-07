package com.cocode.measureapp.core.measurement

import com.cocode.measureapp.geometry.MeasurementFailureReason
import com.cocode.measureapp.geometry.MeasurementOutcome
import com.cocode.measureapp.model.TextKey
import com.cocode.measureapp.model.UiText

/** The recovery the marking flow offers first for a failure; Back and Retake always remain. */
enum class RecoveryAction { REMARK, SETTINGS, RETAKE }

/**
 * User-facing correction text for the shared failure vocabulary. The producer's [detail]
 * (marker rule, both methods' eligibility explanations, calibration note) is kept after the
 * headline, so the specific cause is never replaced by a generic message.
 */
object CorrectionText {
    fun headline(reason: MeasurementFailureReason): UiText = UiText(
        when (reason) {
            MeasurementFailureReason.INVALID_OBJECT_CORNERS -> TextKey.FIX_OBJECT_CORNERS
            MeasurementFailureReason.INVALID_STICK_CORNERS -> TextKey.FIX_STICK_BOX
            MeasurementFailureReason.INVALID_REFERENCE_DIMENSIONS -> TextKey.FIX_REFERENCE_SIZE
            MeasurementFailureReason.METADATA_UNAVAILABLE -> TextKey.FIX_RETAKE_PHOTO
            MeasurementFailureReason.UNSUPPORTED_GEOMETRY -> TextKey.FIX_UNSUPPORTED_VIEW
            MeasurementFailureReason.NUMERICAL_FAILURE -> TextKey.FIX_UNSTABLE
        },
    )

    /** Headline followed by the producer's specific explanation, when it has one. */
    fun message(failure: MeasurementOutcome.Failure): UiText {
        val headline = headline(failure.reason)
        val detail = failure.detail ?: return headline
        return UiText.withMessages(TextKey.SENTENCES, headline, detail)
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
