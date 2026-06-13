package com.cocode.measureapp.stick

import com.cocode.measureapp.geometry.Vec2
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class PrincipalAxisTest {

    // ---- error guards -----------------------------------------------------------

    @Test fun `fewer than 2 points throws`() {
        assertThrows(IllegalArgumentException::class.java) { PrincipalAxis.fit(emptyList()) }
    }

    @Test fun `single point throws`() {
        assertThrows(IllegalArgumentException::class.java) { PrincipalAxis.fit(listOf(Vec2(1.0, 2.0))) }
    }

    // ---- y = 2x line -----------------------------------------------------------
    //  Points on y = 2x: direction proportional to (1, 2).
    //  Normalised: (1/√5, 2/√5).  Cross product of unit output with (1,2)/||(1,2)|| must be 0.

    @Test fun `y equals 2x cloud gives unit direction parallel to (1,2)`() {
        val pts = listOf(
            Vec2(0.0, 0.0), Vec2(1.0, 2.0), Vec2(2.0, 4.0),
            Vec2(3.0, 6.0), Vec2(-1.0, -2.0)
        )
        val axis = PrincipalAxis.fit(pts)
        val d = axis.direction

        // Must be a unit vector.
        assertEquals(1.0, d.norm(), 1e-9)

        // Cross product with (1,2)/√5 must be zero (parallel or anti-parallel).
        val refX = 1.0 / sqrt(5.0)
        val refY = 2.0 / sqrt(5.0)
        val cross = abs(d.x * refY - d.y * refX)
        assertEquals(0.0, cross, 1e-9)
    }

    @Test fun `centroid lies on the axis`() {
        val pts = listOf(Vec2(0.0, 0.0), Vec2(2.0, 4.0), Vec2(4.0, 8.0))
        val axis = PrincipalAxis.fit(pts)
        // Centroid = (2, 4); perpendicular distance from centroid to axis must be 0.
        assertEquals(0.0, axis.perpendicularDistance(axis.origin), 1e-9)
    }

    // ---- axis-aligned clouds (Sxy ≈ 0) -----------------------------------------

    @Test fun `horizontal cloud (Sxx greater than Syy) gives direction (1,0)`() {
        // Spread along x, constant y → Sxy=0, Sxx >> Syy.
        val pts = listOf(Vec2(-5.0, 0.0), Vec2(0.0, 0.0), Vec2(5.0, 0.0))
        val axis = PrincipalAxis.fit(pts)
        assertEquals(1.0, axis.direction.x, 1e-9)
        assertEquals(0.0, axis.direction.y, 1e-9)
    }

    @Test fun `vertical cloud (Syy greater than Sxx) gives direction (0,1)`() {
        // Spread along y, constant x → Sxy=0, Syy >> Sxx.
        val pts = listOf(Vec2(0.0, -5.0), Vec2(0.0, 0.0), Vec2(0.0, 5.0))
        val axis = PrincipalAxis.fit(pts)
        assertEquals(0.0, axis.direction.x, 1e-9)
        assertEquals(1.0, axis.direction.y, 1e-9)
    }

    @Test fun `equal variance axis-aligned (Sxy=0, Sxx equals Syy) takes horizontal branch`() {
        // A square arrangement: Sxx == Syy, Sxy == 0 → takes the Sxx >= Syy branch → (1,0).
        val pts = listOf(Vec2(-1.0, -1.0), Vec2(-1.0, 1.0), Vec2(1.0, -1.0), Vec2(1.0, 1.0))
        val axis = PrincipalAxis.fit(pts)
        assertEquals(1.0, axis.direction.x, 1e-9)
        assertEquals(0.0, axis.direction.y, 1e-9)
    }

    // ---- minimum case -----------------------------------------------------------

    @Test fun `two points give direction equal to connecting vector normalised`() {
        val a = Vec2(0.0, 0.0)
        val b = Vec2(3.0, 4.0)
        val axis = PrincipalAxis.fit(listOf(a, b))
        assertEquals(1.0, axis.direction.norm(), 1e-9)
        // direction should be (3/5, 4/5).
        assertEquals(0.6, axis.direction.x, 1e-9)
        assertEquals(0.8, axis.direction.y, 1e-9)
    }
}
