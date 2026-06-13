package com.cocode.measureapp.geometry

import kotlin.math.abs

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

        val normal = d1.cross(d2).normalized()
        val e1 = d1
        val e2 = normal.cross(e1).normalized()
        val confidence = (1.0 - abs(d1.dot(d2))).coerceIn(0.0, 1.0)

        return PlaneSolution(PlaneFrame(e1, e2, normal), SolverKind.RECTANGLE, confidence)
    }
}
