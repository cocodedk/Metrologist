package com.cocode.measureapp.core.validation

import com.cocode.measureapp.geometry.QuadMetrics
import com.cocode.measureapp.geometry.Tolerances
import com.cocode.measureapp.geometry.Vec2
import kotlin.math.abs

/**
 * The single set of marker degeneracy rules, shared by the object and stick validators.
 * Every threshold is relative to the quad's own diameter (see [Tolerances]), so outcomes
 * do not depend on display zoom or image resolution.
 */
internal object QuadRules {
    /** Order-independent checks: count, finite coordinates, distinct vertices / edges. */
    fun inputRule(points: List<Vec2>): MarkerRule? {
        if (points.size != 4) return MarkerRule.POINT_COUNT
        if (points.any { !it.x.isFinite() || !it.y.isFinite() }) return MarkerRule.NON_FINITE
        val diameter = QuadMetrics.diameter(points)
        // Every edge is a vertex pair, so this also rules out zero-length edges.
        val minSeparation = points.indices.minOf { i ->
            (i + 1 until 4).minOfOrNull { j -> points[i].distanceTo(points[j]) } ?: Double.MAX_VALUE
        }
        val separated = diameter.isFinite() && diameter > 0.0 &&
            minSeparation > Tolerances.MARKER_MIN_SEPARATION_FRACTION * diameter
        return if (separated) null else MarkerRule.COINCIDENT_VERTICES
    }

    /**
     * Checks a cyclic vertex order that already passed [inputRule]. For four vertices, equal
     * turn signs at every corner is exactly a simple convex quad; two-and-two signs is a
     * crossed (bow-tie) cycle and three-and-one a concave one.
     */
    fun cycleRule(cycle: List<Vec2>): MarkerRule? {
        val sines = turnSines(cycle)
        if (sines.any { abs(it) <= Tolerances.MARKER_MIN_TURN_SIN }) {
            return MarkerRule.COLLINEAR_VERTICES
        }
        when (sines.count { it > 0.0 }) {
            2 -> return MarkerRule.SELF_CROSSING
            1, 3 -> return MarkerRule.CONCAVE
        }
        if (!QuadMetrics.hasResolvedArea(cycle)) return MarkerRule.ZERO_AREA
        return null
    }

    /** Winding of a cycle accepted by [cycleRule]. */
    fun winding(cycle: List<Vec2>): Winding =
        if (QuadMetrics.signedArea(cycle) > 0.0) Winding.CLOCKWISE else Winding.COUNTER_CLOCKWISE

    /** Signed sine of the turn at each vertex; positive turns clockwise in a y-down frame. */
    private fun turnSines(p: List<Vec2>): List<Double> = p.indices.map { i ->
        val incoming = p[i] - p[(i + 3) % 4]
        val outgoing = p[(i + 1) % 4] - p[i]
        val cross = incoming.x * outgoing.y - incoming.y * outgoing.x
        cross / (incoming.norm() * outgoing.norm())
    }
}
