package com.cocode.measureapp.geometry

import kotlin.math.abs

/** Homogeneous-coordinate helpers for image lines and their intersections. */
object Projective {
    /** Homogeneous line through two image points. */
    fun lineThrough(a: Vec2, b: Vec2): Vec3 =
        Vec3(a.x, a.y, 1.0).cross(Vec3(b.x, b.y, 1.0))

    /**
     * Homogeneous intersection `l1 × l2`, never divided: a zero third coordinate is a point at
     * infinity (a valid direction), and only the zero vector means the lines coincide.
     */
    fun homogeneousIntersection(l1: Vec3, l2: Vec3): Vec3 = l1.cross(l2)

    /** Homogeneous vanishing point of world-parallel edges (a1->a2) and (b1->b2). */
    fun homogeneousVanishingPoint(a1: Vec2, a2: Vec2, b1: Vec2, b2: Vec2): Vec3 =
        homogeneousIntersection(lineThrough(a1, a2), lineThrough(b1, b2))

    /** Finite intersection of two homogeneous lines; null when parallel in the image. */
    fun intersection(l1: Vec3, l2: Vec3): Vec2? {
        val p = homogeneousIntersection(l1, l2)
        if (abs(p.z) < Tolerances.PROJ_EPS) return null
        return Vec2(p.x / p.z, p.y / p.z)
    }

    /**
     * Finite vanishing point of world-parallel edges (a1->a2) and (b1->b2); null when the edges
     * appear parallel. Plane recovery uses [homogeneousVanishingPoint] instead, because a
     * vanishing point at infinity is a valid direction, not a degenerate plane.
     */
    fun vanishingPoint(a1: Vec2, a2: Vec2, b1: Vec2, b2: Vec2): Vec2? =
        intersection(lineThrough(a1, a2), lineThrough(b1, b2))
}
