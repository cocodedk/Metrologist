package com.cocode.measureapp.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end test of a **usable HORIZONTAL (floor)** gravity measurement through
 * [MetrologyEngine.measureHybrid].
 *
 * The other hybrid tests only ever drive HORIZONTAL with confidence `0.0` (the zeroed branch),
 * so the non-degenerate floor basis (`fwd.normalized()` / `worldUp` normal) was never exercised
 * end-to-end — a wrong floor normal or basis would have gone uncaught at the engine level.
 *
 * Floor scene: [SyntheticScene] always builds a rectangle in the world `z = 0` plane, so to
 * treat it as a **floor** the world-down direction is the plane's own normal `(0, 0, 1)`; the
 * camera-frame gravity is then `gravityCam = R * (0, 0, 1)`. With that self-consistent gravity
 * the HORIZONTAL solver's plane normal equals the true plane normal, so the recovered metric is
 * exact. A near-fronto pure-yaw pose keeps the rectangle solver null, forcing GRAVITY selection.
 */
class MetrologyEngineHorizontalHybridTest {
    private val w = 3.0
    private val h = 2.0
    private val l = 1.0
    private val k = CameraIntrinsics(fx = 1500.0, fy = 1500.0, cx = 960.0, cy = 540.0)
    private val t = Vec3(0.0, 0.0, 6.0)

    /** Camera-frame gravity for a FLOOR pose: world down is the plane normal `(0,0,1)`. */
    private fun floorGravity(r: Mat3): Vec3 = r * Vec3(0.0, 0.0, 1.0)

    @Test fun hybridSelectsHorizontalGravityAndRecoversFloorMeasurement() {
        val r = SceneRotations.yawPitch(yawDeg = 1.5, pitchDeg = 0.0)
        val scene = SyntheticScene(w = w, h = h, r = r, t = t, k = k, l = l)
        val gravity = floorGravity(r)

        // Rectangle must be null so the selector falls through to the gravity floor solution.
        assertNull("rectangle must be null", RectangleSolver.solve(scene.cornerPixels, scene.k))
        // The HORIZONTAL gravity solution is usable (confidence > 0), so GRAVITY is selected.
        val grav = GravitySolver.solve(gravity, SurfaceOrientation.HORIZONTAL)
        assertTrue("horizontal confidence > 0", grav.confidence > 0.0)

        val result = MetrologyEngine.measureHybrid(
            scene.cornerPixels, scene.stickPixels, scene.k, scene.profile,
            gravity, SurfaceOrientation.HORIZONTAL,
        )

        assertEquals("gravity floor path selected", SolverKind.GRAVITY, result.solution.solver)
        // The selected floor solution's normal is the world-up (floor normal) and its frame is
        // a valid orthonormal basis — pins the HORIZONTAL normal/basis construction end-to-end.
        val worldUp = (gravity * -1.0).normalized()
        val frame = result.solution.frame
        assertEquals("normal.x = worldUp.x", worldUp.x, frame.normal.x, 1e-9)
        assertEquals("normal.y = worldUp.y", worldUp.y, frame.normal.y, 1e-9)
        assertEquals("normal.z = worldUp.z", worldUp.z, frame.normal.z, 1e-9)
        assertEquals("|e1|", 1.0, frame.e1.norm(), 1e-9)
        assertEquals("|e2|", 1.0, frame.e2.norm(), 1e-9)
        assertEquals("e1·e2", 0.0, frame.e1.dot(frame.e2), 1e-9)
        assertEquals("e1·normal", 0.0, frame.e1.dot(frame.normal), 1e-9)
        assertEquals("e2·normal", 0.0, frame.e2.dot(frame.normal), 1e-9)
        // Self-consistent floor gravity -> the recovered metric is exact (well within 2%).
        val tol = 0.02
        assertEquals("width", w, result.measurement.width, w * tol)
        assertEquals("height", h, result.measurement.height, h * tol)
        assertEquals("area", w * h, result.measurement.area, w * h * tol)
        assertTrue("non-zero finite area", result.measurement.area.isFinite() && result.measurement.area > 0.0)
        assertTrue("confidence in (0,1]", result.confidence > 0.0 && result.confidence <= 1.0)

        val diag = result.diagnostics!!
        assertEquals(SolverKind.GRAVITY, diag.solver)
        assertTrue("diagnostic scale > 0", diag.scale > 0.0)
    }
}
