package com.cocode.measureapp.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProjectiveTest {
    @Test fun intersectionOfPerpendicularLines() {
        val vertical = Projective.lineThrough(Vec2(2.0, 0.0), Vec2(2.0, 5.0))
        val horizontal = Projective.lineThrough(Vec2(0.0, 3.0), Vec2(5.0, 3.0))
        val p = Projective.intersection(vertical, horizontal)!!
        assertEquals(2.0, p.x, 1e-9)
        assertEquals(3.0, p.y, 1e-9)
    }

    @Test fun parallelLinesHaveNoFiniteIntersection() {
        val l1 = Projective.lineThrough(Vec2(0.0, 0.0), Vec2(5.0, 0.0))
        val l2 = Projective.lineThrough(Vec2(0.0, 1.0), Vec2(5.0, 1.0))
        assertNull(Projective.intersection(l1, l2))
    }

    @Test fun vanishingPointOfConvergingEdges() {
        val vp = Projective.vanishingPoint(
            Vec2(0.0, 0.0), Vec2(10.0, 1.0),
            Vec2(0.0, 2.0), Vec2(10.0, 1.0),
        )!!
        assertEquals(10.0, vp.x, 1e-9)
        assertEquals(1.0, vp.y, 1e-9)
    }

    @Test fun vanishingPointOfParallelEdgesIsNull() {
        val vp = Projective.vanishingPoint(
            Vec2(0.0, 0.0), Vec2(5.0, 0.0),
            Vec2(0.0, 2.0), Vec2(5.0, 2.0),
        )
        assertNull(vp)
    }
}
