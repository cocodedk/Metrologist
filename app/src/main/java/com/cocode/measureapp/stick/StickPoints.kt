package com.cocode.measureapp.stick

import com.cocode.measureapp.geometry.Vec2

/**
 * The result of a successful stick assembly: [bandCount + 1] points ordered end-to-end
 * along the stick, plus a [confidence] score in [0, 1].
 */
data class StickPoints(val points: List<Vec2>, val confidence: Double)
