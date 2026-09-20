package com.cocode.measureapp.geometry.eligibility

import com.cocode.measureapp.geometry.MeasurementFailureReason
import com.cocode.measureapp.geometry.SolverKind
import com.cocode.measureapp.geometry.eligibility.EligibilityTolerances as T
import com.cocode.measureapp.geometry.rectangle.RectangleCandidate
import com.cocode.measureapp.geometry.rectangle.RectangleTolerances

/**
 * Decides whether a node 01 rectangle plane candidate may be used. Checks, in order:
 * the candidate exists; its back-projected edge directions are orthogonal
 * ([T.MAX_ORTHOGONALITY_RESIDUAL]) BEFORE its orthogonal basis is trusted; its normal agrees
 * with the selected wall/floor when valid gravity exists; and the independently marked stick
 * box keeps its known aspect on that plane. A high image-angle score cannot bypass any check.
 */
object RectangleEligibility {
    fun assess(candidate: RectangleCandidate, input: EligibilityInput): Assessment {
        val accepted = when (candidate) {
            is RectangleCandidate.Rejected -> return ineligible(
                IneligibleReason.RECTANGLE_REJECTED, candidate.reason.failure,
                "no rectangle plane (${candidate.reason}): ${candidate.detail}",
            )
            is RectangleCandidate.Accepted -> candidate
        }
        val ev = accepted.evidence
        val residual = ev.orthogonalityResidual
        if (!(residual.isFinite() && residual <= T.MAX_ORTHOGONALITY_RESIDUAL)) {
            return ineligible(
                IneligibleReason.NOT_ORTHOGONAL, MeasurementFailureReason.UNSUPPORTED_GEOMETRY,
                "the marked corners are not a rectangle in 3D (edge residual $residual > " +
                    "${T.MAX_ORTHOGONALITY_RESIDUAL}); check the corners or the camera calibration",
            )
        }
        val consistency = when (val s = PlaneChecks.surface(accepted.frame.normal, input.gravity, input.orientation)) {
            is SurfaceVerdict.Contradiction -> return ineligible(
                IneligibleReason.SURFACE_CONTRADICTION, MeasurementFailureReason.UNSUPPORTED_GEOMETRY,
                "${s.detail}; check the wall/floor selection or the corners",
            )
            is SurfaceVerdict.Checked -> s.consistency
        }
        val measured = when (val m = PlaneMeasurer.measure(accepted.frame, input)) {
            is PlaneMeasurer.Result.Unusable -> return ineligible(m.reason, failureOf(m.reason), m.detail)
            is PlaneMeasurer.Result.Measured -> m
        }
        val raw = PlaneChecks.factor(residual, T.MAX_ORTHOGONALITY_RESIDUAL) *
            PlaneChecks.factor(ev.halfPixelAspectSensitivity, RectangleTolerances.MAX_HALF_PIXEL_ASPECT_SENSITIVITY) *
            PlaneChecks.factor(measured.scale.agreement, T.MAX_STICK_DISAGREEMENT) *
            PlaneChecks.rayFactor(measured.minRayCos)
        val (confidence, caps) = PlaneChecks.capped(
            raw, input.calibration, consistency, measured.referenceChecked, PlaneAssumption.RECTANGLE_TARGET,
        )
        return Assessment.Eligible(
            SolverKind.RECTANGLE, accepted.frame, measured.measurement, measured.scale, confidence,
            PlaneAssumption.RECTANGLE_TARGET, consistency, measured.referenceChecked, caps,
        )
    }

    /** Shared failure vocabulary for an eligibility reason. */
    internal fun failureOf(reason: IneligibleReason): MeasurementFailureReason = when (reason) {
        IneligibleReason.NUMERICAL -> MeasurementFailureReason.NUMERICAL_FAILURE
        IneligibleReason.GRAVITY_UNAVAILABLE -> MeasurementFailureReason.METADATA_UNAVAILABLE
        else -> MeasurementFailureReason.UNSUPPORTED_GEOMETRY
    }

    private fun ineligible(reason: IneligibleReason, failure: MeasurementFailureReason, detail: String) =
        Assessment.Ineligible(SolverKind.RECTANGLE, reason, failure, "Rectangle method: $detail")
}
