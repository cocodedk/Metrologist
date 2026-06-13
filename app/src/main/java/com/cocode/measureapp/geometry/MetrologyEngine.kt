package com.cocode.measureapp.geometry

import kotlin.math.asin
import kotlin.math.min

/**
 * Per-pass summary of which solver was used and how the result was conditioned: the chosen
 * [solver], the overall [confidence], the camera pitch above horizontal in degrees
 * ([cameraTiltDeg]), the recovered metric-to-real [scale], and the stick [scaleAgreement].
 */
data class MeasurementDiagnostics(
    val solver: SolverKind,
    val confidence: Double,
    val cameraTiltDeg: Double,
    val scale: Double,
    val scaleAgreement: Double,
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
 * rectangle measurements with a confidence score, using either the rectangle solver
 * ([measure]) or an auto-selected rectangle/gravity solver ([measureHybrid]).
 */
object MetrologyEngine {
    /**
     * @param corners image corners of the target rectangle, `[TL, TR, BR, BL]` clockwise.
     * @param stick image markers along the known-length stick lying on the same plane.
     * @param k camera intrinsics.
     * @param profile the stick's known real length and band subdivision.
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
     * Auto-selecting hybrid path. Solves the rectangle plane and the gravity plane, lets
     * [SolverSelector] pick one, and (when the pick is usable) runs the shared logic and
     * attaches [MeasurementDiagnostics]. A selected solution of `confidence == 0.0` yields a
     * zeroed result that still carries diagnostics.
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
    ): EngineResult {
        val rect = RectangleSolver.solve(corners, k)
        val grav = GravitySolver.solve(gravity, orientation)
        val sel = SolverSelector.select(rect, grav)
        val tilt = cameraTiltDeg(gravity)

        if (sel.solution.confidence == 0.0) {
            val zero = zeroed(sel.solution.solver)
            return zero.copy(
                diagnostics = MeasurementDiagnostics(sel.solution.solver, 0.0, tilt, 0.0, 0.0),
            )
        }
        val result = measureWith(sel.solution, corners, stick, k, profile)
        return result.copy(
            diagnostics = MeasurementDiagnostics(
                sel.solution.solver, result.confidence, tilt,
                result.scale.scale, result.scale.agreement,
            ),
        )
    }

    /**
     * Shared metrology logic for a usable [solution]: project corners + stick to up-to-scale
     * metric, recover the real scale with [ScaleSolver], scale the corners, measure them, and
     * combine `confidence = solution.confidence * (1 - min(agreement, 1))`.
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
        val scale = ScaleSolver.solve(stickMetric, profile)
        val cornersReal = cornerMetric.map { it * scale.scale }
        val measurement = Measurements.compute(cornersReal)
        val confidence = solution.confidence * (1.0 - min(scale.agreement, 1.0))
        return EngineResult(measurement, solution, scale, confidence)
    }

    /** Camera pitch above horizontal (degrees): positive when the optical axis tips downward. */
    private fun cameraTiltDeg(gravity: Vec3): Double {
        val worldUp = (gravity * -1.0).normalized()
        return Math.toDegrees(asin(Vec3(0.0, 0.0, 1.0).dot(worldUp).coerceIn(-1.0, 1.0)))
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
