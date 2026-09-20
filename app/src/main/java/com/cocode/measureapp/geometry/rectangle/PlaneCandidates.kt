package com.cocode.measureapp.geometry.rectangle

import com.cocode.measureapp.geometry.CameraIntrinsics
import com.cocode.measureapp.geometry.Mat3
import com.cocode.measureapp.geometry.Vec2
import com.cocode.measureapp.geometry.frames.CalibrationProvenance
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.max

/**
 * Builds a [RectangleCandidate] from validated object corners `[TL, TR, BR, BL]` and the
 * intrinsics of the SAME aligned image frame. Vanishing points at infinity are supported;
 * rejection happens only for invalid input, numeric degeneracy (including finite inputs whose
 * projection overflows or whose `K` is not safely invertible), grazing views, or marking
 * noise that makes the rectified shape unstable.
 */
object PlaneCandidates {
    fun solve(
        corners: List<Vec2>,
        k: CameraIntrinsics,
        provenance: CalibrationProvenance,
        sceneId: String?,
    ): RectangleCandidate {
        fun reject(reason: RectangleRejection, detail: String) =
            RectangleCandidate.Rejected(reason, detail, provenance, sceneId)

        if (corners.size != 4) return reject(RectangleRejection.INVALID_CORNER_COUNT, "expected 4 corners, got ${corners.size}")
        if (!corners.all { it.x.isFinite() && it.y.isFinite() }) {
            return reject(RectangleRejection.NON_FINITE_CORNERS, "corner coordinates must be finite")
        }
        if (!k.isUsable()) return reject(RectangleRejection.INVALID_INTRINSICS, "intrinsics not finite/positive: $k")
        val kInv = NumericGuards.inverseOrNull(k)
            ?: return reject(RectangleRejection.NUMERICAL_FAILURE, "intrinsics not safely invertible: $k")

        val plane = when (val r = VanishingPlane.solve(corners, kInv)) {
            is VanishingPlane.Result.Degenerate -> return reject(r.reason, r.detail)
            is VanishingPlane.Result.Plane -> r
        }
        if (plane.minRayCos < RectangleTolerances.MIN_RAY_PLANE_COS) {
            return reject(RectangleRejection.GRAZING_VIEW, "min ray/normal cosine ${plane.minRayCos}")
        }
        val sensitivity = halfPixelSensitivity(corners, kInv, plane)
        if (!(sensitivity.first <= RectangleTolerances.MAX_HALF_PIXEL_ASPECT_SENSITIVITY)) {
            return reject(RectangleRejection.ILL_CONDITIONED, "half-pixel aspect sensitivity ${sensitivity.first}")
        }
        val evidence = RectangleEvidence(
            widthVanishing = VanishingDirection.classify(plane.widthVp),
            heightVanishing = VanishingDirection.classify(plane.heightVp),
            orthogonalityResidual = plane.orthogonalityResidual,
            minRayPlaneCosine = plane.minRayCos,
            halfPixelAspectSensitivity = sensitivity.first,
            halfPixelNormalShiftRad = sensitivity.second,
        )
        return RectangleCandidate.Accepted(plane.frame, evidence, provenance, sceneId)
    }

    /**
     * Moves each of the 8 corner coordinates by ±[RectangleTolerances.MARK_UNCERTAINTY_PX] and
     * re-solves. Returns (sum over coordinates of the worst relative aspect change, largest
     * normal rotation in radians). A perturbation that collapses the plane gives infinity.
     */
    private fun halfPixelSensitivity(
        corners: List<Vec2>,
        kInv: Mat3,
        base: VanishingPlane.Result.Plane,
    ): Pair<Double, Double> {
        var aspectSum = 0.0
        var normalShift = 0.0
        for (i in corners.indices) for (axis in 0..1) {
            var worst = 0.0
            for (sign in listOf(-1.0, 1.0)) {
                val d = sign * RectangleTolerances.MARK_UNCERTAINTY_PX
                val moved = corners.toMutableList()
                moved[i] = if (axis == 0) Vec2(corners[i].x + d, corners[i].y) else Vec2(corners[i].x, corners[i].y + d)
                val p = VanishingPlane.solve(moved, kInv) as? VanishingPlane.Result.Plane
                    ?: return Pair(Double.POSITIVE_INFINITY, PI)
                worst = max(worst, abs(p.aspect - base.aspect) / base.aspect)
                normalShift = max(normalShift, acos(p.frame.normal.dot(base.frame.normal).coerceIn(-1.0, 1.0)))
            }
            aspectSum += worst
        }
        return Pair(aspectSum, normalShift)
    }
}
