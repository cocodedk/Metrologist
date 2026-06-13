package com.cocode.measureapp.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.asin

/**
 * Wiring-level tests for [MetrologyEngine.measureHybrid] — the Plan 3 hybrid path that
 * auto-selects between the rectangle and gravity plane solvers and attaches
 * [MeasurementDiagnostics].
 *
 * Scenes are built with [SyntheticScene]'s independent pinhole model. The camera-frame
 * gravity for a pose is `gravityCam = R * worldDown`, `worldDown = (0, 1, 0)` in world.
 */
class MetrologyEngineHybridTest {
    private val w = 3.0
    private val h = 2.0
    private val l = 1.0
    private val k = CameraIntrinsics(fx = 1500.0, fy = 1500.0, cx = 960.0, cy = 540.0)
    private val t = Vec3(0.0, 0.0, 6.0)

    /** Camera-frame gravity for a pose: world down `(0,1,0)` rotated into the camera. */
    private fun gravityCam(r: Mat3): Vec3 = r * Vec3(0.0, 1.0, 0.0)

    /** Independent re-derivation of the contract's cameraTiltDeg from a gravity vector. */
    private fun expectedTiltDeg(gravity: Vec3): Double {
        val worldUp = (gravity * -1.0).normalized()
        return Math.toDegrees(asin(Vec3(0.0, 0.0, 1.0).dot(worldUp).coerceIn(-1.0, 1.0)))
    }

    @Test fun hybridSelectsRectangleAndPopulatesDiagnostics() {
        val r = SceneRotations.yawPitch(yawDeg = 25.0, pitchDeg = 20.0)
        val scene = SyntheticScene(w = w, h = h, r = r, t = t, k = k, l = l)
        val gravity = gravityCam(r)

        val result = MetrologyEngine.measureHybrid(
            scene.cornerPixels, scene.stickPixels, scene.k, scene.profile,
            gravity, SurfaceOrientation.VERTICAL,
        )

        // The well-conditioned oblique quad wins the selector.
        assertEquals(SolverKind.RECTANGLE, result.solution.solver)
        // Plausible non-zero measurement (rigorous accuracy lives in the oracle test).
        assertTrue("width > 0", result.measurement.width > 0.0)
        assertTrue("height > 0", result.measurement.height > 0.0)
        assertTrue("area > 0", result.measurement.area > 0.0)
        assertTrue("confidence in (0,1]", result.confidence > 0.0 && result.confidence <= 1.0)

        val diag = result.diagnostics
        assertNotNull("diagnostics populated", diag)
        diag!!
        assertEquals(SolverKind.RECTANGLE, diag.solver)
        assertEquals(result.confidence, diag.confidence, 1e-12)
        assertEquals(result.scale.scale, diag.scale, 1e-12)
        assertEquals(result.scale.agreement, diag.scaleAgreement, 1e-12)
        assertEquals(expectedTiltDeg(gravity), diag.cameraTiltDeg, 1e-9)
    }

    @Test fun levelGravityGivesZeroCameraTilt() {
        // Near-fronto-parallel pure yaw: rectangle returns null, so the level-camera
        // VERTICAL gravity solution (confidence 1.0) is selected and still measures.
        val r = SceneRotations.yawPitch(yawDeg = 1.5, pitchDeg = 0.0)
        val scene = SyntheticScene(w = w, h = h, r = r, t = t, k = k, l = l)
        // Level camera looking along z: world down is exactly camera down.
        val gravity = Vec3(0.0, 1.0, 0.0)

        assertNull("rectangle must be null here", RectangleSolver.solve(scene.cornerPixels, scene.k))

        val result = MetrologyEngine.measureHybrid(
            scene.cornerPixels, scene.stickPixels, scene.k, scene.profile,
            gravity, SurfaceOrientation.VERTICAL,
        )

        assertEquals(SolverKind.GRAVITY, result.solution.solver)
        assertTrue("finite width", result.measurement.width.isFinite() && result.measurement.width > 0.0)
        assertTrue("finite area", result.measurement.area.isFinite())
        val diag = result.diagnostics!!
        assertEquals(SolverKind.GRAVITY, diag.solver)
        // Level camera -> optical axis perpendicular to world up -> zero pitch.
        assertEquals(0.0, diag.cameraTiltDeg, 1e-9)
    }

    @Test fun zeroConfidenceSelectionReturnsZeroedResultWithDiagnostics() {
        // Pure yaw -> rectangle null; HORIZONTAL + level gravity -> gravity confidence 0.0
        // (optical axis lies in the floor). Selector falls through to that zero solution.
        val r = SceneRotations.yawPitch(yawDeg = 1.5, pitchDeg = 0.0)
        val scene = SyntheticScene(w = w, h = h, r = r, t = t, k = k, l = l)
        val gravity = Vec3(0.0, 1.0, 0.0)

        val result = MetrologyEngine.measureHybrid(
            scene.cornerPixels, scene.stickPixels, scene.k, scene.profile,
            gravity, SurfaceOrientation.HORIZONTAL,
        )

        assertEquals(0.0, result.confidence, 0.0)
        assertEquals(0.0, result.measurement.width, 0.0)
        assertEquals(0.0, result.measurement.height, 0.0)
        assertEquals(0.0, result.measurement.area, 0.0)
        assertEquals(0.0, result.measurement.diagonal, 0.0)
        assertTrue(result.measurement.cornerAngles.isEmpty())
        assertEquals(0.0, result.scale.scale, 0.0)
        assertEquals(0.0, result.scale.agreement, 0.0)

        val diag = result.diagnostics!!
        assertEquals(0.0, diag.confidence, 0.0)
        assertEquals(0.0, diag.scale, 0.0)
        assertEquals(0.0, diag.scaleAgreement, 0.0)
        assertEquals(expectedTiltDeg(gravity), diag.cameraTiltDeg, 1e-9)
    }

    @Test fun diagnosticsGettersReturnConstructedValues() {
        val d = MeasurementDiagnostics(
            solver = SolverKind.GRAVITY,
            confidence = 0.42,
            cameraTiltDeg = 12.5,
            scale = 3.0,
            scaleAgreement = 0.1,
        )
        assertEquals(SolverKind.GRAVITY, d.solver)
        assertEquals(0.42, d.confidence, 0.0)
        assertEquals(12.5, d.cameraTiltDeg, 0.0)
        assertEquals(3.0, d.scale, 0.0)
        assertEquals(0.1, d.scaleAgreement, 0.0)
    }
}
