package com.cocode.measureapp.stick

import com.cocode.measureapp.geometry.Tolerances
import com.cocode.measureapp.geometry.Vec2
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Assembles [StickPoints] from a list of stick-body points and red-band points.
 *
 * Returns null when the geometry is degenerate, collinearity is too low, or the
 * red-band pattern score is too low to reach [minConfidence].
 */
object StickAssembler {

    fun assemble(
        stickPoints: List<Vec2>,
        redPoints: List<Vec2>,
        bandCount: Int = 4,
        minConfidence: Double = 0.3,
    ): StickPoints? {
        require(bandCount >= 2) { "bandCount must be >= 2, got $bandCount" }
        if (stickPoints.size < 2) return null

        val axis = PrincipalAxis.fit(stickPoints)
        val ts = stickPoints.map { axis.project(it) }
        val tMin = ts.min()
        val tMax = ts.max()
        val len = tMax - tMin
        if (len < Tolerances.NORM_EPS) return null

        // Evenly-spaced sample points from one end to the other (bandCount+1 points).
        val fivePoints = (0..bandCount).map { i ->
            axis.pointAt(tMin + len * i.toDouble() / bandCount)
        }

        // Collinearity: how tightly do the stick points hug the axis?
        val perpRms = sqrt(stickPoints.map { axis.perpendicularDistance(it) }
            .sumOf { it * it } / stickPoints.size)
        val collinearity = (1.0 - perpRms / (len * 0.1)).coerceIn(0.0, 1.0)

        // Red-pattern score: check whether red points fall predominantly in even or odd bands.
        val usableReds = redPoints.filter { p ->
            val u = (axis.project(p) - tMin) / len
            u in (-1e-9)..(1.0 + 1e-9)
        }

        val redScore: Double
        if (usableReds.isEmpty()) {
            redScore = 0.0
        } else {
            val bandIndices = usableReds.map { p ->
                val u = (axis.project(p) - tMin) / len
                floor(u * bandCount).toInt().coerceIn(0, bandCount - 1)
            }
            val total = bandIndices.size.toDouble()
            val inEven = bandIndices.count { it % 2 == 0 } / total
            val inOdd  = bandIndices.count { it % 2 == 1 } / total
            redScore = maxOf(inEven, inOdd)
        }

        val confidence = (collinearity * redScore).coerceIn(0.0, 1.0)
        if (confidence < minConfidence) return null

        return StickPoints(fivePoints, confidence)
    }
}
