package com.cocode.measureapp.geometry.rectangle

import com.cocode.measureapp.geometry.Mat3
import com.cocode.measureapp.geometry.PlaneFrame
import com.cocode.measureapp.geometry.Projective
import com.cocode.measureapp.geometry.Tolerances
import com.cocode.measureapp.geometry.Vec2
import com.cocode.measureapp.geometry.Vec3
import kotlin.math.abs

/**
 * Plane frame from the two homogeneous vanishing points of a `[TL, TR, BR, BL]` quad.
 *
 * Each vanishing point is back-projected as `K⁻¹ · v` on the raw homogeneous vector, so a
 * point at infinity (`w = 0`, image-parallel edges) yields its direction exactly and passes
 * continuously into nearby finite points. Only numeric degeneracy is rejected here.
 */
internal object VanishingPlane {
    sealed interface Result {
        data class Plane(
            val frame: PlaneFrame,
            val widthVp: Vec3,
            val heightVp: Vec3,
            val orthogonalityResidual: Double,
            val minRayCos: Double,
            val aspect: Double,
        ) : Result

        data class Degenerate(val reason: RectangleRejection, val detail: String) : Result
    }

    fun solve(corners: List<Vec2>, kInv: Mat3): Result {
        val (tl, tr, br, bl) = corners
        val edges = listOf(tl to tr, bl to br, tl to bl, tr to br).map { (a, b) ->
            val line = Projective.lineThrough(a, b)
            if (!NumericGuards.isFinite(line)) return degenerate(RectangleRejection.NUMERICAL_FAILURE, "edge $a-$b line overflows")
            unitLine(a, b, line) ?: return degenerate(RectangleRejection.COINCIDENT_CORNERS, "edge $a-$b has no length")
        }
        val widthVp = Projective.homogeneousIntersection(edges[0], edges[1])
        val heightVp = Projective.homogeneousIntersection(edges[2], edges[3])
        if (widthVp.norm() <= RectangleTolerances.ZERO_HOMOGENEOUS || heightVp.norm() <= RectangleTolerances.ZERO_HOMOGENEOUS) {
            return degenerate(RectangleRejection.ZERO_VANISHING_VECTOR, "opposite edges lie on one image line")
        }
        // Unit-scale each vanishing point before K⁻¹ so a near-zero homogeneous vector cannot
        // shrink below the normalisation guard; the back-projected direction is scale-free.
        val d1 = backProject(kInv, widthVp) ?: return numeric("width vanishing direction")
        val d2 = backProject(kInv, heightVp) ?: return numeric("height vanishing direction")
        val cross = d1.cross(d2)
        if (cross.norm() <= RectangleTolerances.MIN_DIRECTION_SIN) {
            return degenerate(RectangleRejection.COINCIDENT_DIRECTIONS, "edge directions coincide")
        }

        val rays = corners.map { backProject(kInv, Vec3(it.x, it.y, 1.0)) ?: return numeric("corner ray $it") }
        var normal = cross.normalized()
        if (normal.dot(rays.reduce { a, b -> a + b }) < 0.0) normal = normal * -1.0
        val cosines = rays.map { normal.dot(it) }
        val minCos = cosines.minOf { it }
        if (!(minCos > Tolerances.PROJ_EPS)) {
            return degenerate(RectangleRejection.PROJECTION_UNUSABLE, "corner rays straddle the plane (min cos $minCos)")
        }
        // Points on the plane `normal · X = 1`; orient e1 along TL->TR, e2 completes the triad.
        val pts = rays.indices.map { rays[it] * (1.0 / cosines[it]) }
        val span = (pts[1] - pts[0]) + (pts[2] - pts[3])
        val e1 = if (d1.dot(span) < 0.0) d1 * -1.0 else d1
        val frame = PlaneFrame(e1, normal.cross(e1).normalized(), normal)
        val aspect = aspect(pts, frame)
        if (!finite(frame) || !(aspect.isFinite() && aspect > 0.0)) {
            return degenerate(RectangleRejection.NUMERICAL_FAILURE, "non-finite plane frame or aspect")
        }
        return Result.Plane(frame, widthVp, heightVp, abs(d1.dot(d2)), minCos, aspect)
    }

    /**
     * Unit form of the finite homogeneous line [l] through [a] and [b], or null when the points
     * coincide. `|l| / (|a| |b|)` is the sine between the homogeneous points, evaluated with
     * overflow-safe norms so large but finite coordinates are not mistaken for coincidence.
     */
    private fun unitLine(a: Vec2, b: Vec2, l: Vec3): Vec3? {
        val na = NumericGuards.stableNorm(Vec3(a.x, a.y, 1.0))
        val nb = NumericGuards.stableNorm(Vec3(b.x, b.y, 1.0))
        val n = NumericGuards.stableNorm(l)
        return if (n / na <= RectangleTolerances.ZERO_HOMOGENEOUS * nb) null else NumericGuards.unitOrNull(l)
    }

    /** Unit camera-frame direction `K⁻¹ · v` for a nonzero homogeneous [v]; null on overflow. */
    private fun backProject(kInv: Mat3, v: Vec3): Vec3? =
        NumericGuards.unitOrNull(v)?.let { NumericGuards.unitOrNull(kInv * it) }

    private fun numeric(what: String) = degenerate(RectangleRejection.NUMERICAL_FAILURE, "$what is not finite")

    /** Rectified (top + bottom) / (left + right) edge-length ratio in plane coordinates. */
    private fun aspect(pts: List<Vec3>, frame: PlaneFrame): Double {
        val q = pts.map { Vec2(frame.e1.dot(it), frame.e2.dot(it)) }
        val width = q[1].distanceTo(q[0]) + q[2].distanceTo(q[3])
        val height = q[3].distanceTo(q[0]) + q[2].distanceTo(q[1])
        return width / height
    }

    private fun finite(f: PlaneFrame): Boolean =
        listOf(f.e1, f.e2, f.normal).all { it.x.isFinite() && it.y.isFinite() && it.z.isFinite() }

    private fun degenerate(reason: RectangleRejection, detail: String) = Result.Degenerate(reason, detail)
}
