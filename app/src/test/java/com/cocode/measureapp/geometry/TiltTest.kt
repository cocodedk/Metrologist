package com.cocode.measureapp.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin

/**
 * Overlay tilt math. [tiltFromGravity] takes physical down in VIEW axes (x right, y down,
 * z away from the user); [viewDownFromDevice] owns display rotation. Device-axis fixtures
 * are derived from poses: a device turned `a` degrees counter-clockwise (as seen by the
 * user) has physical down `(-sin a, -cos a, 0)` in device axes (x right, y natural top).
 */
class TiltTest {

    private val eps = 1e-9

    private fun screenTurnDown(ccwDeg: Double): Vec3 {
        val a = Math.toRadians(ccwDeg)
        return Vec3(-sin(a), -cos(a), 0.0)
    }

    @Test fun uprightIsZeroAndLevel() {
        val t = tiltFromGravity(Vec3(0.0, 1.0, 0.0))!!
        assertEquals(0.0, t.pitchDeg, eps)
        assertEquals(0.0, t.rollDeg, eps)
        assertTrue(t.isLevel())
    }

    @Test fun flatToFloorIsNinetyPitchAndLevel() {
        val t = tiltFromGravity(Vec3(0.0, 0.0, 1.0))!!
        assertEquals(90.0, t.pitchDeg, eps)
        assertEquals(0.0, t.rollDeg, eps)
        assertTrue(t.isLevel())
    }

    @Test fun flatToCeilingIsMinusNinetyAndLevel() {
        val t = tiltFromGravity(Vec3(0.0, 0.0, -1.0))!!
        assertEquals(-90.0, t.pitchDeg, eps)
        assertEquals(0.0, t.rollDeg, eps)
        assertTrue(t.isLevel())
    }

    @Test fun sidewaysRollIsDetectedAndNotLevel() {
        val a = Math.toRadians(10.0)
        val t = tiltFromGravity(Vec3(sin(a), cos(a), 0.0))!!
        assertEquals(0.0, t.pitchDeg, eps)
        assertEquals(10.0, t.rollDeg, eps)
        assertFalse(t.isLevel())
    }

    @Test fun forwardPitchIsDetectedAndNotLevel() {
        // Overlay convention: looking down is POSITIVE pitch (opposite to diagnostic tilt).
        val a = Math.toRadians(10.0)
        val t = tiltFromGravity(Vec3(0.0, cos(a), sin(a)))!!
        assertEquals(10.0, t.pitchDeg, eps)
        assertEquals(0.0, t.rollDeg, eps)
        assertFalse(t.isLevel())
    }

    @Test fun gravityIsNormalizedBeforeUse() {
        val t = tiltFromGravity(Vec3(3.0, 4.0, 0.0))!!   // norm 5 -> (0.6, 0.8, 0)
        assertEquals(0.0, t.pitchDeg, eps)
        assertEquals(Math.toDegrees(asin(0.6)), t.rollDeg, eps)
    }

    @Test fun zeroOrNonFiniteGravityIsUnavailableNotUpright() {
        assertNull(tiltFromGravity(Vec3(0.0, 0.0, 0.0)))
        assertNull(tiltFromGravity(Vec3(Double.NaN, 1.0, 0.0)))
        assertNull(tiltFromGravity(Vec3(0.0, Double.POSITIVE_INFINITY, 0.0)))
        assertNull(levelTiltFromDeviceDown(Vec3(0.0, 0.0, 0.0), 90))
    }

    @Test fun rollWithinToleranceStaysLevelButOutsideDoesNot() {
        assertTrue(TiltAngles(0.0, 0.8).isLevel(1.0))
        assertFalse(TiltAngles(0.0, 1.5).isLevel(1.0))
    }

    @Test fun nearFlatWithinToleranceIsLevel() {
        assertTrue(TiltAngles(89.5, 0.0).isLevel(1.0))
        assertFalse(TiltAngles(85.0, 0.0).isLevel(1.0))
    }

    @Test fun tiltedDiagonallyIsNotLevel() {
        assertFalse(TiltAngles(45.0, 45.0).isLevel(1.0))
    }

    // --- device axes -> view axes; each display rotation paired with its physical pose ---

    @Test fun levelPoseForEachRotationMapsToViewDown() {
        for (rot in listOf(0, 90, 180, 270)) {
            val v = viewDownFromDevice(screenTurnDown(rot.toDouble()), rot)
            assertEquals("x@$rot", 0.0, v.x, eps)
            assertEquals("y@$rot", 1.0, v.y, eps)
            assertEquals("z@$rot", 0.0, v.z, eps)
        }
    }

    @Test fun landscape90FacingWallIsLevel() {
        // Turned counter-clockwise: natural top left, +x up, so device down is -x.
        val t = levelTiltFromDeviceDown(Vec3(-1.0, 0.0, 0.0), rotationDegrees = 90)!!
        assertEquals(0.0, t.pitchDeg, eps)
        assertEquals(0.0, t.rollDeg, eps)
        assertTrue(t.isLevel())
    }

    @Test fun landscape270FacingWallIsLevel() {
        val t = levelTiltFromDeviceDown(Vec3(1.0, 0.0, 0.0), rotationDegrees = 270)!!
        assertEquals(0.0, t.pitchDeg, eps)
        assertEquals(0.0, t.rollDeg, eps)
        assertTrue(t.isLevel())
    }

    @Test fun landscapeForwardLeanShowsPitch() {
        // Landscape-90, screen top (+x) tipped 8 deg away from the user: down = (-cos8, 0, -sin8).
        val a = Math.toRadians(8.0)
        val t = levelTiltFromDeviceDown(Vec3(-cos(a), 0.0, -sin(a)), rotationDegrees = 90)!!
        assertEquals(8.0, t.pitchDeg, eps)
        assertEquals(0.0, t.rollDeg, eps)
        assertFalse(t.isLevel())
    }

    @Test fun landscapeSidewaysTiltShowsRoll() {
        // Landscape-90 turned 8 deg clockwise back (82 deg CCW): view-down leans view-right.
        val t = levelTiltFromDeviceDown(screenTurnDown(82.0), rotationDegrees = 90)!!
        assertEquals(0.0, t.pitchDeg, eps)
        assertEquals(8.0, t.rollDeg, 1e-9)
        assertFalse(t.isLevel())
    }

    @Test fun portraitClockwiseTurnIsPositiveRollInEveryRotation() {
        for (rot in listOf(0, 90, 180, 270)) {
            val t = levelTiltFromDeviceDown(screenTurnDown(rot - 5.0), rot)!!
            assertEquals("roll@$rot", 5.0, t.rollDeg, 1e-9)
            assertEquals("pitch@$rot", 0.0, t.pitchDeg, eps)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonQuarterRotationIsRejected() {
        viewDownFromDevice(Vec3(0.0, -1.0, 0.0), 45)
    }
}
