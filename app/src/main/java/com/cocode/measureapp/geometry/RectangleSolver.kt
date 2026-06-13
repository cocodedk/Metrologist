package com.cocode.measureapp.geometry

import kotlin.math.abs
import kotlin.math.acos

/**
 * Recovers a plane orientation from four image corners of a real-world rectangle.
 *
 * Corners are `[TL, TR, BR, BL]` in clockwise order. The two pairs of world-parallel
 * edges give two vanishing points; their back-projected directions are orthogonal in
 * the world, so they yield the plane's spanning axes and normal in the camera frame.
 */
object RectangleSolver {
    fun solve(corners: List<Vec2>, k: CameraIntrinsics): PlaneSolution? {
        require(corners.size == 4) { "expected 4 corners [TL,TR,BR,BL], got ${corners.size}" }
        val (tl, tr, br, bl) = corners

        val vp1 = Projective.vanishingPoint(tl, tr, bl, br) ?: return null
        val vp2 = Projective.vanishingPoint(tl, bl, tr, br) ?: return null

        val kInv = k.inverseMatrix()
        val d1 = (kInv * Vec3(vp1.x, vp1.y, 1.0)).normalized()
        val d2 = (kInv * Vec3(vp2.x, vp2.y, 1.0)).normalized()

        // Coincident / near-coincident vanishing points make d1 ∥ d2, so the cross
        // product is the zero vector. Guard before normalizing to honor the nullable
        // contract instead of throwing from normalized().
        val cross = d1.cross(d2)
        if (cross.norm() < Tolerances.NORM_EPS) return null
        val normal = cross.normalized()
        val e1 = d1
        // `normal` is perpendicular to `e1` by construction, so `normal × e1` is a unit
        // vector and never degenerates — no extra guard needed here.
        val e2 = normal.cross(e1).normalized()

        return PlaneSolution(
            PlaneFrame(e1, e2, normal),
            SolverKind.RECTANGLE,
            imageQuadConfidence(tl, tr, br, bl),
        )
    }

    /**
     * Confidence in `[0,1]` reflecting how square (non-skewed) the imaged quad is.
     *
     * The back-projected `d1·d2` is identically 0 for any true rectangle imaged with the
     * matching intrinsics, so it cannot measure skew. Instead we use an image-space
     * quantity that genuinely varies with tilt: the deviation of the four corner angles
     * of the pixel quad from 90°. A right-angled image quad → 1.0; a maximally skewed
     * one → toward 0.0.
     */
    private fun imageQuadConfidence(tl: Vec2, tr: Vec2, br: Vec2, bl: Vec2): Double {
        val pts = listOf(tl, tr, br, bl)
        var totalDev = 0.0
        for (i in 0 until 4) {
            val prev = pts[(i + 3) % 4]
            val cur = pts[i]
            val next = pts[(i + 1) % 4]
            totalDev += abs(cornerAngleDeg(prev, cur, next) - 90.0)
        }
        val meanDev = totalDev / 4.0
        return (1.0 - meanDev / 90.0).coerceIn(0.0, 1.0)
    }

    /**
     * Interior angle (degrees) at [cur] between edges (cur→prev) and (cur→next).
     * On the success path the two vanishing points are distinct and non-null, so no two
     * adjacent corners coincide and the edge lengths are strictly positive; the
     * `coerceIn` only guards `acos` against floating-point domain overshoot.
     */
    private fun cornerAngleDeg(prev: Vec2, cur: Vec2, next: Vec2): Double {
        val a = prev - cur
        val b = next - cur
        val cosA = (a.dot(b) / (a.norm() * b.norm())).coerceIn(-1.0, 1.0)
        return Math.toDegrees(acos(cosA))
    }
}
