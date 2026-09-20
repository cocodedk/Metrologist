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
 * exact. The rectangle candidate is also eligible and floor-consistent for that pose.
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

        // The HORIZONTAL gravity frame: normal is world-up and the basis is orthonormal.
        val worldUp = (gravity * -1.0).normalized()
        val frame = GravitySolver.solve(gravity, SurfaceOrientation.HORIZONTAL).frame
        assertEquals("normal.x = worldUp.x", worldUp.x, frame.normal.x, 1e-9)
        assertEquals("normal.y = worldUp.y", worldUp.y, frame.normal.y, 1e-9)
        assertEquals("normal.z = worldUp.z", worldUp.z, frame.normal.z, 1e-9)

        // Both the floor-consistent rectangle and the gravity floor are eligible here; ranking
        // is by confidence. Either way the selected plane is the floor and the metric is exact.
        val result = MetrologyEngine.measureHybrid(
            scene.cornerPixels, scene.stickPixels, scene.k, scene.profile,
            gravity, SurfaceOrientation.HORIZONTAL,
        )
        val selected = result.solution.frame
        assertEquals("selected normal is the floor normal", 1.0, kotlin.math.abs(selected.normal.dot(worldUp)), 1e-9)
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
        assertEquals(result.solution.solver, diag.solver)
        assertTrue("diagnostic scale > 0", diag.scale > 0.0)
    }

    /** Independent pinhole projection of a floor point `(x, y, 0)` for a straight-down camera. */
    private fun projectDown(x: Double, y: Double, depth: Double): Vec2 {
        val u = k.matrix() * (Vec3(x, y, depth) * (1.0 / depth))
        return Vec2(u.x, u.y)
    }

    /**
     * Convex NON-rectangular floor (a slanted parallelogram) seen by a camera looking straight
     * down. Its image edges stay parallel, so no rectangle solution exists and only the
     * horizontal gravity path can recover it — the rectangle solver cannot mask that path.
     */
    @Test fun horizontalGravityRecoversConvexNonRectangularFloor() {
        val depth = 3.0
        val world = listOf(Vec2(-1.0, -0.5), Vec2(1.0, -0.5), Vec2(1.4, 0.5), Vec2(-0.6, 0.5))
        val sw = 0.08
        val stickWorld = listOf(
            Vec2(-0.5, 0.8 - sw / 2), Vec2(0.5, 0.8 - sw / 2), Vec2(0.5, 0.8 + sw / 2), Vec2(-0.5, 0.8 + sw / 2),
        )
        val corners = world.map { projectDown(it.x, it.y, depth) }
        val stick = stickWorld.map { projectDown(it.x, it.y, depth) }
        val gravity = Vec3(0.0, 0.0, 1.0)   // camera z points at the floor: world down
        val expected = Measurements.compute(world)
        assertTrue("fixture is non-rectangular", expected.cornerAngles.any { kotlin.math.abs(it - 90.0) > 10.0 })

        assertNull("no rectangle solution", RectangleSolver.solve(corners, k))
        val result = MetrologyEngine.measureHybrid(
            corners, stick, k, StickProfile(1.0, width = sw), gravity, SurfaceOrientation.HORIZONTAL,
        )

        assertEquals(SolverKind.GRAVITY, result.solution.solver)
        assertTrue("usable", result.confidence > 0.0)
        val m = result.measurement
        assertEquals("width", expected.width, m.width, 1e-6)
        assertEquals("height", expected.height, m.height, 1e-6)
        assertEquals("area", expected.area, m.area, 1e-6)
        assertEquals("diagonal", expected.diagonal, m.diagonal, 1e-6)
        for (i in 0..3) assertEquals("angle $i", expected.cornerAngles[i], m.cornerAngles[i], 1e-4)
    }

    /** The same straight-down floor under a wall assumption has no usable wall plane. */
    @Test fun wallAssumptionOnStraightDownFloorIsNotUsable() {
        val corners = listOf(Vec2(-1.0, -0.5), Vec2(1.0, -0.5), Vec2(1.0, 0.5), Vec2(-1.0, 0.5))
            .map { projectDown(it.x, it.y, 3.0) }
        val stick = listOf(Vec2(-0.5, 0.7), Vec2(0.5, 0.7), Vec2(0.5, 0.78), Vec2(-0.5, 0.78))
            .map { projectDown(it.x, it.y, 3.0) }
        val result = MetrologyEngine.measureHybrid(
            corners, stick, k, StickProfile(1.0, width = 0.08), Vec3(0.0, 0.0, 1.0), SurfaceOrientation.VERTICAL,
        )
        assertEquals("wall assumption cannot yield a usable floor result", 0.0, result.confidence, 0.0)
    }
}
