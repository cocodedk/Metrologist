package com.cocode.measureapp.geometry

import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.sqrt

/**
 * Synthetic ground-truth oracle for [MetrologyEngine] — the correctness backbone.
 *
 * A known real wall (W x H) plus a stick of known length lying on the same plane is placed at
 * a camera pose and projected to pixels by [SyntheticScene] with an INDEPENDENT pinhole model
 * (it never calls the engine). Feeding those pixels through the full engine pipeline
 * (RectangleSolver -> projectToPlane -> ScaleSolver -> Measurements) must recover `width`,
 * `height`, and `area` within 0.5% of truth with confidence > 0.7, proving the assembled math
 * is metrically correct.
 *
 * Poses: small yaw (~10deg), oblique yaw (~30deg), oblique pitch (~25deg), combined yaw+pitch.
 * Plus a near-fronto-parallel pose (~1-2deg yaw) that exercises the degenerate / low-confidence
 * branch of the rectangle path.
 *
 * Pure single-axis rotations leave one edge pair image-parallel (a null vanishing point), so the
 * oblique poses pair a dominant axis with a small secondary tilt to keep both vanishing points
 * finite — except the deliberate near-fronto-parallel degenerate case.
 */
class MetrologyEngineOracleTest {
    private val w = 3.0
    private val h = 2.0
    private val l = 1.0
    private val k = CameraIntrinsics(fx = 1500.0, fy = 1500.0, cx = 960.0, cy = 540.0)
    private val t = Vec3(0.0, 0.0, 6.0) // wall in front of camera (positive depth)

    private fun assertRecoversTruth(name: String, r: Mat3) {
        val scene = SyntheticScene(w = w, h = h, r = r, t = t, k = k, l = l)
        val result = MetrologyEngine.measure(scene.cornerPixels, scene.stickPixels, scene.k, scene.profile)
        val tol = 0.005 // 0.5% relative
        assertEquals("$name width", w, result.measurement.width, w * tol)
        assertEquals("$name height", h, result.measurement.height, h * tol)
        assertEquals("$name area", w * h, result.measurement.area, w * h * tol)
        assertTrue("$name confidence > 0.7 (was ${result.confidence})", result.confidence > 0.7)
        val trueDiagonal = sqrt(w * w + h * h)
        assertEquals("$name diagonal", trueDiagonal, result.measurement.diagonal, trueDiagonal * tol)
        for ((i, angle) in result.measurement.cornerAngles.withIndex()) {
            assertEquals("$name corner[$i] angle ~90°", 90.0, angle, 1.0)
        }
    }

    @Test fun recoversTruthSmallYaw() =
        assertRecoversTruth("small-yaw", SceneRotations.yawPitch(yawDeg = 10.0, pitchDeg = 5.0))

    @Test fun recoversTruthObliqueYaw() =
        assertRecoversTruth("oblique-yaw", SceneRotations.yawPitch(yawDeg = 30.0, pitchDeg = 5.0))

    @Test fun recoversTruthObliquePitch() =
        assertRecoversTruth("oblique-pitch", SceneRotations.yawPitch(yawDeg = 5.0, pitchDeg = 25.0))

    @Test fun recoversTruthCombinedYawPitch() =
        assertRecoversTruth("combined", SceneRotations.yawPitch(yawDeg = 25.0, pitchDeg = 20.0))

    /**
     * Pure-yaw near-fronto-parallel (~1.5deg yaw, 0 pitch): the left/right edges (TL->BL, TR->BR)
     * stay exactly image-parallel, so their vanishing point `vp2 = vanishingPoint(TL,BL, TR,BR)`
     * is null. [RectangleSolver.solve] therefore returns null and [MetrologyEngine.measure] takes
     * its confidence-0 fallback branch. This exercises that specific null-second-vanishing-point
     * fallback — not a "nearly square but finite" path: with any tiny finite pitch both vanishing
     * points become finite, the solver succeeds, and the imaged quad's confidence is ~0.997 (a
     * near-square quad has HIGH, not low, confidence), so that alternative would not be degenerate.
     */
    @Test fun nearFrontoParallelNullVanishingPointFallback() {
        val r = SceneRotations.yawPitch(yawDeg = 1.5, pitchDeg = 0.0)
        val scene = SyntheticScene(w = w, h = h, r = r, t = t, k = k, l = l)
        val (tl, tr, br, bl) = scene.cornerPixels
        // The branch under test: pure yaw keeps the vertical edges image-parallel -> null vp2.
        assertEquals("vertical-edge vanishing point must be null under pure yaw", null, Projective.vanishingPoint(tl, bl, tr, br))
        assertEquals("solver must return null on the null-vanishing-point path", null, RectangleSolver.solve(scene.cornerPixels, scene.k))
        val result = MetrologyEngine.measure(scene.cornerPixels, scene.stickPixels, scene.k, scene.profile)
        assertEquals("engine must take the confidence-0 fallback", 0.0, result.confidence, 0.0)
        assertTrue("fallback confidence is below the 0.7 acceptance bar", result.confidence < 0.7)
    }
}
