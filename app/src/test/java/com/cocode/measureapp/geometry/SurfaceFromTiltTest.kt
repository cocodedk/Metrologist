package com.cocode.measureapp.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The camera's pitch already says what it is pointed at, so the user should not be asked.
 * Looking straight ahead is a wall; looking down (or up) is a floor, table or ceiling. The
 * band between the two is genuinely ambiguous and must stay a question rather than a guess.
 */
class SurfaceFromTiltTest {

    @Test
    fun `level camera means a wall`() {
        assertEquals(SurfaceOrientation.VERTICAL, surfaceFromTilt(TiltAngles(0.0, 0.0)))
        assertEquals(SurfaceOrientation.VERTICAL, surfaceFromTilt(TiltAngles(12.0, 5.0)))
        assertEquals(SurfaceOrientation.VERTICAL, surfaceFromTilt(TiltAngles(-20.0, 0.0)))
    }

    @Test
    fun `camera pointed down or up means a horizontal surface`() {
        assertEquals(SurfaceOrientation.HORIZONTAL, surfaceFromTilt(TiltAngles(90.0, 0.0)))
        assertEquals(SurfaceOrientation.HORIZONTAL, surfaceFromTilt(TiltAngles(68.0, 3.0)))
        assertEquals(SurfaceOrientation.HORIZONTAL, surfaceFromTilt(TiltAngles(-85.0, 0.0)))
    }

    @Test
    fun `the middle band stays a question`() {
        assertNull(surfaceFromTilt(TiltAngles(45.0, 0.0)))
        assertNull(surfaceFromTilt(TiltAngles(35.0, 0.0)))
        assertNull(surfaceFromTilt(TiltAngles(-50.0, 0.0)))
    }

    @Test
    fun `no reading means no answer`() {
        assertNull(surfaceFromTilt(null))
    }
}
