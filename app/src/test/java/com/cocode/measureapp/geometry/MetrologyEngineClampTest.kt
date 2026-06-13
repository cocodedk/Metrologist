package com.cocode.measureapp.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pins both outcomes of the `min(scale.agreement, 1.0)` clamp in
 * [MetrologyEngine.measure] (`confidence = solution.confidence * (1 - min(agreement, 1))`).
 *
 * Both cases reuse a single valid, non-fronto-parallel corner quad so the rectangle solver
 * succeeds with `solution.confidence > 0`. Only the stick marker spacing differs, driving
 * `ScaleSolver.agreement` either at/above 1.0 (clamp fires -> overall confidence forced to
 * exactly 0.0) or below 1.0 (clamp inert -> 0 < confidence < solution.confidence). This proves
 * the clamp itself — not the solver — zeroes a successful solution, and isolates the boundary.
 */
class MetrologyEngineClampTest {
    private val k = CameraIntrinsics(fx = 800.0, fy = 800.0, cx = 320.0, cy = 240.0)
    private val w = 1.2
    private val h = 0.8
    private val depth = 4.0
    private val profile = StickProfile(totalLength = 1.0, bandCount = 4)
    private val r = rotY(0.4) * rotX(0.25)

    private fun rotY(a: Double) = Mat3(cos(a), 0.0, sin(a), 0.0, 1.0, 0.0, -sin(a), 0.0, cos(a))
    private fun rotX(a: Double) = Mat3(1.0, 0.0, 0.0, 0.0, cos(a), -sin(a), 0.0, sin(a), cos(a))

    private fun project(p: Vec3): Vec2 {
        val u = k.matrix() * Vec3(p.x / p.z, p.y / p.z, 1.0)
        return Vec2(u.x, u.y)
    }

    private fun place(x: Double, y: Double): Vec3 = r * Vec3(x, y, 0.0) + Vec3(0.0, 0.0, depth)

    private val corners = listOf(
        place(-w / 2, -h / 2), place(w / 2, -h / 2), place(w / 2, h / 2), place(-w / 2, h / 2),
    ).map { project(it) }

    /** Stick markers at given wall-x positions (all on y=0 on the same plane). */
    private fun stickAt(xs: List<Double>): List<Vec2> = xs.map { project(place(it, 0.0)) }

    @Test fun saturatingAgreementForcesConfidenceToZero() {
        // Badly mismarked joints: metric bands ~ [0.05, 0.25, 0.25, 0.45] of wall-x -> one
        // segment estimate ~2x the median, so agreement ~4.0 >= 1.0 and the clamp fires.
        val stick = stickAt(listOf(-0.5, -0.45, -0.20, 0.05, 0.5))
        val result = MetrologyEngine.measure(corners, stick, k, profile)

        assertTrue("scale agreement must saturate", result.scale.agreement >= 1.0)
        assertTrue("solver itself stays usable", result.solution.confidence > 0.0)
        // The clamp (not the solver) zeroes a successful RECTANGLE solution.
        assertEquals(0.0, result.confidence, 0.0)
    }

    @Test fun subUnitAgreementKeepsPartialConfidence() {
        // One overstretched band -> agreement ~0.94, just below the clamp threshold.
        val stick = stickAt(listOf(-0.5, -0.45, -0.40, -0.35, 0.5))
        val result = MetrologyEngine.measure(corners, stick, k, profile)

        assertTrue("agreement below clamp", result.scale.agreement > 0.0 && result.scale.agreement < 1.0)
        assertTrue("solver usable", result.solution.confidence > 0.0)
        // Clamp inert: confidence is attenuated but strictly between 0 and the solver's.
        assertTrue("0 < confidence", result.confidence > 0.0)
        assertTrue("confidence < solution.confidence", result.confidence < result.solution.confidence)
        assertEquals(
            result.solution.confidence * (1.0 - result.scale.agreement),
            result.confidence,
            1e-12,
        )
    }
}
