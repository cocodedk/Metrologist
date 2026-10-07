package com.cocode.measureapp.core

import com.cocode.measureapp.geometry.MeasurementDiagnostics
import com.cocode.measureapp.geometry.SolverKind
import com.cocode.measureapp.geometry.eligibility.PlaneAssumption
import com.cocode.measureapp.geometry.eligibility.SurfaceConsistency
import com.cocode.measureapp.geometry.frames.CalibrationStatus
import com.cocode.measureapp.model.TextKey
import com.cocode.measureapp.model.UiText
import kotlin.math.abs

/**
 * Turns a [MeasurementDiagnostics] from the engine into the messages the Results screen shows.
 * Pure logic that picks messages ([UiText]); the Android layer words them. No Android dependencies.
 */
object DiagnosticsText {
    /** Plain-language confidence band: `>= 0.7` High, `>= 0.4` Medium, else Low. */
    fun confidenceLabel(confidence: Double): UiText = UiText(
        when {
            confidence >= 0.7 -> TextKey.CONFIDENCE_HIGH
            confidence >= 0.4 -> TextKey.CONFIDENCE_MEDIUM
            else -> TextKey.CONFIDENCE_LOW
        },
    )

    /** Caveats that apply to [d], in a stable order; empty when the result is clean. */
    fun caveats(d: MeasurementDiagnostics): List<UiText> = buildList {
        if (d.confidence < 0.4) add(UiText(TextKey.CAVEAT_LOW_CONFIDENCE))
        if (d.scaleAgreement > 0.1) add(UiText(TextKey.CAVEAT_STICK_SPACING))
        if (d.solver == SolverKind.GRAVITY) add(UiText(TextKey.CAVEAT_TILT_SENSOR_METHOD))
        if (abs(d.cameraTiltDeg) > 60.0) add(UiText(TextKey.CAVEAT_STEEP_CAMERA))
        addAll(evidenceCaveats(d))
    }

    /** Eligibility evidence that must stay visible; nothing is added for unreported (null) fields. */
    private fun evidenceCaveats(d: MeasurementDiagnostics): List<UiText> = buildList {
        when (d.calibration?.status) {
            CalibrationStatus.APPROXIMATE -> add(UiText(TextKey.CAVEAT_LENS_APPROXIMATE))
            CalibrationStatus.UNAVAILABLE -> add(UiText(TextKey.CAVEAT_LENS_UNAVAILABLE))
            CalibrationStatus.CALIBRATED, null -> Unit
        }
        if (d.surfaceConsistency == SurfaceConsistency.UNVERIFIED_NO_GRAVITY) add(UiText(TextKey.CAVEAT_NO_TILT_READING))
        if (d.assumption == PlaneAssumption.WALL_FACES_CAMERA) add(UiText(TextKey.CAVEAT_WALL_ASSUMED))
        if (d.referenceChecked == false) add(UiText(TextKey.CAVEAT_STICK_WIDTH_UNKNOWN))
    }
}
