package com.cocode.measureapp.geometry

import kotlin.math.abs

/**
 * Project image pixels onto the metric plane defined by [frame] (up to scale; the plane is
 * assumed at `normal·X = 1`).
 *
 * For each pixel `p`: ray = K⁻¹·(p.x, p.y, 1); denom = normal·ray (guarded);
 * X = ray·(1/denom); result = (e1·X, e2·X).
 */
fun projectToPlane(points: List<Vec2>, k: CameraIntrinsics, frame: PlaneFrame): List<Vec2> {
    val kInv = k.inverseMatrix()
    return points.map { p ->
        val ray = kInv * Vec3(p.x, p.y, 1.0)
        val denom = frame.normal.dot(ray)
        require(abs(denom) > Tolerances.PROJ_EPS) { "ray parallel to plane" }
        val x = ray * (1.0 / denom)
        Vec2(frame.e1.dot(x), frame.e2.dot(x))
    }
}
