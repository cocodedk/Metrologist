package com.cocode.measureapp.geometry

import kotlin.math.min

/**
 * Full result of one metrology pass: the recovered measurements, the plane solution used,
 * the metric-to-real scale, and an overall confidence in `[0,1]` combining solver confidence
 * with scale agreement.
 */
data class EngineResult(
    val measurement: MeasurementResult,
    val solution: PlaneSolution,
    val scale: ScaleResult,
    val confidence: Double,
)

/**
 * Turns marked image points + camera intrinsics + a known stick length into real-world
 * rectangle measurements with a confidence score, using the rectangle solver.
 */
object MetrologyEngine {
    /**
     * @param corners image corners of the target rectangle, `[TL, TR, BR, BL]` clockwise.
     * @param stick image markers along the known-length stick lying on the same plane.
     * @param k camera intrinsics.
     * @param profile the stick's known real length and band subdivision.
     *
     * When [RectangleSolver] cannot recover a plane (fronto-parallel / degenerate quad), the
     * solution is `null` and the engine returns a zeroed result with `confidence = 0.0` and a
     * RECTANGLE solution flagged unusable (Plan 3 adds the gravity fallback). Otherwise corners
     * and stick are projected to up-to-scale metric, [ScaleSolver] recovers the real scale, the
     * scaled corners are measured, and `confidence = solution.confidence * (1 - min(agreement, 1))`.
     */
    fun measure(
        corners: List<Vec2>,
        stick: List<Vec2>,
        k: CameraIntrinsics,
        profile: StickProfile,
    ): EngineResult {
        val solution = RectangleSolver.solve(corners, k) ?: return EngineResult(
            MeasurementResult(0.0, 0.0, 0.0, 0.0, emptyList()),
            PlaneSolution(
                PlaneFrame(Vec3(1.0, 0.0, 0.0), Vec3(0.0, 1.0, 0.0), Vec3(0.0, 0.0, 1.0)),
                SolverKind.RECTANGLE,
                0.0,
            ),
            ScaleResult(0.0, 0.0),
            0.0,
        )

        val cornerMetric = projectToPlane(corners, k, solution.frame)
        val stickMetric = projectToPlane(stick, k, solution.frame)
        val scale = ScaleSolver.solve(stickMetric, profile)
        val cornersReal = cornerMetric.map { it * scale.scale }
        val measurement = Measurements.compute(cornersReal)
        val confidence = solution.confidence * (1.0 - min(scale.agreement, 1.0))
        return EngineResult(measurement, solution, scale, confidence)
    }
}
