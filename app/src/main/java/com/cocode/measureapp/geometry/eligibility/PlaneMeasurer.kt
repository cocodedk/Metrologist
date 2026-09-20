package com.cocode.measureapp.geometry.eligibility

import com.cocode.measureapp.geometry.MeasurementResult
import com.cocode.measureapp.geometry.Measurements
import com.cocode.measureapp.geometry.PlaneFrame
import com.cocode.measureapp.geometry.ScaleResult
import com.cocode.measureapp.geometry.Vec2
import com.cocode.measureapp.geometry.Vec3
import com.cocode.measureapp.geometry.eligibility.EligibilityTolerances as T
import com.cocode.measureapp.stick.StickScale
import kotlin.math.abs

/**
 * Projects the object and stick marks onto a candidate plane, recovers the stick scale and
 * measures the object. Every denominator, depth side, scale and dimension is guarded first,
 * so an unusable plane becomes an explicit verdict instead of an exception or a NaN.
 */
internal object PlaneMeasurer {
    sealed interface Result {
        data class Measured(
            val measurement: MeasurementResult,
            val scale: ScaleResult,
            val minRayCos: Double,
            val referenceChecked: Boolean,
        ) : Result

        data class Unusable(val reason: IneligibleReason, val detail: String) : Result
    }

    fun measure(frame: PlaneFrame, input: EligibilityInput): Result {
        val k = input.intrinsics
        val points = input.corners + input.stick
        // Rays K⁻¹·(u, v, 1) without a matrix inverse: finite and nonzero for usable intrinsics.
        val rays = points.map { Vec3((it.x - k.cx) / k.fx, (it.y - k.cy) / k.fy, 1.0) }
        if (!rays.all { finite(it) } || !finite(frame.normal)) return numeric("rays or plane normal are not finite")
        val cosines = rays.map { frame.normal.dot(it) / it.norm() }
        val side = if (cosines[0] >= 0.0) 1.0 else -1.0
        if (cosines.any { it * side <= 0.0 }) {
            return unusable(IneligibleReason.PROJECTION_UNUSABLE, "marks lie on both sides of the plane or parallel to it")
        }
        val minCos = cosines.minOf { abs(it) }
        if (minCos < T.MIN_RAY_PLANE_COS) {
            return unusable(IneligibleReason.PROJECTION_UNUSABLE, "the view grazes the plane (min ray cosine $minCos)")
        }
        val projected = rays.map { r ->
            val x = r * (1.0 / frame.normal.dot(r))
            Vec2(frame.e1.dot(x), frame.e2.dot(x))
        }
        if (!projected.all { it.x.isFinite() && it.y.isFinite() }) return numeric("projected marks are not finite")
        val cornerMetric = projected.subList(0, 4)
        val stickMetric = projected.subList(4, 8)
        return try {
            val scale = StickScale.solve(stickMetric, input.profile)
            if (!(scale.scale.isFinite() && scale.scale > 0.0 && scale.agreement.isFinite())) {
                return numeric("stick scale is not finite and positive")
            }
            val referenceChecked = input.profile.width > 0.0
            if (referenceChecked && scale.agreement > T.MAX_STICK_DISAGREEMENT) {
                return unusable(
                    IneligibleReason.REFERENCE_INCONSISTENT,
                    "the stick's length and width disagree by ${pct(scale.agreement)} on this plane " +
                        "(limit ${pct(T.MAX_STICK_DISAGREEMENT)})",
                )
            }
            val m = Measurements.compute(cornerMetric.map { it * scale.scale })
            if (!valid(m)) numeric("measurement is not finite and positive") else Result.Measured(m, scale, minCos, referenceChecked)
        } catch (e: IllegalArgumentException) {
            numeric("degenerate projection: ${e.message}")
        }
    }

    private fun valid(m: MeasurementResult): Boolean =
        listOf(m.width, m.height, m.area, m.diagonal).all { it.isFinite() && it > 0.0 } &&
            m.cornerAngles.size == 4 && m.cornerAngles.all { it.isFinite() }

    private fun finite(v: Vec3) = v.x.isFinite() && v.y.isFinite() && v.z.isFinite()

    private fun pct(x: Double) = "%.1f%%".format(java.util.Locale.ROOT, x * 100.0)

    private fun numeric(detail: String) = unusable(IneligibleReason.NUMERICAL, detail)

    private fun unusable(reason: IneligibleReason, detail: String) = Result.Unusable(reason, detail)
}
