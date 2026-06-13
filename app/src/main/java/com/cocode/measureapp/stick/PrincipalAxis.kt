package com.cocode.measureapp.stick

import com.cocode.measureapp.geometry.Tolerances
import com.cocode.measureapp.geometry.Vec2
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Fits the principal axis (first principal component) through a cloud of 2-D points.
 * Uses population covariance — appropriate when the full point set is known.
 */
object PrincipalAxis {

    fun fit(points: List<Vec2>): Axis {
        require(points.size >= 2) { "need at least 2 points, got ${points.size}" }

        val n = points.size.toDouble()
        val cx = points.sumOf { it.x } / n
        val cy = points.sumOf { it.y } / n
        val centroid = Vec2(cx, cy)

        val sxx = points.sumOf { (it.x - cx) * (it.x - cx) } / n
        val sxy = points.sumOf { (it.x - cx) * (it.y - cy) } / n
        val syy = points.sumOf { (it.y - cy) * (it.y - cy) } / n

        val halfDiff = (sxx - syy) / 2.0
        val l1 = (sxx + syy) / 2.0 + sqrt(halfDiff * halfDiff + sxy * sxy)

        val direction = when {
            abs(sxy) > Tolerances.NORM_EPS -> Vec2(l1 - syy, sxy).normalized()
            sxx >= syy -> Vec2(1.0, 0.0)
            else -> Vec2(0.0, 1.0)
        }

        return Axis(centroid, direction)
    }
}
