package com.cocode.measureapp.geometry

import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

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
     * Near-fronto-parallel (~1.5deg yaw): both edge pairs are nearly image-parallel, so the
     * rectangle path degrades. Either the vanishing point is null (engine returns the
     * confidence-0 fallback branch) or the imaged quad is nearly square and confidence stays low.
     */
    @Test fun nearFrontoParallelDegradesRectanglePath() {
        val scene = SyntheticScene(w = w, h = h, r = SceneRotations.yawPitch(yawDeg = 1.5, pitchDeg = 0.0), t = t, k = k, l = l)
        val result = MetrologyEngine.measure(scene.cornerPixels, scene.stickPixels, scene.k, scene.profile)
        assertTrue("near-fronto must degrade (confidence ${result.confidence})", result.confidence < 0.7)
    }
}
