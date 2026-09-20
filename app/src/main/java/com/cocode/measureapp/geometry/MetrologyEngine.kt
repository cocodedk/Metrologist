package com.cocode.measureapp.geometry

import com.cocode.measureapp.geometry.eligibility.EligibilityInput
import com.cocode.measureapp.geometry.eligibility.OutcomeProducer
import com.cocode.measureapp.geometry.eligibility.PlaneAssumption
import com.cocode.measureapp.geometry.eligibility.SurfaceConsistency
import com.cocode.measureapp.geometry.frames.AlignedGravity
import com.cocode.measureapp.geometry.frames.CalibrationProvenance
import com.cocode.measureapp.geometry.frames.GravityAlignmentReason
import com.cocode.measureapp.stick.StickScale
import kotlin.math.min

/**
 * Per-pass summary of which solver was used and how the result was conditioned: the chosen
 * [solver], the overall [confidence], the camera pitch in degrees ([cameraTiltDeg], positive
 * looking up / negative looking down, NaN without valid gravity), the recovered metric-to-real
 * [scale], and the stick [scaleAgreement].
 *
 * Evidence added by solver eligibility (null = not reported, e.g. legacy construction): the
 * scene [calibration] provenance, [surfaceConsistency] with the wall/floor selection, the plane
 * [assumption], why gravity was unavailable ([gravityUnavailable]), whether the stick's known
 * aspect was cross-checked ([referenceChecked]), the input [revision] and the [selectionReason].
 */
data class MeasurementDiagnostics(
    val solver: SolverKind,
    val confidence: Double,
    val cameraTiltDeg: Double,
    val scale: Double,
    val scaleAgreement: Double,
    val calibration: CalibrationProvenance? = null,
    val surfaceConsistency: SurfaceConsistency? = null,
    val assumption: PlaneAssumption? = null,
    val gravityUnavailable: GravityAlignmentReason? = null,
    val referenceChecked: Boolean? = null,
    val revision: Int? = null,
    val selectionReason: String? = null,
)

/**
 * Full result of one metrology pass: the recovered measurements, the plane solution used,
 * the metric-to-real scale, and an overall confidence in `[0,1]` combining solver confidence
 * with scale agreement. [diagnostics] is populated by the hybrid path and left null by the
 * rectangle-only [MetrologyEngine.measure] (preserving Plan 2 positional construction).
 */
data class EngineResult(
    val measurement: MeasurementResult,
    val solution: PlaneSolution,
    val scale: ScaleResult,
    val confidence: Double,
    val diagnostics: MeasurementDiagnostics? = null,
)

/**
 * Turns marked image points + camera intrinsics + a known stick length into real-world
 * measurements. [evaluate] is the eligibility-checked production path returning a
 * [MeasurementOutcome]; [measureHybrid] adapts it to the legacy [EngineResult]; [measure] is
 * the unchecked rectangle-only Plan 2 path kept for oracle tests.
 */
object MetrologyEngine {
    /**
     * @param corners image corners of the target rectangle, `[TL, TR, BR, BL]` clockwise.
     * @param stick the 4 image corners of the stick's bounding box (clockwise around the quad),
     *   lying on the same plane; long edges = stick length, short edges = stick width.
     * @param k camera intrinsics.
     * @param profile the stick's known real length, width, and band subdivision.
     *
     * Rectangle-only Plan 2 path. When [RectangleSolver] cannot recover a plane the solution
     * is `null` and a zeroed result with `confidence = 0.0` and a RECTANGLE solution flagged
     * unusable is returned. Otherwise the shared project + scale + measure logic runs. The
     * returned `diagnostics` stays null here, preserving the Plan 2 behavior identically.
     */
    fun measure(
        corners: List<Vec2>,
        stick: List<Vec2>,
        k: CameraIntrinsics,
        profile: StickProfile,
    ): EngineResult {
        val solution = RectangleSolver.solve(corners, k) ?: return zeroed(SolverKind.RECTANGLE)
        return measureWith(solution, corners, stick, k, profile)
    }

    /**
     * Production measurement entry point (contract C10). Validates the marks, builds the
     * rectangle candidate and the gravity plane, applies eligibility and surface consistency,
     * ranks only eligible candidates and returns a finite success or an explanatory failure.
     *
     * @param corners object corners `[TL, TR, BR, BL]` in the aligned marking frame.
     * @param stick the stick box corners in either cyclic winding.
     * @param k intrinsics of the SAME aligned frame; [calibration] is their provenance.
     * @param gravity aligned physical-down gravity or why it is unavailable (never a default).
     * @param orientation the user's explicit wall/floor selection.
     * @param revision the image/input revision the outcome belongs to (echoed in diagnostics).
     */
    fun evaluate(
        corners: List<Vec2>,
        stick: List<Vec2>,
        k: CameraIntrinsics,
        calibration: CalibrationProvenance,
        gravity: AlignedGravity,
        profile: StickProfile,
        orientation: SurfaceOrientation,
        revision: Int,
    ): MeasurementOutcome = OutcomeProducer.evaluate(
        EligibilityInput(corners, stick, k, calibration, gravity, profile, orientation), revision,
    ).outcome

    /**
     * Legacy [EngineResult] adapter over [evaluate] for callers not yet migrated to
     * [MeasurementOutcome]: [gravity] is treated as a valid aligned reading and [calibration]
     * defaults to unavailable, as nothing established it. A failure is returned zeroed with
     * `confidence == 0.0` and diagnostics (tilt); consumers must not format it.
     *
     * @param gravity unit camera-frame vector pointing along world down (level camera: `(0,1,0)`).
     * @param orientation whether the measured surface is a wall ([SurfaceOrientation.VERTICAL])
     *   or a floor/table ([SurfaceOrientation.HORIZONTAL]).
     */
    fun measureHybrid(
        corners: List<Vec2>,
        stick: List<Vec2>,
        k: CameraIntrinsics,
        profile: StickProfile,
        gravity: Vec3,
        orientation: SurfaceOrientation,
        calibration: CalibrationProvenance = CalibrationProvenance.UNAVAILABLE,
    ): EngineResult {
        val input = EligibilityInput(
            corners, stick, k, calibration, AlignedGravity.Available(gravity, 0L), profile, orientation,
        )
        val e = OutcomeProducer.evaluate(input, revision = 0)
        val success = e.outcome.successOrNull()
        val chosen = e.chosen
        if (success == null || chosen == null) {
            return zeroed(SolverKind.RECTANGLE).copy(
                diagnostics = MeasurementDiagnostics(
                    SolverKind.RECTANGLE, 0.0, e.cameraTiltDeg, 0.0, 0.0, calibration = calibration,
                ),
            )
        }
        return EngineResult(
            success.measurement,
            PlaneSolution(chosen.frame, success.solver, success.confidence),
            success.scale,
            success.confidence,
            success.diagnostics,
        )
    }

    /**
     * Shared metrology logic for a usable [solution]: project corners + the stick's 4 box
     * corners to up-to-scale metric, recover the real scale with [StickScale] (using BOTH the
     * known length along the long edges and the known width across the short edges), scale the
     * corners, measure them, and combine `confidence = solution.confidence * (1 - min(agreement, 1))`.
     */
    private fun measureWith(
        solution: PlaneSolution,
        corners: List<Vec2>,
        stick: List<Vec2>,
        k: CameraIntrinsics,
        profile: StickProfile,
    ): EngineResult {
        val cornerMetric = projectToPlane(corners, k, solution.frame)
        val stickMetric = projectToPlane(stick, k, solution.frame)
        val scale = StickScale.solve(stickMetric, profile)
        val cornersReal = cornerMetric.map { it * scale.scale }
        val measurement = Measurements.compute(cornersReal)
        val confidence = solution.confidence * (1.0 - min(scale.agreement, 1.0))
        return EngineResult(measurement, solution, scale, confidence)
    }

    /** Zeroed, zero-confidence result with a placeholder [solver] plane flagged unusable. */
    private fun zeroed(solver: SolverKind): EngineResult = EngineResult(
        MeasurementResult(0.0, 0.0, 0.0, 0.0, emptyList()),
        PlaneSolution(
            PlaneFrame(Vec3(1.0, 0.0, 0.0), Vec3(0.0, 1.0, 0.0), Vec3(0.0, 0.0, 1.0)),
            solver,
            0.0,
        ),
        ScaleResult(0.0, 0.0),
        0.0,
    )
}
