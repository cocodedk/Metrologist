package com.cocode.measureapp.geometry

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Synthetic ground-truth oracle for [MetrologyEngine]. A real wall rectangle (W x H) plus a
 * stick of known length lying on the same plane is placed at a camera pose and projected to
 * pixels. Feeding those pixels through the full pipeline must recover `width`, `height`,
 * `area`, and `diagonal` within 0.5% of the known truth — proving the assembled math chain
 * (projectToPlane -> ScaleSolver -> scale multiply -> Measurements.compute) is metrically
 * correct, not merely non-zero. Covers frontal-ish, oblique-yaw, oblique-pitch, and
 * combined-tilt poses.
 */
class MetrologyEngineOracleTest {
    private val k = CameraIntrinsics(fx = 800.0, fy = 800.0, cx = 320.0, cy = 240.0)
    private val w = 1.2
    private val h = 0.8
    private val stickLen = 1.0
    private val profile = StickProfile(totalLength = stickLen, bandCount = 4)

    private fun rotY(a: Double) = Mat3(cos(a), 0.0, sin(a), 0.0, 1.0, 0.0, -sin(a), 0.0, cos(a))
    private fun rotX(a: Double) = Mat3(1.0, 0.0, 0.0, 0.0, cos(a), -sin(a), 0.0, sin(a), cos(a))

    private fun project(p: Vec3): Vec2 {
        val u = k.matrix() * Vec3(p.x / p.z, p.y / p.z, 1.0)
        return Vec2(u.x, u.y)
    }

    private fun place(x: Double, y: Double, r: Mat3, depth: Double): Vec3 =
        r * Vec3(x, y, 0.0) + Vec3(0.0, 0.0, depth)

    private fun cornerPixels(r: Mat3, depth: Double): List<Vec2> = listOf(
        place(-w / 2, -h / 2, r, depth), // TL
        place(w / 2, -h / 2, r, depth),  // TR
        place(w / 2, h / 2, r, depth),   // BR
        place(-w / 2, h / 2, r, depth),  // BL
    ).map { project(it) }

    private fun stickPixels(r: Mat3, depth: Double): List<Vec2> = (0..4).map { i ->
        val sx = -stickLen / 2 + stickLen * i / 4.0
        project(place(sx, 0.0, r, depth))
    }

    /** Recovered measurements must match the known wall to within 0.5% for the given pose. */
    private fun assertRecoversTruth(name: String, r: Mat3, depth: Double) {
        val result = MetrologyEngine.measure(cornerPixels(r, depth), stickPixels(r, depth), k, profile)
        val tol = 0.005 // 0.5% relative tolerance
        assertEquals("$name width", w, result.measurement.width, w * tol)
        assertEquals("$name height", h, result.measurement.height, h * tol)
        assertEquals("$name area", w * h, result.measurement.area, w * h * tol)
        assertEquals("$name diagonal", hypot(w, h), result.measurement.diagonal, hypot(w, h) * tol)
    }

    // NOTE: a pure single-axis rotation leaves one edge pair image-parallel, so its vanishing
    // point is null and the rectangle solver degenerates. The oblique cases therefore use a
    // dominant axis plus a small secondary tilt so both vanishing points stay finite.
    @Test fun recoversTruthFrontalIsh() = assertRecoversTruth("frontal", rotY(0.12) * rotX(0.08), 4.0)

    @Test fun recoversTruthObliqueYaw() = assertRecoversTruth("yaw", rotY(0.5) * rotX(0.08), 4.0)

    @Test fun recoversTruthObliquePitch() = assertRecoversTruth("pitch", rotX(0.45) * rotY(0.08), 4.0)

    @Test fun recoversTruthCombinedTilt() = assertRecoversTruth("combined", rotY(0.4) * rotX(0.25), 4.0)
}
