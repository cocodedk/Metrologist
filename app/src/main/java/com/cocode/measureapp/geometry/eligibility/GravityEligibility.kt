package com.cocode.measureapp.geometry.eligibility

import com.cocode.measureapp.geometry.GravitySolver
import com.cocode.measureapp.geometry.MeasurementFailureReason
import com.cocode.measureapp.geometry.SolverKind
import com.cocode.measureapp.geometry.SurfaceOrientation
import com.cocode.measureapp.geometry.Vec3
import com.cocode.measureapp.model.TextKey
import com.cocode.measureapp.model.UiText
import com.cocode.measureapp.geometry.eligibility.EligibilityTolerances as T

/**
 * Decides whether the gravity plane may be used. It requires valid aligned gravity and uses
 * the user's selected surface. A floor/table normal is fully determined by gravity; a wall's
 * azimuth is NOT, so the wall plane is assumed to face the camera, reported as
 * [PlaneAssumption.WALL_FACES_CAMERA] and capped below "Medium" confidence. The stick box
 * cross-check rejects azimuth errors large enough to distort its known aspect.
 */
object GravityEligibility {
    private val OPTICAL_AXIS = Vec3(0.0, 0.0, 1.0)

    fun assess(input: EligibilityInput): Assessment {
        val down = PlaneChecks.unitDown(input.gravity) ?: return ineligible(
            IneligibleReason.GRAVITY_UNAVAILABLE, MeasurementFailureReason.METADATA_UNAVAILABLE,
            UiText(TextKey.CAUSE_NO_TILT_READING),
        )
        val assumption = when (input.orientation) {
            SurfaceOrientation.HORIZONTAL -> PlaneAssumption.FLOOR_NORMAL_FROM_GRAVITY
            SurfaceOrientation.VERTICAL -> {
                // Horizontal component of the optical axis: the only cue for a wall's facing.
                val horizontal = OPTICAL_AXIS - down * OPTICAL_AXIS.dot(down)
                if (!(horizontal.norm() >= T.MIN_RAY_PLANE_COS)) {
                    return ineligible(
                        IneligibleReason.AZIMUTH_UNRESOLVED, MeasurementFailureReason.UNSUPPORTED_GEOMETRY,
                        UiText(TextKey.CAUSE_WALL_DIRECTION_UNKNOWN),
                    )
                }
                PlaneAssumption.WALL_FACES_CAMERA
            }
        }
        val frame = GravitySolver.solve(down, input.orientation).frame
        val measured = when (val m = PlaneMeasurer.measure(frame, input)) {
            is PlaneMeasurer.Result.Unusable -> return ineligible(
                m.reason, RectangleEligibility.failureOf(m.reason), Causes.then(m.detail, TextKey.ADVICE_SURFACE_CHOICE),
            )
            is PlaneMeasurer.Result.Measured -> m
        }
        val raw = PlaneChecks.rayFactor(measured.minRayCos) *
            PlaneChecks.factor(measured.scale.agreement, T.MAX_STICK_DISAGREEMENT)
        val (confidence, caps) = PlaneChecks.capped(
            raw, input.calibration, SurfaceConsistency.CONSISTENT, measured.referenceChecked, assumption,
        )
        return Assessment.Eligible(
            SolverKind.GRAVITY, frame, measured.measurement, measured.scale, confidence,
            assumption, SurfaceConsistency.CONSISTENT, measured.referenceChecked, caps,
        )
    }

    private fun ineligible(reason: IneligibleReason, failure: MeasurementFailureReason, detail: UiText) =
        Assessment.Ineligible(
            SolverKind.GRAVITY, reason, failure, UiText.withMessages(TextKey.TILT_METHOD_PROBLEM, detail),
        )
}
