package com.cocode.measureapp.geometry

import org.junit.Assert.assertEquals
import org.junit.Test

class VecTest {
    @Test fun crossOfUnitXAndUnitYIsUnitZ() {
        val r = Vec3(1.0, 0.0, 0.0).cross(Vec3(0.0, 1.0, 0.0))
        assertEquals(0.0, r.x, 1e-9)
        assertEquals(0.0, r.y, 1e-9)
        assertEquals(1.0, r.z, 1e-9)
    }

    @Test fun normalizedHasUnitLengthAndDirection() {
        val r = Vec3(3.0, 0.0, 4.0).normalized()
        assertEquals(1.0, r.norm(), 1e-9)
        assertEquals(0.6, r.x, 1e-9)
        assertEquals(0.8, r.z, 1e-9)
    }

    @Test fun vec2DistanceIsEuclidean() {
        assertEquals(5.0, Vec2(0.0, 0.0).distanceTo(Vec2(3.0, 4.0)), 1e-9)
    }
}
