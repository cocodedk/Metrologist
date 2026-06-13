package com.cocode.measureapp.geometry

import kotlin.math.abs

/** Whether the measured surface stands upright (a wall) or lies flat (a floor/table). */
enum class SurfaceOrientation { VERTICAL, HORIZONTAL }

/**
 * Recovers a plane orientation from the camera-frame gravity vector.
 *
 * Camera frame is x-right, y-down, z-forward; [gravity] is the unit camera-frame vector
 * pointing along world **down** (so for a level camera `gravity = (0, 1, 0)`). Because
 * `projectToPlane` depends only on the plane **normal**, the in-plane basis `(e1, e2)` is
 * any orthonormal pair on the plane — measurements are invariant to that choice.
 */
object GravitySolver {
    private val AXIS = Vec3(0.0, 0.0, 1.0)

    fun solve(gravity: Vec3, orientation: SurfaceOrientation): PlaneSolution {
        val worldUp = (gravity * -1.0).normalized()
        return when (orientation) {
            SurfaceOrientation.VERTICAL -> solveVertical(worldUp)
            SurfaceOrientation.HORIZONTAL -> solveHorizontal(worldUp)
        }
    }

    /**
     * Wall: the plane contains [worldUp], so the normal is horizontal and chosen to face
     * the camera. If the optical axis is parallel to [worldUp] there is no horizontal
     * component to face the camera — return a fallback frame with confidence `0`.
     */
    private fun solveVertical(worldUp: Vec3): PlaneSolution {
        val candidate = AXIS * -1.0
        val nHoriz = candidate - worldUp * candidate.dot(worldUp)
        if (nHoriz.norm() < Tolerances.PROJ_EPS) {
            val normal = stableHorizontal(worldUp)
            val e1 = worldUp.cross(normal).normalized()
            return PlaneSolution(PlaneFrame(e1, worldUp, normal), SolverKind.GRAVITY, 0.0)
        }
        val normal = nHoriz.normalized()
        val e2 = worldUp
        val e1 = worldUp.cross(normal).normalized()
        val confidence = nHoriz.norm().coerceIn(0.0, 1.0)
        return PlaneSolution(PlaneFrame(e1, e2, normal), SolverKind.GRAVITY, confidence)
    }

    /**
     * Floor: the normal is [worldUp]. The in-plane `e2` follows the camera forward
     * projected into the plane; if that collapses (axis parallel to [worldUp]) fall back
     * to a deterministic in-plane direction.
     */
    private fun solveHorizontal(worldUp: Vec3): PlaneSolution {
        val normal = worldUp
        val fwd = AXIS - worldUp * AXIS.dot(worldUp)
        val e2 = if (fwd.norm() > Tolerances.PROJ_EPS) fwd.normalized() else stableHorizontal(worldUp)
        val e1 = normal.cross(e2).normalized()
        val confidence = abs(AXIS.dot(worldUp)).coerceIn(0.0, 1.0)
        return PlaneSolution(PlaneFrame(e1, e2, normal), SolverKind.GRAVITY, confidence)
    }

    /**
     * A deterministic unit vector orthogonal to [up]: take a fixed reference axis, remove
     * its [up] component, and normalize.
     *
     * Both call sites (the VERTICAL degenerate fallback and the HORIZONTAL fwd-collapse
     * path) are reached only when [up] is (anti)parallel to the optical axis. The fixed
     * reference `(1,0,0)` is therefore always perpendicular-ish to [up] (near the z-axis),
     * so the residual is non-degenerate and a conditional swap would be dead code.
     */
    private fun stableHorizontal(up: Vec3): Vec3 {
        val ref = Vec3(1.0, 0.0, 0.0)
        return (ref - up * ref.dot(up)).normalized()
    }
}
