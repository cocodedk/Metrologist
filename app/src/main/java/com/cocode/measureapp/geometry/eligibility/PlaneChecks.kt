package com.cocode.measureapp.geometry.eligibility

import com.cocode.measureapp.geometry.SurfaceOrientation
import com.cocode.measureapp.geometry.Vec3
import com.cocode.measureapp.geometry.frames.AlignedGravity
import com.cocode.measureapp.geometry.frames.CalibrationProvenance
import com.cocode.measureapp.geometry.frames.CalibrationStatus
import com.cocode.measureapp.geometry.eligibility.EligibilityTolerances as T
import kotlin.math.abs
import kotlin.math.min

/** Result of checking a plane normal against the selected surface. */
sealed interface SurfaceVerdict {
    data class Checked(val consistency: SurfaceConsistency) : SurfaceVerdict
    data class Contradiction(val absNormalDotDown: Double, val detail: String) : SurfaceVerdict
}

/** Shared gravity, surface and confidence rules used by both eligibility assessors. */
object PlaneChecks {
    /** Unit physical-down vector, or null when gravity is absent, non-finite or not unit-like. */
    fun unitDown(gravity: AlignedGravity): Vec3? {
        val g = (gravity as? AlignedGravity.Available)?.down ?: return null
        if (!(g.x.isFinite() && g.y.isFinite() && g.z.isFinite())) return null
        val n = g.norm()
        return if (n >= T.MIN_GRAVITY_NORM && n <= T.MAX_GRAVITY_NORM) g * (1.0 / n) else null
    }

    /** Human-readable reason gravity cannot be used. */
    fun gravityProblem(gravity: AlignedGravity): String = when (gravity) {
        is AlignedGravity.Unavailable -> "no tilt reading aligned with this photo (${gravity.reason})"
        is AlignedGravity.Available -> "tilt reading ${gravity.down} is not a valid unit direction"
    }

    /**
     * Wall requires `|n·g| <= sin 5°`, floor/table `|n·g| >= cos 5°`. Without usable gravity
     * the result is [SurfaceConsistency.UNVERIFIED_NO_GRAVITY], never a fabricated match.
     */
    fun surface(normal: Vec3, gravity: AlignedGravity, orientation: SurfaceOrientation): SurfaceVerdict {
        val g = unitDown(gravity) ?: return SurfaceVerdict.Checked(SurfaceConsistency.UNVERIFIED_NO_GRAVITY)
        val d = abs(normal.normalized().dot(g))
        return when (orientation) {
            SurfaceOrientation.VERTICAL -> if (d <= T.WALL_MAX_ABS_NORMAL_DOT_DOWN) {
                SurfaceVerdict.Checked(SurfaceConsistency.CONSISTENT)
            } else {
                SurfaceVerdict.Contradiction(d, "the marked plane is ${degrees(d, wall = true)} deg from vertical, not a wall")
            }
            SurfaceOrientation.HORIZONTAL -> if (d >= T.FLOOR_MIN_ABS_NORMAL_DOT_DOWN) {
                SurfaceVerdict.Checked(SurfaceConsistency.CONSISTENT)
            } else {
                SurfaceVerdict.Contradiction(d, "the marked plane is ${degrees(d, wall = false)} deg from level, not a floor/table")
            }
        }
    }

    /** Linear factor: 1 at zero, [T.FACTOR_AT_LIMIT] at [limit]; callers reject beyond it. */
    fun factor(value: Double, limit: Double): Double =
        (1.0 - (1.0 - T.FACTOR_AT_LIMIT) * (value / limit)).coerceIn(T.FACTOR_AT_LIMIT, 1.0)

    /** Factor for the smallest ray/normal cosine: [T.FACTOR_AT_LIMIT] at grazing, 1 head-on. */
    fun rayFactor(minRayCos: Double): Double {
        val span = (minRayCos - T.MIN_RAY_PLANE_COS) / (1.0 - T.MIN_RAY_PLANE_COS)
        return (T.FACTOR_AT_LIMIT + (1.0 - T.FACTOR_AT_LIMIT) * span).coerceIn(T.FACTOR_AT_LIMIT, 1.0)
    }

    /**
     * Applies every honest upper bound to [raw]: approximate or unavailable calibration,
     * unverified surface, unchecked reference and an assumed wall azimuth. Returns the bounded
     * confidence in `(0, 1]` and the names of the caps that apply.
     */
    fun capped(
        raw: Double,
        calibration: CalibrationProvenance,
        surface: SurfaceConsistency,
        referenceChecked: Boolean,
        assumption: PlaneAssumption,
    ): Pair<Double, List<String>> {
        var c = raw.coerceIn(T.FACTOR_AT_LIMIT * T.FACTOR_AT_LIMIT, 1.0)
        val caps = mutableListOf<String>()
        fun cap(limit: Double, name: String) {
            caps += name
            c = min(c, limit)
        }
        when (calibration.status) {
            CalibrationStatus.CALIBRATED -> Unit
            CalibrationStatus.APPROXIMATE -> cap(T.APPROXIMATE_CALIBRATION_CAP, "approximate calibration")
            CalibrationStatus.UNAVAILABLE -> cap(T.UNAVAILABLE_CALIBRATION_CAP, "calibration unavailable")
        }
        if (surface == SurfaceConsistency.UNVERIFIED_NO_GRAVITY) cap(T.UNVERIFIED_SURFACE_CAP, "surface unverified")
        if (!referenceChecked) cap(T.UNCHECKED_REFERENCE_CAP, "reference shape unchecked")
        if (assumption == PlaneAssumption.WALL_FACES_CAMERA) cap(T.ASSUMED_AZIMUTH_CAP, "wall azimuth assumed")
        return Pair(c, caps)
    }

    private fun degrees(absDot: Double, wall: Boolean): String {
        val rad = if (wall) kotlin.math.asin(absDot.coerceIn(0.0, 1.0)) else kotlin.math.acos(absDot.coerceIn(0.0, 1.0))
        return "%.1f".format(java.util.Locale.ROOT, Math.toDegrees(rad))
    }
}
