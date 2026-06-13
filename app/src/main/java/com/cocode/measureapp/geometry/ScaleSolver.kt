package com.cocode.measureapp.geometry

import kotlin.math.abs

/**
 * Recovered metric-to-real scale with an agreement score.
 *
 * `agreement` is the maximum relative deviation among the per-segment scale estimates
 * (0.0 = perfect agreement; larger = more disagreement, e.g. from a mismarked joint).
 */
data class ScaleResult(val scale: Double, val agreement: Double)

/**
 * Recovers the scale that converts up-to-scale metric coordinates into the stick's real
 * unit by comparing measured segment lengths against the known [StickProfile].
 */
object ScaleSolver {
    /**
     * @param stickMetric stick marker points in up-to-scale metric coordinates, ordered
     *   end-to-end (>= 2 points, consecutive points strictly distinct).
     * @param profile the known real stick length and band subdivision.
     *
     * Point-count handling:
     * - `bandCount + 1` points (e.g. 5 for 4 bands): one segment per band, each of real
     *   length `totalLength / bandCount`.
     * - exactly 2 points (ends only): one segment of real length `totalLength`.
     * - any other count: treat the polyline as a single end-to-end span (first..last) of
     *   real length `totalLength`; intermediate points are ignored for scale.
     */
    fun solve(stickMetric: List<Vec2>, profile: StickProfile): ScaleResult {
        require(stickMetric.size >= 2) {
            "need >= 2 stick points, got ${stickMetric.size}"
        }

        val estimates: List<Double> = when (stickMetric.size) {
            profile.bandCount + 1 -> {
                val realLen = profile.totalLength / profile.bandCount
                segmentDistances(stickMetric).map { realLen / it }
            }
            2 -> listOf(profile.totalLength / segmentDistance(stickMetric[0], stickMetric[1]))
            else -> {
                // Single end-to-end span: first point to last point.
                val span = segmentDistance(stickMetric.first(), stickMetric.last())
                listOf(profile.totalLength / span)
            }
        }

        val scale = median(estimates)
        val agreement = if (estimates.size == 1) {
            0.0
        } else {
            estimates.maxOf { abs(it - scale) } / scale
        }
        return ScaleResult(scale, agreement)
    }

    /** Consecutive Euclidean distances; each must be strictly positive. */
    private fun segmentDistances(points: List<Vec2>): List<Double> =
        (0 until points.size - 1).map { segmentDistance(points[it], points[it + 1]) }

    private fun segmentDistance(a: Vec2, b: Vec2): Double {
        val d = a.distanceTo(b)
        require(d > Tolerances.NORM_EPS) { "zero-length stick segment" }
        return d
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[mid]
        } else {
            (sorted[mid - 1] + sorted[mid]) / 2.0
        }
    }
}
