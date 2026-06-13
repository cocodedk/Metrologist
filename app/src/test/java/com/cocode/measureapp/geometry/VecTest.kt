package com.cocode.measureapp.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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

    @Test fun normalizeThrowsOnZeroVector() {
        assertThrows(IllegalArgumentException::class.java) {
            Vec3(0.0, 0.0, 0.0).normalized()
        }
    }

    @Test fun vec3DotProduct() {
        assertEquals(32.0, Vec3(1.0, 2.0, 3.0).dot(Vec3(4.0, 5.0, 6.0)), 1e-9)
    }

    @Test fun vec2DotProduct() {
        assertEquals(7.0, Vec2(1.0, 2.0).dot(Vec2(3.0, 2.0)), 1e-9)
    }

    @Test fun vec2NormalizedHasUnitLength() {
        val r = Vec2(3.0, 4.0).normalized()
        assertEquals(1.0, r.norm(), 1e-9)
        assertEquals(0.6, r.x, 1e-9)
        assertEquals(0.8, r.y, 1e-9)
    }

    @Test fun vec2NormalizeThrowsOnZeroVector() {
        assertThrows(IllegalArgumentException::class.java) {
            Vec2(0.0, 0.0).normalized()
        }
    }

    @Test fun vec2ScalarMultiply() {
        val r = Vec2(1.5, -2.0) * 3.0
        assertEquals(4.5, r.x, 1e-9)
        assertEquals(-6.0, r.y, 1e-9)
    }

    @Test fun vec2PlusAddsComponentwise() {
        assertEquals(Vec2(4.0, 6.0), Vec2(1.0, 2.0) + Vec2(3.0, 4.0))
    }

    @Test fun vec3MinusSubtractsComponentwise() {
        assertEquals(Vec3(-3.0, -3.0, -3.0), Vec3(1.0, 2.0, 3.0) - Vec3(4.0, 5.0, 6.0))
    }
}
