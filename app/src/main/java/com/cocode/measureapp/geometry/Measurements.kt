package com.cocode.measureapp.geometry

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.PI

/**
 * Real-world measurements of a quadrilateral, in the same unit as the scaled corners.
 *
 * `cornerAngles` holds the interior angle (degrees) at each corner, one per corner.
 */
data class MeasurementResult(
    val width: Double,
    val height: Double,
    val area: Double,
    val diagonal: Double,
    val cornerAngles: List<Double>,
)

/**
 * Turns four real-scaled metric corners into rectangle measurements.
 *
 * Corners are ordered clockwise from top-left: `[TL, TR, BR, BL]`.
 */
object Measurements {
    /**
     * @param corners exactly 4 points in `[TL, TR, BR, BL]` order, already real-scaled.
     *
     * - `width`    = mean(|TR-TL|, |BR-BL|)  (top and bottom edges)
     * - `height`   = mean(|BL-TL|, |BR-TR|)  (left and right edges)
     * - `area`     = absolute shoelace area of the quadrilateral
     * - `diagonal` = mean(|BR-TL|, |BL-TR|)  (the two diagonals)
     * - `cornerAngles[i]` = interior angle (degrees) at corner i between its adjacent edges
     */
    fun compute(corners: List<Vec2>): MeasurementResult {
        require(corners.size == 4) { "need exactly 4 corners, got ${corners.size}" }
        val (tl, tr, br, bl) = corners

        val width = (tr.distanceTo(tl) + br.distanceTo(bl)) / 2.0
        val height = (bl.distanceTo(tl) + br.distanceTo(tr)) / 2.0
        val diagonal = (br.distanceTo(tl) + bl.distanceTo(tr)) / 2.0
        val area = shoelace(corners)
        val angles = corners.indices.map { interiorAngleDeg(corners, it) }
        return MeasurementResult(width, height, area, diagonal, angles)
    }

    /** Absolute polygon area via the shoelace formula. */
    private fun shoelace(p: List<Vec2>): Double {
        var sum = 0.0
        for (i in p.indices) {
            val a = p[i]
            val b = p[(i + 1) % p.size]
            sum += a.x * b.y - b.x * a.y
        }
        return abs(sum) / 2.0
    }

    /**
     * Interior angle (degrees) at corner [i] between edges to its previous and next corners.
     *
     * Rejects degenerate quads where an adjacent corner coincides with corner [i]
     * (zero-length edge). Without this guard the denominator `prev.norm() * next.norm()`
     * is 0.0, the ratio is 0.0/0.0 = NaN (which `coerceIn` leaves unchanged), and
     * `acos(NaN)` = NaN would silently poison the result.
     */
    private fun interiorAngleDeg(p: List<Vec2>, i: Int): Double {
        val n = p.size
        val prev = p[(i - 1 + n) % n] - p[i]
        val next = p[(i + 1) % n] - p[i]
        val pn = prev.norm()
        val nn = next.norm()
        require(pn > Tolerances.NORM_EPS && nn > Tolerances.NORM_EPS) {
            "degenerate quad: zero-length edge at corner $i"
        }
        val cos = (prev.dot(next) / (pn * nn)).coerceIn(-1.0, 1.0)
        return acos(cos) * 180.0 / PI
    }
}
