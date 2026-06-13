package com.cocode.measureapp.geometry

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.sqrt

class ProjectionTest {
    private val k = CameraIntrinsics(fx = 100.0, fy = 100.0, cx = 50.0, cy = 50.0)

    @Test fun frontoParallelFrameMapsPixelToMetricPoint() {
        val frame = PlaneFrame(
            e1 = Vec3(1.0, 0.0, 0.0),
            e2 = Vec3(0.0, 1.0, 0.0),
            normal = Vec3(0.0, 0.0, 1.0),
        )
        // Pixel (150,50): ray=(1,0,1), denom=1, X=(1,0,1) -> (e1.X, e2.X)=(1,0).
        // Pixel (50,150): ray=(0,1,1), denom=1, X=(0,1,1) -> (0,1).
        val result = projectToPlane(listOf(Vec2(150.0, 50.0), Vec2(50.0, 150.0)), k, frame)
        assertEquals(2, result.size)
        assertEquals(1.0, result[0].x, 1e-9)
        assertEquals(0.0, result[0].y, 1e-9)
        assertEquals(0.0, result[1].x, 1e-9)
        assertEquals(1.0, result[1].y, 1e-9)
    }

    @Test fun obliqueFrameMapsPixelToMetricPoint() {
        val s = sqrt(0.5) // sin 45
        val c = sqrt(0.5) // cos 45
        val frame = PlaneFrame(
            e1 = Vec3(1.0, 0.0, 0.0),
            e2 = Vec3(0.0, c, -s),
            normal = Vec3(0.0, s, c),
        )
        // Pixel (50,50): ray=(0,0,1), denom=normal.z=c, X=(0,0,1/c).
        // e1.X = 0 ; e2.X = -s*(1/c) = -tan45 = -1.
        val result = projectToPlane(listOf(Vec2(50.0, 50.0)), k, frame)
        assertEquals(1, result.size)
        assertEquals(0.0, result[0].x, 1e-9)
        assertEquals(-1.0, result[0].y, 1e-9)
    }

    @Test(expected = IllegalArgumentException::class)
    fun denomGuardThrowsWhenRayParallelToPlane() {
        // Plane normal along x; ray for pixel (50,50) is (0,0,1) -> denom=0.
        val frame = PlaneFrame(
            e1 = Vec3(0.0, 1.0, 0.0),
            e2 = Vec3(0.0, 0.0, 1.0),
            normal = Vec3(1.0, 0.0, 0.0),
        )
        projectToPlane(listOf(Vec2(50.0, 50.0)), k, frame)
    }
}
