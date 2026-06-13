package com.cocode.measureapp.core

import com.cocode.measureapp.geometry.MeasurementDiagnostics
import com.cocode.measureapp.geometry.SolverKind
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
    }
}
