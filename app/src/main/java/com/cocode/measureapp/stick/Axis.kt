package com.cocode.measureapp.stick

import com.cocode.measureapp.geometry.Vec2

/**
 * An axis in 2-D image space: a [origin] point and a unit [direction] vector.
 * All distances and projections are in the same pixel units as the inputs.
 */
data class Axis(val origin: Vec2, val direction: Vec2) {

    /** Signed scalar projection of [p] onto this axis (distance from [origin] along [direction]). */
    fun project(p: Vec2): Double = (p - origin).dot(direction)

    /** Point on the axis at parameter [t] from [origin]. */
    fun pointAt(t: Double): Vec2 = origin + direction * t

    /**
     * Perpendicular (off-axis) distance from [p] to the axis line.
     * Computed as the residual after subtracting the along-axis component.
     */
    fun perpendicularDistance(p: Vec2): Double =
        ((p - origin) - direction * project(p)).norm()
}
