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
}
