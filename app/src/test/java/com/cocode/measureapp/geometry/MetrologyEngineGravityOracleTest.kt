package com.cocode.measureapp.geometry

import com.cocode.measureapp.geometry.rectangle.RectangleCandidate
import com.cocode.measureapp.stick.StickScale
import org.junit.Assert.assertEquals
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
 *    (asserted via `diagnostics.solver`); a fronto-parallel scene still yields a finite
 *    measurement. Its image-parallel edges are a vanishing point at infinity, which the
 *    rectangle candidate solves exactly (node 01); solver choice there is node 03's.
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
        val scale = StickScale.solve(stickMetric, scene.profile)
        return Measurements.compute(cornerMetric.map { it * scale.scale })
    }

    @Test fun gravityRecoversTruthWhenWallFacesCamera() {
        // Near-fronto pure yaw: the wall faces the camera, so the gravity pipeline alone
        // (no selector) stays within 2%.
        val scene = scene(SceneRotations.yawPitch(yawDeg = 1.5, pitchDeg = 0.0))
        val result = gravityOnly(scene)
        val tol = 0.02 // 2% relative, per contract
        assertEquals("width", w, result.width, w * tol)
        assertEquals("height", h, result.height, h * tol)
        assertEquals("area", w * h, result.area, w * h * tol)
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

    @Test fun nearFrontoParallelRectangleCandidateRecoversTruth() {
        // Pure tiny yaw leaves the vertical edges image-parallel (vanishing point at infinity).
        // That is a valid direction: the rectangle candidate recovers the oracle exactly.
        val scene = scene(SceneRotations.yawPitch(yawDeg = 1.5, pitchDeg = 0.0))
        val c = RectangleSolver.candidate(scene.cornerPixels, scene.k)
        assertTrue("candidate rejected: $c", c is RectangleCandidate.Accepted)
        val frame = (c as RectangleCandidate.Accepted).frame
        val scale = StickScale.solve(projectToPlane(scene.stickPixels, scene.k, frame), scene.profile)
        val m = Measurements.compute(projectToPlane(scene.cornerPixels, scene.k, frame).map { it * scale.scale })
        assertEquals("width", w, m.width, w * 1e-6)
        assertEquals("height", h, m.height, h * 1e-6)
        assertEquals("area", w * h, m.area, w * h * 1e-6)
    }

    @Test fun hybridStillYieldsFiniteMeasurementOnFrontoParallelScene() {
        // Solver choice for this scene belongs to the selector node; whichever it picks, the
        // hybrid path must produce a finite, positive measurement.
        val scene = scene(SceneRotations.yawPitch(yawDeg = 1.5, pitchDeg = 0.0))

        val result = MetrologyEngine.measureHybrid(
            scene.cornerPixels, scene.stickPixels, scene.k, scene.profile,
            scene.gravityCam, SurfaceOrientation.VERTICAL,
        )

        assertTrue("diagnostics present", result.diagnostics != null)
        assertTrue("finite width", result.measurement.width.isFinite())
        assertTrue("finite height", result.measurement.height.isFinite())
        assertTrue("finite area", result.measurement.area.isFinite())
        assertTrue("positive area", result.measurement.area > 0.0)
    }
}
