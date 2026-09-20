package com.cocode.measureapp.geometry

import kotlin.math.abs

/** Below this pitch the camera is looking ahead, which can only be a wall. */
private const val WALL_MAX_PITCH_DEG = 30.0

/** Above this pitch it is looking down or up: floor, table or ceiling. */
private const val HORIZONTAL_MIN_PITCH_DEG = 60.0

/**
 * The surface the camera is pointed at, from its pitch alone — the phone already knows which
 * way is down, so asking the user is a question with a known answer.
 *
 * Returns null when no answer is justified: a missing reading, or a pitch in the band between
 * the two cases, where a guess could silently pick the wrong measurement method. The caller
 * asks only then. See [TiltAngles] for the sign convention (positive pitch = looking down).
 */
fun surfaceFromTilt(tilt: TiltAngles?): SurfaceOrientation? {
    val pitch = tilt?.pitchDeg?.takeIf { it.isFinite() } ?: return null
    return when {
        abs(pitch) <= WALL_MAX_PITCH_DEG -> SurfaceOrientation.VERTICAL
        abs(pitch) >= HORIZONTAL_MIN_PITCH_DEG -> SurfaceOrientation.HORIZONTAL
        else -> null
    }
}
