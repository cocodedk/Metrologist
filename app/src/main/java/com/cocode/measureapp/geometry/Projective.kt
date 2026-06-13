package com.cocode.measureapp.geometry

import kotlin.math.abs

/** Homogeneous-coordinate helpers for image lines and their intersections. */
object Projective {
    /** Homogeneous line through two image points. */
    fun lineThrough(a: Vec2, b: Vec2): Vec3 =
        Vec3(a.x, a.y, 1.0).cross(Vec3(b.x, b.y, 1.0))

    /** Intersection of two homogeneous lines; null when parallel in the image. */
    fun intersection(l1: Vec3, l2: Vec3): Vec2? {
        val p = l1.cross(l2)
        if (abs(p.z) < 1e-9) return null
        return Vec2(p.x / p.z, p.y / p.z)
    }

    /**
     * Vanishing point of world-parallel edges (a1->a2) and (b1->b2).
     * Null when the edges appear parallel in the image (fronto-parallel view).
     */
    fun vanishingPoint(a1: Vec2, a2: Vec2, b1: Vec2, b2: Vec2): Vec2? =
        intersection(lineThrough(a1, a2), lineThrough(b1, b2))
}
