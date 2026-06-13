package com.cocode.measureapp.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Ground-truth oracle for the **gravity path + auto-selector** (plan §75-89).
 *
 * The camera-frame gravity is taken straight from the pose via [SyntheticScene.gravityCam]
 * (`R * worldDown`, `worldDown = (0,1,0)`, normalized) — it is NEVER derived from any solver,
 * so feeding it back through [MetrologyEngine.measureHybrid] is a genuine round-trip check.
 *
 * Three guarantees, each demanded by the contract:
 * 1. **Wall faces the camera** (tiny yaw): the gravity pipeline recovers width/height/area
 *    within **<=2%** of truth.
 * 2. **Oblique azimuth** (large yaw): gravity-only recovery error is **materially larger**
 *    (documents the faces-camera assumption) while the RectangleSolver-based measure on the
 *    SAME scene stays within **0.5%**.
 * 3. **Selector behavior**: a clean oblique rectangle scene selects [SolverKind.RECTANGLE]
 *    (asserted via `diagnostics.solver`); a fronto-parallel scene (RectangleSolver returns
 *    null) selects [SolverKind.GRAVITY] and still yields a finite measurement.
 */
class MetrologyEngineGravityOracleTest {
    private val w = 3.0
    private val h = 2.0
    private val l = 1.0
    private val k = CameraIntrinsics(fx = 1500.0, fy = 1500.0, cx = 960.0, cy = 540.0)
    private val t = Vec3(0.0, 0.0, 6.0)

    private fun scene(r: Mat3) = SyntheticScene(w = w, h = h, r = r, t = t, k = k, l = l)

    /** Gravity-only pipeline (no selector): solve plane, project, scale, measure. */
    private fun gravityOnly(scene: SyntheticScene): MeasurementResult {
        val sol = GravitySolver.solve(scene.gravityCam, SurfaceOrientation.VERTICAL)
        val cornerMetric = projectToPlane(scene.cornerPixels, scene.k, sol.frame)
        val stickMetric = projectToPlane(scene.stickPixels, scene.k, sol.frame)
        val scale = ScaleSolver.solve(stickMetric, scene.profile)
        return Measurements.compute(cornerMetric.map { it * scale.scale })
    }

    @Test fun gravityRecoversTruthWhenWallFacesCamera() {
        // Near-fronto pure yaw: RectangleSolver returns null, so measureHybrid is forced onto
        // the gravity path. The wall faces the camera, so gravity stays within 2%.
        val scene = scene(SceneRotations.yawPitch(yawDeg = 1.5, pitchDeg = 0.0))
        assertNull(
            "rectangle must be null to force gravity",
            RectangleSolver.solve(scene.cornerPixels, scene.k),
        )

        val result = MetrologyEngine.measureHybrid(
            scene.cornerPixels, scene.stickPixels, scene.k, scene.profile,
            scene.gravityCam, SurfaceOrientation.VERTICAL,
        )

        assertEquals("gravity path selected", SolverKind.GRAVITY, result.solution.solver)
        val tol = 0.02 // 2% relative, per contract
        assertEquals("width", w, result.measurement.width, w * tol)
        assertEquals("height", h, result.measurement.height, h * tol)
        assertEquals("area", w * h, result.measurement.area, w * h * tol)
    }

    @Test fun gravityMateriallyWorseAtObliqueAzimuthWhileRectangleStaysAccurate() {
        // Oblique azimuth (30deg yaw) + small secondary pitch so BOTH solvers run.
        val scene = scene(SceneRotations.yawPitch(yawDeg = 30.0, pitchDeg = 5.0))

        val grav = gravityOnly(scene)
        val gravHeightErr = abs(grav.height - h) / h
        val gravAreaErr = abs(grav.area - w * h) / (w * h)
        assertTrue(
            "gravity height error must materially exceed 5% (was ${gravHeightErr * 100}%)",
            gravHeightErr > 0.05,
        )
        assertTrue(
            "gravity area error must materially exceed 5% (was ${gravAreaErr * 100}%)",
            gravAreaErr > 0.05,
        )

        // RectangleSolver-based measure on the same scene stays within 0.5%.
        val rect = MetrologyEngine.measure(
            scene.cornerPixels, scene.stickPixels, scene.k, scene.profile,
        )
        assertEquals("rectangle solver selected", SolverKind.RECTANGLE, rect.solution.solver)
        val tol = 0.005
        assertEquals("rect width", w, rect.measurement.width, w * tol)
        assertEquals("rect height", h, rect.measurement.height, h * tol)
        assertEquals("rect area", w * h, rect.measurement.area, w * h * tol)
        val rectHeightErr = abs(rect.measurement.height - h) / h
        assertTrue(
            "gravity must be materially worse than rectangle",
            gravHeightErr > rectHeightErr * 10,
        )
    }

    @Test fun selectorPicksRectangleOnCleanObliqueScene() {
        // A well-conditioned oblique quad: the selector must prefer the rectangle solver.
        val scene = scene(SceneRotations.yawPitch(yawDeg = 25.0, pitchDeg = 20.0))

        val result = MetrologyEngine.measureHybrid(
            scene.cornerPixels, scene.stickPixels, scene.k, scene.profile,
            scene.gravityCam, SurfaceOrientation.VERTICAL,
        )

        val diag = result.diagnostics!!
        assertEquals("selector picks RECTANGLE on clean oblique", SolverKind.RECTANGLE, diag.solver)
        assertEquals("solution agrees with diagnostics", SolverKind.RECTANGLE, result.solution.solver)
    }

    @Test fun selectorFallsBackToGravityOnFrontoParallelScene() {
        // Fronto-parallel (pure tiny yaw): RectangleSolver returns null, so the selector
        // must fall back to GRAVITY and still produce a finite measurement.
        val scene = scene(SceneRotations.yawPitch(yawDeg = 1.5, pitchDeg = 0.0))
        assertNull(
            "rectangle must be null on fronto-parallel scene",
            RectangleSolver.solve(scene.cornerPixels, scene.k),
        )

        val result = MetrologyEngine.measureHybrid(
            scene.cornerPixels, scene.stickPixels, scene.k, scene.profile,
            scene.gravityCam, SurfaceOrientation.VERTICAL,
        )

        val diag = result.diagnostics!!
        assertEquals("selector falls back to GRAVITY", SolverKind.GRAVITY, diag.solver)
        assertTrue("finite width", result.measurement.width.isFinite())
        assertTrue("finite height", result.measurement.height.isFinite())
        assertTrue("finite area", result.measurement.area.isFinite())
        assertTrue("positive area", result.measurement.area > 0.0)
    }
}
