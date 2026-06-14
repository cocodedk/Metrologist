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
 * succeeds with `solution.confidence > 0`. The stick is a real box of fixed length and a fixed
 * ACTUAL width on the wall; only the DECLARED [StickProfile.width] differs, driving the
 * length-vs-width scale disagreement [StickScale] reports either at/above 1.0 (clamp fires ->
 * overall confidence forced to exactly 0.0) or below 1.0 (clamp inert -> 0 < confidence <
 * solution.confidence). This proves the clamp itself — not the solver — zeroes a successful
 * solution, and isolates the boundary.
 */
class MetrologyEngineClampTest {
    private val k = CameraIntrinsics(fx = 800.0, fy = 800.0, cx = 320.0, cy = 240.0)
    private val w = 1.2
    private val h = 0.8
    private val depth = 4.0
    private val stickLen = 1.0
    private val stickWidthActual = 0.04
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

    /** The stick's real box (length [stickLen] x [stickWidthActual]) projected to 4 corners. */
    private val stick = listOf(
        place(-stickLen / 2, -stickWidthActual / 2), // TL
        place(stickLen / 2, -stickWidthActual / 2),  // TR
        place(stickLen / 2, stickWidthActual / 2),   // BR
        place(-stickLen / 2, stickWidthActual / 2),  // BL
    ).map { project(it) }

    @Test fun saturatingAgreementForcesConfidenceToZero() {
        // Declared width 10x the real width -> scaleWid ~ 10x scaleLen -> agreement ~2.5 >= 1.0.
        val profile = StickProfile(totalLength = stickLen, bandCount = 4, width = stickWidthActual * 10.0)
        val result = MetrologyEngine.measure(corners, stick, k, profile)

        assertTrue("scale agreement must saturate", result.scale.agreement >= 1.0)
        assertTrue("solver itself stays usable", result.solution.confidence > 0.0)
        // The clamp (not the solver) zeroes a successful RECTANGLE solution.
        assertEquals(0.0, result.confidence, 0.0)
    }

    @Test fun subUnitAgreementKeepsPartialConfidence() {
        // Declared width 1.25x the real width -> mild length-vs-width disagreement below 1.0.
        val profile = StickProfile(totalLength = stickLen, bandCount = 4, width = stickWidthActual * 1.25)
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
