package com.cocode.measureapp.geometry

import org.junit.Assert.assertEquals
import org.junit.Test

class CameraIntrinsicsTest {
    @Test fun inverseMatrixMapsPrincipalPointToOpticalAxis() {
        val k = CameraIntrinsics(fx = 1000.0, fy = 1000.0, cx = 640.0, cy = 360.0)
        val ray = k.inverseMatrix() * Vec3(640.0, 360.0, 1.0)
        assertEquals(0.0, ray.x, 1e-9)
        assertEquals(0.0, ray.y, 1e-9)
        assertEquals(1.0, ray.z, 1e-9)
    }

    @Test fun inverseMatrixNormalizesPixelOffsetByFocalLength() {
        val k = CameraIntrinsics(fx = 800.0, fy = 400.0, cx = 320.0, cy = 240.0)
        val ray = k.inverseMatrix() * Vec3(320.0 + 800.0, 240.0 - 400.0, 1.0)
        assertEquals(1.0, ray.x, 1e-9)
        assertEquals(-1.0, ray.y, 1e-9)
        assertEquals(1.0, ray.z, 1e-9)
    }

    @Test fun matrixMapsOpticalAxisRayToPrincipalPoint() {
        val k = CameraIntrinsics(fx = 1000.0, fy = 1000.0, cx = 640.0, cy = 360.0)
        val pixel = k.matrix() * Vec3(0.0, 0.0, 1.0)
        assertEquals(640.0, pixel.x, 1e-9)
        assertEquals(360.0, pixel.y, 1e-9)
        assertEquals(1.0, pixel.z, 1e-9)
    }
}
