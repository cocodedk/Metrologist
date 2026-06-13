package com.cocode.measureapp.geometry

import kotlin.math.abs

/** Row-major 3x3 matrix. */
data class Mat3(
    val m00: Double, val m01: Double, val m02: Double,
    val m10: Double, val m11: Double, val m12: Double,
    val m20: Double, val m21: Double, val m22: Double,
) {
    operator fun times(v: Vec3) = Vec3(
        m00 * v.x + m01 * v.y + m02 * v.z,
        m10 * v.x + m11 * v.y + m12 * v.z,
        m20 * v.x + m21 * v.y + m22 * v.z,
    )

    operator fun times(o: Mat3) = Mat3(
        m00 * o.m00 + m01 * o.m10 + m02 * o.m20,
        m00 * o.m01 + m01 * o.m11 + m02 * o.m21,
        m00 * o.m02 + m01 * o.m12 + m02 * o.m22,
        m10 * o.m00 + m11 * o.m10 + m12 * o.m20,
        m10 * o.m01 + m11 * o.m11 + m12 * o.m21,
        m10 * o.m02 + m11 * o.m12 + m12 * o.m22,
        m20 * o.m00 + m21 * o.m10 + m22 * o.m20,
        m20 * o.m01 + m21 * o.m11 + m22 * o.m21,
        m20 * o.m02 + m21 * o.m12 + m22 * o.m22,
    )

    fun determinant(): Double =
        m00 * (m11 * m22 - m12 * m21) -
        m01 * (m10 * m22 - m12 * m20) +
        m02 * (m10 * m21 - m11 * m20)

    fun inverse(): Mat3 {
        val det = determinant()
        require(abs(det) > 1e-12) { "matrix not invertible" }
        val s = 1.0 / det
        return Mat3(
            (m11 * m22 - m12 * m21) * s, (m02 * m21 - m01 * m22) * s, (m01 * m12 - m02 * m11) * s,
            (m12 * m20 - m10 * m22) * s, (m00 * m22 - m02 * m20) * s, (m02 * m10 - m00 * m12) * s,
            (m10 * m21 - m11 * m20) * s, (m01 * m20 - m00 * m21) * s, (m00 * m11 - m01 * m10) * s,
        )
    }
}
