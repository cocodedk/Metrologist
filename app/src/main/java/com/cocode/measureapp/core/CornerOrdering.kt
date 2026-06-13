package com.cocode.measureapp.core

import com.cocode.measureapp.geometry.Vec2
import kotlin.math.atan2

/**
 * Orders 4 tapped image points into clockwise [TL, TR, BR, BL] order.
 * Image coordinates with y pointing down.
 *
 * The points are sorted around their centroid by angle (atan2(y-cy, x-cx)),
 * which yields a guaranteed clockwise cycle in y-down image coordinates, then
 * the cycle is rotated to start at the top-left-most vertex (smallest x + y).
 * This is robust to arbitrary tap order, tie-stable for edges along the image
 * diagonals, and correct for diamond-oriented (~45 deg) quads where the plain
 * sum/diff heuristic collapses two corners onto one point.
 */
object CornerOrdering {
    fun order(points: List<Vec2>): List<Vec2> {
        require(points.size == 4) { "expected exactly 4 corners, got ${points.size}" }
        val cx = points.sumOf { it.x } / 4.0
        val cy = points.sumOf { it.y } / 4.0
        val clockwise = points.sortedBy { atan2(it.y - cy, it.x - cx) }
        val topLeft = clockwise.minWith(topLeftOrder)
        val start = clockwise.indexOf(topLeft)
        val ordered = (0 until 4).map { clockwise[(start + it) % 4] }
        require(ordered.toSet().size == 4) {
            "corners are degenerate/collinear; cannot order into 4 distinct corners"
        }
        return ordered
    }

    /**
     * Orders vertices by "top-left-ness": smallest x + y first; on a tie (an
     * edge along the -45 deg diagonal, e.g. a diamond) prefer the upper vertex
     * (smallest y), then the left one (smallest x) for full determinism.
     */
    private val topLeftOrder: Comparator<Vec2> =
        compareBy({ it.x + it.y }, { it.y }, { it.x })
}
