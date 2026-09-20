package com.cocode.measureapp.geometry.eligibility

import com.cocode.measureapp.core.validation.MarkValidation
import com.cocode.measureapp.core.validation.MarkerValidator
import com.cocode.measureapp.geometry.MeasurementDiagnostics
import com.cocode.measureapp.geometry.MeasurementFailureReason
import com.cocode.measureapp.geometry.MeasurementOutcome
import com.cocode.measureapp.geometry.RectangleSolver
import com.cocode.measureapp.geometry.Selection
import com.cocode.measureapp.geometry.SolverSelector
import com.cocode.measureapp.geometry.Vec3
import com.cocode.measureapp.geometry.frames.AlignedGravity
import com.cocode.measureapp.geometry.frames.CalibrationStatus
import kotlin.math.asin

/** A produced [outcome] plus the internals legacy adapters still need. */
data class Evaluation(
    val outcome: MeasurementOutcome,
    /** The selected plane for a success; null for a failure. */
    val chosen: Assessment.Eligible?,
    /** Camera pitch in degrees (up positive); NaN when no valid gravity exists. */
    val cameraTiltDeg: Double,
)

/**
 * Contract C10 producer: validated marks -> rectangle and gravity candidates -> eligibility
 * -> ranking -> a finite [MeasurementOutcome.Success] or an explanatory
 * [MeasurementOutcome.Failure]. Expected invalid input and geometric failures never escape as
 * exceptions, and no placeholder plane or zero measurement is ever produced.
 */
object OutcomeProducer {
    fun evaluate(input: EligibilityInput, revision: Int): Evaluation {
        val tilt = PlaneChecks.unitDown(input.gravity)?.let { cameraTiltDeg(it) } ?: Double.NaN
        fun fail(reason: MeasurementFailureReason, detail: String) =
            Evaluation(MeasurementOutcome.Failure(reason, detail + calibrationNote(input)), null, tilt)

        if (!input.intrinsics.isUsable()) {
            return fail(MeasurementFailureReason.METADATA_UNAVAILABLE, "Camera intrinsics are missing or invalid; retake the photo.")
        }
        val marks = when (val v = MarkerValidator.validateMarks(input.corners, input.stick)) {
            is MarkValidation.Rejected -> return Evaluation(v.rejection.toFailure(), null, tilt)
            is MarkValidation.Accepted -> v
        }
        val checked = input.copy(corners = marks.objectCorners, stick = marks.stickBox)
        val candidate = RectangleSolver.candidate(checked.corners, checked.intrinsics, checked.calibration, "revision $revision")
        val selection = SolverSelector.select(
            RectangleEligibility.assess(candidate, checked),
            GravityEligibility.assess(checked),
        )
        val chosen = when (selection) {
            is Selection.NoneEligible -> return fail(
                failureFor(selection),
                "No measurement method is justified. ${selection.rectangle.detail}. ${selection.gravity.detail}.",
            )
            is Selection.Chosen -> selection
        }
        val c = chosen.chosen
        val diagnostics = MeasurementDiagnostics(
            solver = c.solver,
            confidence = c.confidence,
            cameraTiltDeg = tilt,
            scale = c.scale.scale,
            scaleAgreement = c.scale.agreement,
            calibration = checked.calibration,
            surfaceConsistency = c.surface,
            assumption = c.assumption,
            gravityUnavailable = (checked.gravity as? AlignedGravity.Unavailable)?.reason,
            referenceChecked = c.referenceChecked,
            revision = revision,
            selectionReason = chosen.reason,
        )
        return try {
            val success = MeasurementOutcome.Success(
                c.measurement, c.solver, checked.orientation, c.scale, c.confidence, diagnostics,
            )
            Evaluation(success, c, tilt)
        } catch (e: IllegalArgumentException) {
            fail(MeasurementFailureReason.NUMERICAL_FAILURE, "The measurement is not finite: ${e.message}.")
        }
    }

    /**
     * Camera pitch relative to horizontal (degrees) from `asin(opticalAxis·worldUp)`: positive
     * when the optical axis tips up, negative when looking down, 0 for a level camera.
     */
    fun cameraTiltDeg(down: Vec3): Double {
        val worldUp = (down * -1.0).normalized()
        return Math.toDegrees(asin(Vec3(0.0, 0.0, 1.0).dot(worldUp).coerceIn(-1.0, 1.0)))
    }

    /** One stable reason for a both-ineligible failure; the detail keeps both explanations. */
    private fun failureFor(s: Selection.NoneEligible): MeasurementFailureReason = when {
        s.rectangle.failure == s.gravity.failure -> s.rectangle.failure
        s.gravity.reason == IneligibleReason.GRAVITY_UNAVAILABLE -> MeasurementFailureReason.METADATA_UNAVAILABLE
        else -> MeasurementFailureReason.UNSUPPORTED_GEOMETRY
    }

    private fun calibrationNote(input: EligibilityInput): String = when (input.calibration.status) {
        CalibrationStatus.CALIBRATED -> ""
        CalibrationStatus.APPROXIMATE -> " Camera calibration is approximate (${input.calibration.origin})."
        CalibrationStatus.UNAVAILABLE -> " Camera calibration is unavailable (${input.calibration.origin})."
    }
}
