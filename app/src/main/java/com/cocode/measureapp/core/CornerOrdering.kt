package com.cocode.measureapp.core

import com.cocode.measureapp.geometry.Vec2

/**
 * Orders 4 tapped image points into clockwise [TL, TR, BR, BL] order.
 * Image coordinates with y pointing down.
 *
 * - TL = smallest x + y; BR = largest x + y.
 * - TR = smallest y - x; BL = largest y - x.
 */
object CornerOrdering {
    fun order(points: List<Vec2>): List<Vec2> {
        require(points.size == 4)
        val tl = points.minByOrNull { it.x + it.y }!!
        val br = points.maxByOrNull { it.x + it.y }!!
        val tr = points.minByOrNull { it.y - it.x }!!
        val bl = points.maxByOrNull { it.y - it.x }!!
        return listOf(tl, tr, br, bl)
    }
}
