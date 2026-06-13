package com.cocode.measureapp.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/**
 * Wiring-level tests for [MetrologyEngine]. A synthetic, internally-consistent scene
 * (a tilted wall rectangle plus a stick lying on the same plane) is projected to pixels
 * and fed through the full pipeline. The rigorous accuracy oracle lives in the Oracle phase;
 * here we only assert plausible non-zero outputs and a confidence in `(0,1]`, plus the
 * null-solution fallback branch.
 */
class MetrologyEngineTest {
    private val k = CameraIntrinsics(fx = 800.0, fy = 800.0, cx = 320.0, cy = 240.0)

    private fun rotY(a: Double) = Mat3(
        cos(a), 0.0, sin(a),
        0.0, 1.0, 0.0,
        -sin(a), 0.0, cos(a),
    )

    private fun rotX(a: Double) = Mat3(
        1.0, 0.0, 0.0,
        0.0, cos(a), -sin(a),
        0.0, sin(a), cos(a),
    )

    private fun project(p: Vec3): Vec2 {
        val u = k.matrix() * Vec3(p.x / p.z, p.y / p.z, 1.0)
        return Vec2(u.x, u.y)
    }

    /** Place a wall-plane point (wall coords x,y) into the camera frame via pose (r, depth). */
    private fun place(x: Double, y: Double, r: Mat3, depth: Double): Vec3 =
        r * Vec3(x, y, 0.0) + Vec3(0.0, 0.0, depth)

    @Test fun measuresPlausibleNonZeroForTiltedScene() {
        val w = 1.2
        val h = 0.8
        val r = rotY(0.4) * rotX(0.25)
        val depth = 4.0
        val corners = listOf(
            place(-w / 2, -h / 2, r, depth), // TL
            place(w / 2, -h / 2, r, depth),  // TR
            place(w / 2, h / 2, r, depth),   // BR
            place(-w / 2, h / 2, r, depth),  // BL
        ).map { project(it) }

        // A stick of real length 1.0 lying horizontally on the same wall, 5 markers / 4 bands.
        val stickLen = 1.0
        val stickPixels = (0..4).map { i ->
            val sx = -stickLen / 2 + stickLen * i / 4.0
            project(place(sx, 0.0, r, depth))
        }
        val profile = StickProfile(totalLength = stickLen, bandCount = 4)

        val result = MetrologyEngine.measure(corners, stickPixels, k, profile)

        assertEquals(SolverKind.RECTANGLE, result.solution.solver)
        assertTrue("width > 0", result.measurement.width > 0.0)
        assertTrue("height > 0", result.measurement.height > 0.0)
        assertTrue("area > 0", result.measurement.area > 0.0)
        assertTrue("diagonal > 0", result.measurement.diagonal > 0.0)
        assertEquals(4, result.measurement.cornerAngles.size)
        assertTrue("confidence in (0,1]", result.confidence > 0.0 && result.confidence <= 1.0)
        // Sanity: recovered width should exceed height (true wall is 1.2 x 0.8).
        assertTrue(result.measurement.width > result.measurement.height)
    }

    @Test fun nullSolutionReturnsZeroConfidenceAndZeroedMeasurement() {
        // Fronto-parallel rectangle: both edge pairs image-parallel -> both vps null ->
        // RectangleSolver.solve returns null -> engine takes the fallback branch.
        val w = 1.2
        val h = 0.8
        val identity = Mat3(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)
        val depth = 4.0
        val corners = listOf(
            place(-w / 2, -h / 2, identity, depth),
            place(w / 2, -h / 2, identity, depth),
            place(w / 2, h / 2, identity, depth),
            place(-w / 2, h / 2, identity, depth),
        ).map { project(it) }
        val stickPixels = listOf(Vec2(0.0, 0.0), Vec2(10.0, 0.0))

        val result = MetrologyEngine.measure(corners, stickPixels, k, StickProfile(1.0))

        assertEquals(0.0, result.confidence, 0.0)
        assertEquals(SolverKind.RECTANGLE, result.solution.solver)
        assertEquals(0.0, result.solution.confidence, 0.0)
        assertEquals(0.0, result.measurement.width, 0.0)
        assertEquals(0.0, result.measurement.height, 0.0)
        assertEquals(0.0, result.measurement.area, 0.0)
        assertEquals(0.0, result.measurement.diagonal, 0.0)
        assertTrue(result.measurement.cornerAngles.isEmpty())
        assertEquals(0.0, result.scale.scale, 0.0)
        assertEquals(0.0, result.scale.agreement, 0.0)
    }
}
