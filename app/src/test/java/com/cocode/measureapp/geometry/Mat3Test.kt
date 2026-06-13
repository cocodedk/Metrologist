package com.cocode.measureapp.geometry

import org.junit.Assert.assertEquals
import org.junit.Test

class Mat3Test {
    @Test fun inverseUndoesMultiplication() {
        val m = Mat3(2.0, 0.0, 1.0, 1.0, 1.0, 0.0, 0.0, 1.0, 1.0)
        val v = Vec3(1.0, 2.0, 3.0)
        val back = m.inverse() * (m * v)
        assertEquals(1.0, back.x, 1e-9)
        assertEquals(2.0, back.y, 1e-9)
        assertEquals(3.0, back.z, 1e-9)
    }

    @Test(expected = IllegalArgumentException::class)
    fun inverseSingularMatrixThrows() {
        Mat3(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0).inverse()
    }

    @Test fun determinantOfKnownMatrix() {
        val m = Mat3(2.0, 0.0, 1.0, 1.0, 1.0, 0.0, 0.0, 1.0, 1.0)
        assertEquals(3.0, m.determinant(), 1e-9)
    }

    @Test fun timesIdentityReturnsSelf() {
        val m = Mat3(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 10.0)
        val id = Mat3(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)
        val r = m * id
        assertEquals(m, r)
    }

    @Test fun timesKnownMatrixProduct() {
        val a = Mat3(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0)
        val b = Mat3(1.0, 0.0, 1.0, 0.0, 1.0, 1.0, 1.0, 0.0, 0.0)
        val r = a * b
        val expected = Mat3(4.0, 2.0, 3.0, 10.0, 5.0, 9.0, 16.0, 8.0, 15.0)
        assertEquals(expected, r)
    }
}
