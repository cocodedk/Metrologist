package com.cocode.measureapp.geometry

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Directly reads every auto-generated data-class property getter so that JaCoCo
 * counts them as covered (METHOD + INSTRUCTION + LINE).
 *
 * Without this, Mat3.m00..m22 (9 getters) and CameraIntrinsics.fx/fy/cx/cy (4 getters)
 * appear as missed even when the objects are used — because usage via named constructors
 * and destructuring does NOT invoke the individual property getters.
 */
class DataClassAccessorsTest {

    @Test fun mat3AllGettersReturnConstructedValues() {
        val m = Mat3(
            m00 = 1.0, m01 = 2.0, m02 = 3.0,
            m10 = 4.0, m11 = 5.0, m12 = 6.0,
            m20 = 7.0, m21 = 8.0, m22 = 9.0,
        )
        assertEquals(1.0, m.m00, 0.0)
        assertEquals(2.0, m.m01, 0.0)
        assertEquals(3.0, m.m02, 0.0)
        assertEquals(4.0, m.m10, 0.0)
        assertEquals(5.0, m.m11, 0.0)
        assertEquals(6.0, m.m12, 0.0)
        assertEquals(7.0, m.m20, 0.0)
        assertEquals(8.0, m.m21, 0.0)
        assertEquals(9.0, m.m22, 0.0)
    }

    @Test fun cameraIntrinsicsAllGettersReturnConstructedValues() {
        val k = CameraIntrinsics(fx = 800.0, fy = 600.0, cx = 320.0, cy = 240.0)
        assertEquals(800.0, k.fx, 0.0)
        assertEquals(600.0, k.fy, 0.0)
        assertEquals(320.0, k.cx, 0.0)
        assertEquals(240.0, k.cy, 0.0)
    }
}
