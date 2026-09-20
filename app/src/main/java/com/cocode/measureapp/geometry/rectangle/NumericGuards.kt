package com.cocode.measureapp.geometry.rectangle

import com.cocode.measureapp.geometry.CameraIntrinsics
import com.cocode.measureapp.geometry.Mat3
import com.cocode.measureapp.geometry.Tolerances
import com.cocode.measureapp.geometry.Vec3
import kotlin.math.abs
import kotlin.math.max

/**
 * Preflight checks that turn numerically unusable, yet finite, projection inputs into explicit
 * rejections instead of letting [Mat3.inverse] or `normalized()` throw, or overflow leak through.
 */
internal object NumericGuards {
    /**
     * `K⁻¹`, or null when `K` is not safely invertible: its determinant `fx·fy` must be finite and
     * above [Tolerances.NORM_EPS] (the [Mat3.inverse] precondition), and every entry of the
     * inverse must be finite. Overflowing focal lengths otherwise yield a zero/NaN inverse.
     */
    fun inverseOrNull(k: CameraIntrinsics): Mat3? {
        val m = k.matrix()
        val det = m.determinant()
        if (!(det.isFinite() && abs(det) > Tolerances.NORM_EPS)) return null
        val inv = m.inverse()
        val entries = listOf(
            inv.m00, inv.m01, inv.m02, inv.m10, inv.m11, inv.m12, inv.m20, inv.m21, inv.m22,
        )
        return if (entries.all { it.isFinite() }) inv else null
    }

    fun isFinite(v: Vec3): Boolean = v.x.isFinite() && v.y.isFinite() && v.z.isFinite()

    /** Overflow-safe Euclidean norm: scales by the largest component first. */
    fun stableNorm(v: Vec3): Double {
        val m = max(abs(v.x), max(abs(v.y), abs(v.z)))
        if (m == 0.0 || !m.isFinite()) return m
        val s = v * (1.0 / m)
        return m * s.norm()
    }

    /** Unit vector along [v], or null when [v] is non-finite or numerically zero. */
    fun unitOrNull(v: Vec3): Vec3? {
        if (!isFinite(v)) return null
        val m = max(abs(v.x), max(abs(v.y), abs(v.z)))
        if (m == 0.0) return null
        val s = v * (1.0 / m)
        val n = s.norm()
        return if (n > Tolerances.NORM_EPS && isFinite(s)) s * (1.0 / n) else null
    }
}
