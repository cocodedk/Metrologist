package com.cocode.measureapp.geometry

/**
 * Stick of known real-world dimensions (engine is unit-agnostic). [totalLength] is the long
 * axis, divided into [bandCount] equal bands. [width] is the across-stick dimension in the
 * SAME unit as [totalLength]; `0.0` means width unknown/ignored (length-only calibration).
 */
data class StickProfile(
    val totalLength: Double,
    val bandCount: Int = 4,
    val width: Double = 0.0,
) {
    init {
        require(bandCount >= 1) { "bandCount must be >= 1, got $bandCount" }
        require(width >= 0.0) { "width must be >= 0, got $width" }
    }
}

/** Orthonormal camera-frame triad: `e1`,`e2` span the plane, `normal` is perpendicular. */
data class PlaneFrame(val e1: Vec3, val e2: Vec3, val normal: Vec3)

/** Which solver produced a [PlaneSolution]. */
enum class SolverKind { RECTANGLE, GRAVITY }

/** A recovered plane orientation with the solver used and a confidence in `[0,1]`. */
data class PlaneSolution(val frame: PlaneFrame, val solver: SolverKind, val confidence: Double)
