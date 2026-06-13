package com.cocode.measureapp.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the documented SIGN convention of [MetrologyEngine] `cameraTiltDeg` independently of
 * the production formula.
 *
 * Convention (per the corrected doc): `cameraTiltDeg` is **positive when the optical axis tips
 * UP, above the horizon**, and **negative when the camera looks DOWN toward the floor**. For a
 * perfectly level camera it is exactly `0`.
 *
 * The expected values here are HAND-COMPUTED from geometry, not re-derived from the engine's
 * `asin(opticalAxis·worldUp)` expression, so a flipped sign (or a wrong formula) is caught.
 *
 * `cameraTiltDeg` is private, so we observe it through [MetrologyEngine.measureHybrid]'s
 * `diagnostics.cameraTiltDeg`, driving a scene whose rectangle solver returns null so the
 * gravity path always runs and diagnostics are populated.
 */
class CameraTiltSignTest {
    private val k = CameraIntrinsics(fx = 1500.0, fy = 1500.0, cx = 960.0, cy = 540.0)
    private val t = Vec3(0.0, 0.0, 6.0)
    private val scene = SyntheticScene(
        w = 3.0, h = 2.0,
        r = SceneRotations.yawPitch(yawDeg = 1.5, pitchDeg = 0.0),
        t = t, k = k, l = 1.0,
    )

    private fun tiltFor(gravity: Vec3): Double {
        val result = MetrologyEngine.measureHybrid(
            scene.cornerPixels, scene.stickPixels, scene.k, scene.profile,
            gravity, SurfaceOrientation.VERTICAL,
        )
        return result.diagnostics!!.cameraTiltDeg
    }

    @Test fun lookingDownAtFloorGivesNegativeTilt() {
        // Optical axis (+z) tips toward world-down: the world-down vector, seen in the camera
        // frame, gains a +z component, so gravity = (0, cos30, sin30) = (0, 0.866, 0.5).
        // A 30deg downward look must read as a NEGATIVE tilt (looking below the horizon).
        val gravityDown = Vec3(0.0, kotlin.math.cos(Math.toRadians(30.0)), kotlin.math.sin(Math.toRadians(30.0)))
        val tilt = tiltFor(gravityDown)
        assertTrue("looking down must be negative tilt (was $tilt)", tilt < 0.0)
        assertEquals(-30.0, tilt, 1e-6)
    }

    @Test fun lookingUpAboveHorizonGivesPositiveTilt() {
        // Optical axis tips toward world-up: world-down gains a -z component in the camera
        // frame, gravity = (0, 0.866, -0.5). A 30deg upward look reads as POSITIVE tilt.
        val gravityUp = Vec3(0.0, kotlin.math.cos(Math.toRadians(30.0)), -kotlin.math.sin(Math.toRadians(30.0)))
        val tilt = tiltFor(gravityUp)
        assertTrue("looking up must be positive tilt (was $tilt)", tilt > 0.0)
        assertEquals(30.0, tilt, 1e-6)
    }

    @Test fun levelCameraGivesZeroTilt() {
        assertEquals(0.0, tiltFor(Vec3(0.0, 1.0, 0.0)), 1e-9)
    }
}
