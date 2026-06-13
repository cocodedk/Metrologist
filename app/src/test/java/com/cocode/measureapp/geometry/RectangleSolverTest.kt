package com.cocode.measureapp.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class RectangleSolverTest {
    private val k = CameraIntrinsics(fx = 800.0, fy = 800.0, cx = 320.0, cy = 240.0)

    /** Rotation about the Y axis (yaw), in radians. */
    private fun rotY(a: Double) = Mat3(
        cos(a), 0.0, sin(a),
        0.0, 1.0, 0.0,
        -sin(a), 0.0, cos(a),
    )

    /** Rotation about the X axis (pitch), in radians. */
    private fun rotX(a: Double) = Mat3(
        1.0, 0.0, 0.0,
        0.0, cos(a), -sin(a),
        0.0, sin(a), cos(a),
    )

    /** Project a camera-frame 3D point to a pixel. */
    private fun project(p: Vec3): Vec2 {
        val u = k.matrix() * Vec3(p.x / p.z, p.y / p.z, 1.0)
        return Vec2(u.x, u.y)
    }

    /**
     * Build pixel corners [TL,TR,BR,BL] for a planar rectangle of size w x h,
     * rotated by [r] and placed at depth [depth] in front of the camera.
     */
    private fun rectPixels(w: Double, h: Double, r: Mat3, depth: Double): List<Vec2> {
        val tl = Vec3(-w / 2, -h / 2, 0.0)
        val tr = Vec3(w / 2, -h / 2, 0.0)
        val br = Vec3(w / 2, h / 2, 0.0)
        val bl = Vec3(-w / 2, h / 2, 0.0)
        val t = Vec3(0.0, 0.0, depth)
        return listOf(tl, tr, br, bl).map { project(r * it + t) }
    }

    @Test fun frameIsOrthonormalForTiltedRectangle() {
        val r = rotY(0.4) * rotX(0.25)
        val corners = rectPixels(1.2, 0.8, r, 4.0)
        val sol = RectangleSolver.solve(corners, k)!!
        val f = sol.frame
        assertEquals(1.0, f.e1.norm(), 1e-9)
        assertEquals(1.0, f.e2.norm(), 1e-9)
        assertEquals(1.0, f.normal.norm(), 1e-9)
        assertEquals(0.0, f.e1.dot(f.e2), 1e-9)
        assertEquals(0.0, f.e1.dot(f.normal), 1e-9)
        assertEquals(0.0, f.e2.dot(f.normal), 1e-9)
        assertEquals(SolverKind.RECTANGLE, sol.solver)
    }

    @Test fun frontoParallelReturnsNull() {
        // Both edge pairs parallel in the image -> both vanishing points null.
        val corners = rectPixels(1.2, 0.8, Mat3(
            1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0,
        ), 4.0)
        assertNull(RectangleSolver.solve(corners, k))
    }

    @Test fun pureYawReturnsNullViaVp2Branch() {
        // Pure yaw: top/bottom edges converge (vp1 finite) but left/right edges stay
        // image-parallel (vp2 == null). Exercises the second `?: return null` (vp2).
        val corners = rectPixels(1.2, 0.8, rotY(0.4), 4.0)
        assertNull(RectangleSolver.solve(corners, k))
    }

    @Test fun purePitchReturnsNullViaVp1Branch() {
        // Pure pitch: left/right edges converge (vp2 finite) but top/bottom edges stay
        // image-parallel (vp1 == null). Exercises the first `?: return null` (vp1).
        val corners = rectPixels(1.2, 0.8, rotX(0.4), 4.0)
        assertNull(RectangleSolver.solve(corners, k))
    }

    @Test fun coincidentVanishingPointsReturnsNull() {
        // Degenerate size-4 quad with a repeated vertex (TR == BL). Both vanishing
        // points are non-null yet coincide, so the back-projected directions are
        // parallel and their cross product is the zero vector. The solver must return
        // null rather than throw on normalize().
        val corners = listOf(
            Vec2(100.0, 100.0), // TL
            Vec2(300.0, 200.0), // TR
            Vec2(500.0, 100.0), // BR
            Vec2(300.0, 200.0), // BL == TR
        )
        assertNull(RectangleSolver.solve(corners, k))
    }

    @Test(expected = IllegalArgumentException::class)
    fun wrongCornerCountThrows() {
        RectangleSolver.solve(listOf(Vec2(0.0, 0.0), Vec2(1.0, 0.0), Vec2(1.0, 1.0)), k)
    }

    @Test fun skewedConfigurationHasLowerConfidenceThanNearSquare() {
        val rNearSquare = rotY(0.05) * rotX(0.04)
        val nearSquare = RectangleSolver.solve(rectPixels(1.0, 1.0, rNearSquare, 4.0), k)!!

        val rSkew = rotY(0.9) * rotX(0.7)
        val skew = RectangleSolver.solve(rectPixels(1.0, 1.0, rSkew, 4.0), k)!!

        // Confidence must MEASURE image-quad skew, not merely differ by rounding noise:
        // demand a meaningful gap so the bit-identical `1 - |d1.dot(d2)|` formula fails.
        assertTrue(
            "skew=${skew.confidence} should be meaningfully < nearSquare=${nearSquare.confidence}",
            skew.confidence < nearSquare.confidence - 0.05,
        )
        assertTrue(nearSquare.confidence in 0.0..1.0)
        assertTrue(skew.confidence in 0.0..1.0)
        assertTrue(abs(1.0 - nearSquare.confidence) < 0.05)
    }

    @Test fun squareImageQuadHasFullConfidence() {
        // A perfectly axis-aligned square in the image has four 90 degree corners, so a
        // skew-based confidence is exactly 1.0. (vps are at infinity for a true square,
        // so feed a converging-but-right-angled trapezoid is not possible; instead assert
        // confidence stays in range and high for a near-square pose.)
        val r = rotY(0.05) * rotX(0.04)
        val sol = RectangleSolver.solve(rectPixels(1.0, 1.0, r, 4.0), k)!!
        assertTrue(sol.confidence in 0.0..1.0)
        assertTrue("near-square confidence ${sol.confidence} should be high", sol.confidence > 0.95)
    }
}
