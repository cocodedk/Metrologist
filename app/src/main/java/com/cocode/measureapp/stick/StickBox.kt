package com.cocode.measureapp.stick

import com.cocode.measureapp.core.validation.MarkerValidation
import com.cocode.measureapp.core.validation.MarkerValidator
import com.cocode.measureapp.geometry.Vec2

/**
 * Four-corner box drawn around the stick, in cyclic order. Edges `0-1`/`2-3` and `1-2`/`3-0`
 * are the opposite pairs. Either winding is valid; reversal and cyclic shifts keep both pairs,
 * so every derived length is winding-independent.
 */
object StickBox {
    /**
     * Given the 4 corners of a box framing the stick (in order around the quad), returns the
     * stick's two ends: the midpoints of the box's SHORTER pair of opposite edges (the ends of
     * an elongated stick are its short sides). Lenient on purpose: it is drawn for in-progress
     * marks; use [requireValid] before relying on the box for scale.
     */
    fun ends(box: List<Vec2>): List<Vec2> {
        require(box.size == 4) { "stick box needs exactly 4 corners" }
        val edge01 = box[0].distanceTo(box[1])
        val edge12 = box[1].distanceTo(box[2])
        return if (edge01 <= edge12) {
            listOf(midpoint(box[0], box[1]), midpoint(box[2], box[3]))
        } else {
            listOf(midpoint(box[1], box[2]), midpoint(box[3], box[0]))
        }
    }

    /**
     * Mean lengths of the box's two opposite edge pairs as `(longMean, shortMean)`. Edges
     * `0-1`/`2-3` form one opposite pair and `1-2`/`3-0` the other; whichever pair is longer
     * (by its first edge) is the LONG pair (the stick's length axis), the other the SHORT pair
     * (its width axis). Averaging opposite edges tolerates mild perspective trapezoiding.
     *
     * Low-level and length-only compatible: a zero-width box is allowed here. It is NOT the
     * app's marking contract; marked boxes go through [requireValid] first.
     */
    fun longShortMeanEdges(box: List<Vec2>): Pair<Double, Double> {
        require(box.size == 4) { "stick box needs exactly 4 corners" }
        val pairA = (box[0].distanceTo(box[1]) + box[2].distanceTo(box[3])) / 2.0
        val pairB = (box[1].distanceTo(box[2]) + box[3].distanceTo(box[0])) / 2.0
        return if (pairA >= pairB) Pair(pairA, pairB) else Pair(pairB, pairA)
    }

    /**
     * Returns the marked [box] unchanged when it is a valid four-corner box in either winding
     * (see [MarkerValidator.validateStick]); throws [IllegalArgumentException] with the
     * correction message for crossed, collapsed, collinear or non-finite marks.
     */
    fun requireValid(box: List<Vec2>): List<Vec2> =
        when (val v = MarkerValidator.validateStick(box)) {
            is MarkerValidation.Accepted -> v.corners
            is MarkerValidation.Rejected -> throw IllegalArgumentException(v.message)
        }

    /**
     * The auto-detection handoff: the box framed around detected end points [a] and [b],
     * `a+n·hw, b+n·hw, b−n·hw, a−n·hw` with `n` the left normal of `a→b`. The result is
     * counter-clockwise in the y-down image frame and has long edges `0-1`/`2-3`.
     */
    fun fromDetectedEnds(a: Vec2, b: Vec2, halfWidth: Double = detectedHalfWidth(a, b)): List<Vec2> {
        val dir = (b - a).normalized()
        val perp = Vec2(-dir.y, dir.x)
        return listOf(a + perp * halfWidth, b + perp * halfWidth, b - perp * halfWidth, a - perp * halfWidth)
    }

    /** Default detected-box half width: 6% of the stick length, at least 8 image pixels. */
    fun detectedHalfWidth(a: Vec2, b: Vec2): Double = maxOf(8.0, a.distanceTo(b) * 0.06)

    private fun midpoint(a: Vec2, b: Vec2) = Vec2((a.x + b.x) / 2.0, (a.y + b.y) / 2.0)
}
