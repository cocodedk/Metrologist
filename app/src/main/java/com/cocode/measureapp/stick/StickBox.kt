package com.cocode.measureapp.stick

import com.cocode.measureapp.geometry.Vec2

/** Derives a stick's two end points from a 4-corner box drawn around it. */
object StickBox {
    /**
     * Given the 4 corners of a box framing the stick (in order around the quad), returns the
     * stick's two ends: the midpoints of the box's SHORTER pair of opposite edges (the ends of
     * an elongated stick are its short sides).
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
     */
    fun longShortMeanEdges(box: List<Vec2>): Pair<Double, Double> {
        require(box.size == 4) { "stick box needs exactly 4 corners" }
        val pairA = (box[0].distanceTo(box[1]) + box[2].distanceTo(box[3])) / 2.0
        val pairB = (box[1].distanceTo(box[2]) + box[3].distanceTo(box[0])) / 2.0
        return if (pairA >= pairB) Pair(pairA, pairB) else Pair(pairB, pairA)
    }

    private fun midpoint(a: Vec2, b: Vec2) = Vec2((a.x + b.x) / 2.0, (a.y + b.y) / 2.0)
}
