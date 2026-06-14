package com.cocode.measureapp.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin

class TiltTest {

    private val eps = 1e-9

    @Test fun uprightIsZeroAndLevel() {
        val t = tiltFromGravity(Vec3(0.0, 1.0, 0.0))
        assertEquals(0.0, t.pitchDeg, eps)
        assertEquals(0.0, t.rollDeg, eps)
        assertTrue(t.isLevel())
    }

    @Test fun flatToFloorIsNinetyPitchAndLevel() {
        val t = tiltFromGravity(Vec3(0.0, 0.0, 1.0))
        assertEquals(90.0, t.pitchDeg, eps)
        assertEquals(0.0, t.rollDeg, eps)
        assertTrue(t.isLevel())
    }

    @Test fun flatToCeilingIsMinusNinetyAndLevel() {
        val t = tiltFromGravity(Vec3(0.0, 0.0, -1.0))
        assertEquals(-90.0, t.pitchDeg, eps)
        assertEquals(0.0, t.rollDeg, eps)
        assertTrue(t.isLevel())
    }

    @Test fun sidewaysRollIsDetectedAndNotLevel() {
        val a = Math.toRadians(10.0)
        val t = tiltFromGravity(Vec3(sin(a), cos(a), 0.0))
        assertEquals(0.0, t.pitchDeg, eps)
        assertEquals(10.0, t.rollDeg, eps)
        assertFalse(t.isLevel())
    }

    @Test fun forwardPitchIsDetectedAndNotLevel() {
        val a = Math.toRadians(10.0)
        val t = tiltFromGravity(Vec3(0.0, cos(a), sin(a)))
        assertEquals(10.0, t.pitchDeg, eps)
        assertEquals(0.0, t.rollDeg, eps)
        assertFalse(t.isLevel())
    }

    @Test fun gravityIsNormalizedBeforeUse() {
        val t = tiltFromGravity(Vec3(3.0, 4.0, 0.0))   // norm 5 -> (0.6, 0.8, 0)
        assertEquals(0.0, t.pitchDeg, eps)
        assertEquals(Math.toDegrees(asin(0.6)), t.rollDeg, eps)
    }

    @Test fun zeroGravityFallsBackToUpright() {
        val t = tiltFromGravity(Vec3(0.0, 0.0, 0.0))
        assertEquals(0.0, t.pitchDeg, eps)
        assertEquals(0.0, t.rollDeg, eps)
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
}
