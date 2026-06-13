package com.cocode.measureapp.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class GravitySolverTest {
    private fun assertVec(expected: Vec3, actual: Vec3, eps: Double = 1e-9) {
        assertEquals("x", expected.x, actual.x, eps)
        assertEquals("y", expected.y, actual.y, eps)
        assertEquals("z", expected.z, actual.z, eps)
    }

    /** All three axes are unit vectors and mutually orthogonal. */
    private fun assertOrthonormal(f: PlaneFrame, eps: Double = 1e-9) {
        assertEquals("|e1|", 1.0, f.e1.norm(), eps)
        assertEquals("|e2|", 1.0, f.e2.norm(), eps)
        assertEquals("|normal|", 1.0, f.normal.norm(), eps)
        assertEquals("e1·e2", 0.0, f.e1.dot(f.e2), eps)
        assertEquals("e1·normal", 0.0, f.e1.dot(f.normal), eps)
        assertEquals("e2·normal", 0.0, f.e2.dot(f.normal), eps)
    }

    @Test
    fun levelCameraVertical_facesCameraWithCanonicalFrame() {
        val sol = GravitySolver.solve(Vec3(0.0, 1.0, 0.0), SurfaceOrientation.VERTICAL)

        assertEquals(SolverKind.GRAVITY, sol.solver)
        assertVec(Vec3(0.0, 0.0, -1.0), sol.frame.normal)
        assertVec(Vec3(0.0, -1.0, 0.0), sol.frame.e2)
        assertVec(Vec3(1.0, 0.0, 0.0), sol.frame.e1)
        assertEquals(1.0, sol.confidence, 1e-9)
        assertOrthonormal(sol.frame)
    }

    @Test
    fun topDownHorizontal_normalIsWorldUpWithOrthonormalFrame() {
        // Camera looking straight down the floor: world "down" aligns with the optical
        // axis (+z), so gravity = (0,0,1) and worldUp = (0,0,-1).
        val sol = GravitySolver.solve(Vec3(0.0, 0.0, 1.0), SurfaceOrientation.HORIZONTAL)

        assertEquals(SolverKind.GRAVITY, sol.solver)
        // worldUp = -gravity = (0,0,-1)
        assertVec(Vec3(0.0, 0.0, -1.0), sol.frame.normal)
        assertOrthonormal(sol.frame)
        // Looking straight down the optical axis => maximal confidence.
        assertEquals(1.0, sol.confidence, 1e-9)
    }

    @Test
    fun verticalDegenerate_opticalAxisParallelToWorldUp_hitsZeroConfidenceFallback() {
        // Camera optical axis aligned with world up (worldUp = (0,0,1)): the wall normal
        // has no horizontal component to face the camera -> degenerate fallback.
        val sol = GravitySolver.solve(Vec3(0.0, 0.0, -1.0), SurfaceOrientation.VERTICAL)

        assertEquals(SolverKind.GRAVITY, sol.solver)
        assertEquals(0.0, sol.confidence, 1e-12)
        assertOrthonormal(sol.frame)
    }

    @Test
    fun horizontalFwdDegenerate_hitsStableHorizontalBranch() {
        // worldUp parallel to the optical axis (gravity=(0,0,-1) => worldUp=(0,0,1)=axis)
        // collapses fwd = axis - worldUp*(axis·worldUp) to zero, forcing stableHorizontal.
        val sol = GravitySolver.solve(Vec3(0.0, 0.0, -1.0), SurfaceOrientation.HORIZONTAL)

        assertEquals(SolverKind.GRAVITY, sol.solver)
        assertVec(Vec3(0.0, 0.0, 1.0), sol.frame.normal)
        assertOrthonormal(sol.frame)
    }

    @Test
    fun verticalSkimming_lowersConfidence() {
        // Camera tilted so the optical axis skims along the wall: confidence < 1 but > 0.
        val g = Vec3(0.0, 0.6, -0.8) // unit-ish, world down tilted
        val sol = GravitySolver.solve(g, SurfaceOrientation.VERTICAL)
        assertTrue("confidence in (0,1)", sol.confidence > 0.0 && sol.confidence < 1.0)
        assertOrthonormal(sol.frame)
        // Normal must be horizontal (perpendicular to worldUp).
        val worldUp = (g * -1.0).normalized()
        assertEquals(0.0, sol.frame.normal.dot(worldUp), 1e-9)
        assertTrue(abs(sol.frame.normal.norm() - 1.0) < 1e-9)
    }
}
