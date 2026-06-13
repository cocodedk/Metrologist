package com.cocode.measureapp.stick

import com.cocode.measureapp.geometry.Vec2
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.sqrt

class AxisTest {

    private val horizontal = Axis(Vec2(0.0, 0.0), Vec2(1.0, 0.0))
    private val diagonal   = Axis(Vec2(1.0, 1.0), Vec2(1.0 / sqrt(2.0), 1.0 / sqrt(2.0)))

    // ---- project ----------------------------------------------------------------

    @Test fun `project returns zero for origin point`() {
        assertEquals(0.0, horizontal.project(Vec2(0.0, 0.0)), 1e-12)
    }

    @Test fun `project returns signed distance along axis`() {
        assertEquals(3.0, horizontal.project(Vec2(3.0, 7.0)), 1e-12)
    }

    @Test fun `project handles diagonal axis`() {
        // point (3,3): relative to origin (1,1) = (2,2); dot with (1/√2, 1/√2) = 2√2
        val expected = 2.0 * sqrt(2.0)
        assertEquals(expected, diagonal.project(Vec2(3.0, 3.0)), 1e-9)
    }

    @Test fun `project negative t for point behind origin`() {
        assertEquals(-2.0, horizontal.project(Vec2(-2.0, 5.0)), 1e-12)
    }

    // ---- pointAt ----------------------------------------------------------------

    @Test fun `pointAt zero returns origin`() {
        val p = horizontal.pointAt(0.0)
        assertEquals(0.0, p.x, 1e-12)
        assertEquals(0.0, p.y, 1e-12)
    }

    @Test fun `pointAt positive t moves along direction`() {
        val p = horizontal.pointAt(5.0)
        assertEquals(5.0, p.x, 1e-12)
        assertEquals(0.0, p.y, 1e-12)
    }

    @Test fun `pointAt negative t moves opposite`() {
        val p = horizontal.pointAt(-3.0)
        assertEquals(-3.0, p.x, 1e-12)
        assertEquals(0.0, p.y, 1e-12)
    }

    @Test fun `pointAt on diagonal axis`() {
        val t = sqrt(2.0)
        val p = diagonal.pointAt(t)  // origin + (1,1)/√2 * √2 = (1,1)+(1,1) = (2,2)
        assertEquals(2.0, p.x, 1e-9)
        assertEquals(2.0, p.y, 1e-9)
    }

    // ---- perpendicularDistance --------------------------------------------------

    @Test fun `perpendicularDistance zero for point on axis`() {
        assertEquals(0.0, horizontal.perpendicularDistance(Vec2(4.0, 0.0)), 1e-12)
    }

    @Test fun `perpendicularDistance equals lateral offset`() {
        assertEquals(3.0, horizontal.perpendicularDistance(Vec2(10.0, 3.0)), 1e-12)
    }

    @Test fun `perpendicularDistance is symmetric for positive and negative offsets`() {
        val pos = horizontal.perpendicularDistance(Vec2(5.0,  2.0))
        val neg = horizontal.perpendicularDistance(Vec2(5.0, -2.0))
        assertEquals(2.0, pos, 1e-12)
        assertEquals(2.0, neg, 1e-12)
    }

    @Test fun `perpendicularDistance for diagonal axis`() {
        // Point directly "above" the axis (perpendicular): (2,0) relative to origin (1,1)
        // Along-axis component: (1,-1)·(1,1)/√2 = 0 → full distance = |(1,-1)| = √2
        val p = Vec2(2.0, 0.0)
        assertEquals(sqrt(2.0), diagonal.perpendicularDistance(p), 1e-9)
    }

    // ---- data-class accessors (coverage) ----------------------------------------

    @Test fun `data class getters return constructed values`() {
        val origin = Vec2(3.0, 4.0)
        val dir    = Vec2(1.0, 0.0)
        val axis   = Axis(origin, dir)
        assertEquals(origin, axis.origin)
        assertEquals(dir,    axis.direction)
    }
}
