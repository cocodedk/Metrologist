package com.cocode.measureapp.core

import com.cocode.measureapp.geometry.MeasurementDiagnostics
import com.cocode.measureapp.geometry.SolverKind
import com.cocode.measureapp.geometry.eligibility.PlaneAssumption
import com.cocode.measureapp.geometry.eligibility.SurfaceConsistency
import com.cocode.measureapp.geometry.frames.CalibrationStatus
import kotlin.math.abs

/**
 * Turns a [MeasurementDiagnostics] from the engine into plain-language UI text. Pure string
 * logic — no Android dependencies.
 */
object DiagnosticsText {
    /** Plain-language confidence band: `>= 0.7` High, `>= 0.4` Medium, else Low. */
    fun confidenceLabel(confidence: Double): String = when {
        confidence >= 0.7 -> "High confidence"
        confidence >= 0.4 -> "Medium confidence"
        else -> "Low confidence"
    }

    /** Caveats that apply to [d], in a stable order; empty when the result is clean. */
    fun caveats(d: MeasurementDiagnostics): List<String> = buildList {
        if (d.confidence < 0.4) {
            add(
                "Low confidence — corners may not be square or the angle is too shallow; " +
                    "try a moderate angle.",
            )
        }
        if (d.scaleAgreement > 0.1) {
            add("Stick band spacing disagrees — make sure the stick lies flat on the surface.")
        }
        if (d.solver == SolverKind.GRAVITY) {
            add("Used the tilt-sensor fallback; accuracy is lower than the rectangle method.")
        }
        if (abs(d.cameraTiltDeg) > 60.0) {
            add("Camera is tilted steeply; re-shoot closer to level for best accuracy.")
        }
        addAll(evidenceCaveats(d))
    }

    /** Eligibility evidence that must stay visible; nothing is added for unreported (null) fields. */
    private fun evidenceCaveats(d: MeasurementDiagnostics): List<String> = buildList {
        when (d.calibration?.status) {
            CalibrationStatus.APPROXIMATE -> add(
                "Camera calibration is approximate (${d.calibration?.origin}); treat dimensions as estimates.",
            )
            CalibrationStatus.UNAVAILABLE -> add(
                "Camera calibration is unavailable for this photo; dimensions are rough estimates.",
            )
            CalibrationStatus.CALIBRATED, null -> Unit
        }
        if (d.surfaceConsistency == SurfaceConsistency.UNVERIFIED_NO_GRAVITY) {
            val why = d.gravityUnavailable?.let { " ($it)" } ?: ""
            add("No tilt reading at capture$why; the wall/floor choice could not be verified.")
        }
        if (d.assumption == PlaneAssumption.WALL_FACES_CAMERA) {
            add("Assumed the wall faces the camera squarely; the tilt sensor cannot tell its direction.")
        }
        if (d.referenceChecked == false) {
            add("Stick width unknown; the reference shape was not cross-checked.")
        }
    }
}
