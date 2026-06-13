package com.cocode.measureapp.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Synthetic ground-truth oracle for the **gravity** measurement path (plan §79-84).
 *
 * Mirrors [MetrologyEngineOracleTest] but for the gravity solver: a known real wall (W x H)
 * plus a known stick is projected to pixels by [SyntheticScene]'s independent pinhole model,
 * the camera-frame gravity for the pose is `gravityCam = R * worldDown`, `worldDown = (0,1,0)`,
 * and the full pipeline (GravitySolver -> projectToPlane -> ScaleSolver -> Measurements) must
 * recover the metric truth.
 *
 * Two oracles, both demanded by the contract:
 * 1. **Facing the camera** (near-fronto, tiny yaw): gravity recovers width/height/area within
 *    **<=2%** (deliberately looser than the rectangle solver's 0.5%).
 * 2. **Oblique azimuth** (large yaw): the gravity solver is **materially less accurate** while
 *    the rectangle solver on the SAME scene stays within 0.5% — documenting the faces-camera
 *    assumption that makes gravity the fallback, not the primary.
 */
class GravityOracleTest {
    private val w = 3.0
    private val h = 2.0
    private val l = 1.0
    private val k = CameraIntrinsics(fx = 1500.0, fy = 1500.0, cx = 960.0, cy = 540.0)
    private val t = Vec3(0.0, 0.0, 6.0)

    /** Camera-frame gravity for a pose: world down `(0,1,0)` rotated into the camera. */
    private fun gravityCam(r: Mat3): Vec3 = r * Vec3(0.0, 1.0, 0.0)

    /** Run the gravity solver + full pipeline directly (no selector), for a chosen pose. */
    private fun gravityMeasure(scene: SyntheticScene, gravity: Vec3): MeasurementResult {
        val sol = GravitySolver.solve(gravity, SurfaceOrientation.VERTICAL)
        val cornerMetric = projectToPlane(scene.cornerPixels, scene.k, sol.frame)
        val stickMetric = projectToPlane(scene.stickPixels, scene.k, sol.frame)
        val scale = ScaleSolver.solve(stickMetric, scene.profile)
        return Measurements.compute(cornerMetric.map { it * scale.scale })
    }

    @Test fun gravityRecoversTruthWhenFacingCamera() {
        // Near-fronto pure yaw: RectangleSolver returns null, so measureHybrid is FORCED onto
        // the gravity path. The wall faces the camera, so gravity stays within 2%.
        val r = SceneRotations.yawPitch(yawDeg = 1.5, pitchDeg = 0.0)
        val scene = SyntheticScene(w = w, h = h, r = r, t = t, k = k, l = l)
        val gravity = gravityCam(r)

        assertNull("rectangle must be null to force gravity", RectangleSolver.solve(scene.cornerPixels, scene.k))

        val result = MetrologyEngine.measureHybrid(
            scene.cornerPixels, scene.stickPixels, scene.k, scene.profile,
            gravity, SurfaceOrientation.VERTICAL,
        )

        assertEquals("gravity path selected", SolverKind.GRAVITY, result.solution.solver)
        val tol = 0.02 // 2% relative, per contract
        assertEquals("width", w, result.measurement.width, w * tol)
        assertEquals("height", h, result.measurement.height, h * tol)
        assertEquals("area", w * h, result.measurement.area, w * h * tol)
    }

    @Test fun gravityMateriallyLessAccurateAtObliqueAzimuthWhileRectangleStaysAccurate() {
        // Oblique azimuth (30deg yaw) with a small secondary pitch so BOTH solvers run: the
        // gravity solver's faces-camera assumption breaks (large height/area error) while the
        // rectangle solver still recovers truth tightly.
        val r = SceneRotations.yawPitch(yawDeg = 30.0, pitchDeg = 5.0)
        val scene = SyntheticScene(w = w, h = h, r = r, t = t, k = k, l = l)
        val gravity = gravityCam(r)

        val grav = gravityMeasure(scene, gravity)
        val gravHeightErr = kotlin.math.abs(grav.height - h) / h
        val gravAreaErr = kotlin.math.abs(grav.area - w * h) / (w * h)
        assertTrue("gravity height error must materially exceed 5% (was ${gravHeightErr * 100}%)", gravHeightErr > 0.05)
        assertTrue("gravity area error must materially exceed 5% (was ${gravAreaErr * 100}%)", gravAreaErr > 0.05)

        // Rectangle solver on the same scene stays within 0.5%.
        val rect = MetrologyEngine.measure(scene.cornerPixels, scene.stickPixels, scene.k, scene.profile)
        assertEquals("rectangle solver selected", SolverKind.RECTANGLE, rect.solution.solver)
        val tol = 0.005
        assertEquals("rect width", w, rect.measurement.width, w * tol)
        assertEquals("rect height", h, rect.measurement.height, h * tol)
        assertEquals("rect area", w * h, rect.measurement.area, w * h * tol)
        // The gravity error is far larger than the rectangle error (the whole point of fallback).
        val rectHeightErr = kotlin.math.abs(rect.measurement.height - h) / h
        assertTrue("gravity must be materially worse than rectangle", gravHeightErr > rectHeightErr * 10)
    }
}
