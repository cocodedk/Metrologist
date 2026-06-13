package com.cocode.measureapp.geometry

/** Stick of known real-world length divided into equal bands (engine is unit-agnostic). */
data class StickProfile(val totalLength: Double, val bandCount: Int = 4)

/** Orthonormal camera-frame triad: `e1`,`e2` span the plane, `normal` is perpendicular. */
data class PlaneFrame(val e1: Vec3, val e2: Vec3, val normal: Vec3)

/** Which solver produced a [PlaneSolution]. */
enum class SolverKind { RECTANGLE, GRAVITY }

/** A recovered plane orientation with the solver used and a confidence in `[0,1]`. */
data class PlaneSolution(val frame: PlaneFrame, val solver: SolverKind, val confidence: Double)
