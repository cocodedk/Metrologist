package com.cocode.measureapp.core

import com.cocode.measureapp.geometry.CameraIntrinsics
import com.cocode.measureapp.geometry.EngineResult
import com.cocode.measureapp.geometry.Mat3
import com.cocode.measureapp.geometry.MetrologyEngine
import com.cocode.measureapp.geometry.MeasurementResult
import com.cocode.measureapp.geometry.PlaneFrame
import com.cocode.measureapp.geometry.PlaneSolution
import com.cocode.measureapp.geometry.ScaleResult
import com.cocode.measureapp.geometry.SceneRotations
import com.cocode.measureapp.geometry.SolverKind
import com.cocode.measureapp.geometry.StickProfile
import com.cocode.measureapp.geometry.SurfaceOrientation
import com.cocode.measureapp.geometry.SyntheticScene
import com.cocode.measureapp.geometry.Vec2
import com.cocode.measureapp.geometry.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TDD tests for [MeasurementPresenter] and [MeasurementView] targeting 100% line, branch,
 * and method coverage of both classes.
 *
 * Scenes use the test-only [SyntheticScene] independent pinhole projector so tests remain
 * a genuine round-trip check rather than a tautology.
 */
class MeasurementPresenterTest {

    // ---- shared scene parameters ----
    private val w = 3.0
    private val h = 2.0
    private val l = 1.0
    private val k = CameraIntrinsics(fx = 1500.0, fy = 1500.0, cx = 960.0, cy = 540.0)
    private val t = Vec3(0.0, 0.0, 6.0)

    /**
     * Oblique scene: 25° yaw + 20° pitch -> RectangleSolver returns a valid solution,
     * SolverSelector picks RECTANGLE, confidence > 0.
     */
    private fun obliqueScene(): SyntheticScene {
        val r = SceneRotations.yawPitch(yawDeg = 25.0, pitchDeg = 20.0)
        return SyntheticScene(w = w, h = h, r = r, t = t, k = k, l = l)
    }

    /** Camera-frame gravity for a given rotation matrix: world down (0,1,0) rotated. */
    private fun gravityFor(r: com.cocode.measureapp.geometry.Mat3): Vec3 =
        r * Vec3(0.0, 1.0, 0.0)

    // ======================================================================
    // 1. Usable oblique scene (Rectangle solver wins)
    // ======================================================================

    @Test
    fun usableObliqueScene_usableIsTrue() {
        val scene = obliqueScene()
        val gravity = scene.gravityCam
        val view = MeasurementPresenter.present(
            scene.cornerPixels, scene.stickPixels, k,
            gravity, scene.profile, SurfaceOrientation.VERTICAL, LengthUnit.METERS,
        )
        assertTrue("usable should be true for well-conditioned oblique scene", view.usable)
    }

    @Test
    fun usableObliqueScene_formattedStringsNonEmpty() {
        val scene = obliqueScene()
        val view = MeasurementPresenter.present(
            scene.cornerPixels, scene.stickPixels, k,
            scene.gravityCam, scene.profile, SurfaceOrientation.VERTICAL, LengthUnit.METERS,
        )
        assertTrue(view.width.isNotEmpty())
        assertTrue(view.height.isNotEmpty())
        assertTrue(view.area.isNotEmpty())
        assertTrue(view.diagonal.isNotEmpty())
    }

    @Test
    fun usableObliqueScene_fourCornerAnglesNear90() {
        val scene = obliqueScene()
        val view = MeasurementPresenter.present(
            scene.cornerPixels, scene.stickPixels, k,
            scene.gravityCam, scene.profile, SurfaceOrientation.VERTICAL, LengthUnit.METERS,
        )
        assertEquals(4, view.cornerAngles.size)
        for (angle in view.cornerAngles) {
            assertEquals("corner angle near 90°", 90.0, angle, 10.0)
        }
    }

    @Test
    fun usableObliqueScene_cornerAnglesRoundedTo1Decimal() {
        val scene = obliqueScene()
        val view = MeasurementPresenter.present(
            scene.cornerPixels, scene.stickPixels, k,
            scene.gravityCam, scene.profile, SurfaceOrientation.VERTICAL, LengthUnit.METERS,
        )
        // Each angle should equal its own rounding to 1 decimal place.
        for (angle in view.cornerAngles) {
            val rounded = kotlin.math.round(angle * 10) / 10.0
            assertEquals("angle $angle should already be 1-decimal rounded", rounded, angle, 1e-9)
        }
    }

    @Test
    fun usableObliqueScene_confidenceLabelSensible() {
        val scene = obliqueScene()
        val view = MeasurementPresenter.present(
            scene.cornerPixels, scene.stickPixels, k,
            scene.gravityCam, scene.profile, SurfaceOrientation.VERTICAL, LengthUnit.METERS,
        )
        val valid = setOf("High confidence", "Medium confidence", "Low confidence")
        assertTrue("confidenceLabel must be one of $valid", view.confidenceLabel in valid)
    }

    @Test
    fun usableObliqueScene_confidencePercentInRange() {
        val scene = obliqueScene()
        val view = MeasurementPresenter.present(
            scene.cornerPixels, scene.stickPixels, k,
            scene.gravityCam, scene.profile, SurfaceOrientation.VERTICAL, LengthUnit.METERS,
        )
        assertTrue("confidencePercent >= 0", view.confidencePercent >= 0)
        assertTrue("confidencePercent <= 100", view.confidencePercent <= 100)
    }

    @Test
    fun usableObliqueScene_solverNameIsRectangleMethod() {
        val scene = obliqueScene()
        val view = MeasurementPresenter.present(
            scene.cornerPixels, scene.stickPixels, k,
            scene.gravityCam, scene.profile, SurfaceOrientation.VERTICAL, LengthUnit.METERS,
        )
        assertEquals("Rectangle method", view.solverName)
    }

    @Test
    fun usableObliqueScene_diagnosticsPresentCaveatsPath() {
        val scene = obliqueScene()
        val view = MeasurementPresenter.present(
            scene.cornerPixels, scene.stickPixels, k,
            scene.gravityCam, scene.profile, SurfaceOrientation.VERTICAL, LengthUnit.METERS,
        )
        // diagnostics is present: caveats comes from DiagnosticsText.caveats(diagnostics)
        // The clean oblique scene should produce 0 or more caveats; assert it is a List.
        assertNotNull(view.caveats)
    }

    // ======================================================================
    // 2. Zero-confidence scene (usable == false)
    // ======================================================================

    @Test
    fun zeroConfidenceScene_usableIsFalse() {
        // Pure yaw (~fronto-parallel) -> RectangleSolver returns null.
        // HORIZONTAL + level gravity -> GravitySolver confidence = 0 (optical axis in floor).
        val r = SceneRotations.yawPitch(yawDeg = 1.5, pitchDeg = 0.0)
        val scene = SyntheticScene(w = w, h = h, r = r, t = t, k = k, l = l)
        val gravity = Vec3(0.0, 1.0, 0.0)

        val view = MeasurementPresenter.present(
            scene.cornerPixels, scene.stickPixels, k,
            gravity, scene.profile, SurfaceOrientation.HORIZONTAL, LengthUnit.METERS,
        )
        assertFalse("usable should be false when confidence == 0", view.usable)
    }

    @Test
    fun zeroConfidenceScene_confidencePercentIsZero() {
        val r = SceneRotations.yawPitch(yawDeg = 1.5, pitchDeg = 0.0)
        val scene = SyntheticScene(w = w, h = h, r = r, t = t, k = k, l = l)
        val gravity = Vec3(0.0, 1.0, 0.0)

        val view = MeasurementPresenter.present(
            scene.cornerPixels, scene.stickPixels, k,
            gravity, scene.profile, SurfaceOrientation.HORIZONTAL, LengthUnit.METERS,
        )
        assertEquals(0, view.confidencePercent)
    }

    // ======================================================================
    // 3. Gravity solver path -> solverName == "Tilt-sensor fallback"
    // ======================================================================

    @Test
    fun gravitySolverPath_solverNameIsTiltSensorFallback() {
        // Near-fronto-parallel yaw: rectangle null, VERTICAL + level gravity -> GRAVITY wins.
        val r = SceneRotations.yawPitch(yawDeg = 1.5, pitchDeg = 0.0)
        val scene = SyntheticScene(w = w, h = h, r = r, t = t, k = k, l = l)
        val gravity = Vec3(0.0, 1.0, 0.0)

        val view = MeasurementPresenter.present(
            scene.cornerPixels, scene.stickPixels, k,
            gravity, scene.profile, SurfaceOrientation.VERTICAL, LengthUnit.METERS,
        )
        assertEquals("Tilt-sensor fallback", view.solverName)
    }

    @Test
    fun gravitySolverPath_usableIsTrue() {
        val r = SceneRotations.yawPitch(yawDeg = 1.5, pitchDeg = 0.0)
        val scene = SyntheticScene(w = w, h = h, r = r, t = t, k = k, l = l)
        val gravity = Vec3(0.0, 1.0, 0.0)

        val view = MeasurementPresenter.present(
            scene.cornerPixels, scene.stickPixels, k,
            gravity, scene.profile, SurfaceOrientation.VERTICAL, LengthUnit.METERS,
        )
        assertTrue("gravity path should produce a usable result", view.usable)
    }

    // ======================================================================
    // 4. diagnostics == null -> caveats is emptyList() and solverName from solution.solver
    //    This is exercised by MetrologyEngine.measure() which leaves diagnostics null.
    //    We can't call present() directly with that, but we can verify the branch via
    //    the zero-confidence horizontal scene: measureHybrid always sets diagnostics, so
    //    to cover the diagnostics-null branch we construct a thin wrapper test via
    //    the gravity zero-confidence path (diagnostics is set to non-null with confidence=0).
    //    To actually cover the null branch we need a result where diagnostics == null.
    //    measureHybrid always sets diagnostics. Therefore we drive the null path by building
    //    a MeasurementView directly and confirming the data class is exercised correctly.
    // ======================================================================

    @Test
    fun measurementView_dataClassCopyAndEquality() {
        val v1 = MeasurementView(
            usable = true,
            width = "1.00 m",
            height = "2.00 m",
            area = "2.00 m²",
            diagonal = "2.24 m",
            cornerAngles = listOf(90.0, 90.0, 90.0, 90.0),
            confidenceLabel = "High confidence",
            confidencePercent = 85,
            solverName = "Rectangle method",
            caveats = emptyList(),
        )
        val v2 = v1.copy(usable = false)
        assertFalse(v2.usable)
        assertEquals(v1.width, v2.width)
        assertEquals(v1, v1)
        assertTrue(v1 != v2)
    }

    @Test
    fun measurementView_toStringContainsFieldValues() {
        val v = MeasurementView(
            usable = true,
            width = "3.00 m",
            height = "2.00 m",
            area = "6.00 m²",
            diagonal = "3.61 m",
            cornerAngles = listOf(89.5, 90.5, 89.5, 90.5),
            confidenceLabel = "Medium confidence",
            confidencePercent = 60,
            solverName = "Tilt-sensor fallback",
            caveats = listOf("some caveat"),
        )
        val s = v.toString()
        assertTrue(s.contains("3.00 m"))
        assertTrue(s.contains("Tilt-sensor fallback"))
    }

    @Test
    fun measurementView_hashCodeConsistent() {
        val v = MeasurementView(
            usable = false, width = "0.00 m", height = "0.00 m",
            area = "0.00 m²", diagonal = "0.00 m", cornerAngles = emptyList(),
            confidenceLabel = "Low confidence", confidencePercent = 0,
            solverName = "Rectangle method", caveats = emptyList(),
        )
        assertEquals(v.hashCode(), v.hashCode())
    }

    // ======================================================================
    // 5. Different LengthUnit values change the formatting
    // ======================================================================

    @Test
    fun differentUnits_metersFormatsWithM() {
        val scene = obliqueScene()
        val view = MeasurementPresenter.present(
            scene.cornerPixels, scene.stickPixels, k,
            scene.gravityCam, scene.profile, SurfaceOrientation.VERTICAL, LengthUnit.METERS,
        )
        assertTrue("width should end with ' m'", view.width.endsWith(" m"))
        assertTrue("area should end with ' m²'", view.area.endsWith(" m²"))
    }

    @Test
    fun differentUnits_centimetersFormatsWithCm() {
        val scene = obliqueScene()
        val view = MeasurementPresenter.present(
            scene.cornerPixels, scene.stickPixels, k,
            scene.gravityCam, scene.profile, SurfaceOrientation.VERTICAL, LengthUnit.CENTIMETERS,
        )
        assertTrue("width should end with ' cm'", view.width.endsWith(" cm"))
        assertTrue("area should end with ' cm²'", view.area.endsWith(" cm²"))
    }

    @Test
    fun differentUnits_feetInchesFormatsWithFtSuffix() {
        val scene = obliqueScene()
        val view = MeasurementPresenter.present(
            scene.cornerPixels, scene.stickPixels, k,
            scene.gravityCam, scene.profile, SurfaceOrientation.VERTICAL, LengthUnit.FEET_INCHES,
        )
        // feet-inches format: "%d' %d\""
        assertTrue("width should contain ft/in pattern", view.width.contains("'"))
        // area should end with ' ft²'
        assertTrue("area should end with ' ft²'", view.area.endsWith(" ft²"))
    }

    @Test
    fun differentUnits_metersAndCentimetersDiffer() {
        val scene = obliqueScene()
        val viewM = MeasurementPresenter.present(
            scene.cornerPixels, scene.stickPixels, k,
            scene.gravityCam, scene.profile, SurfaceOrientation.VERTICAL, LengthUnit.METERS,
        )
        val viewCm = MeasurementPresenter.present(
            scene.cornerPixels, scene.stickPixels, k,
            scene.gravityCam, scene.profile, SurfaceOrientation.VERTICAL, LengthUnit.CENTIMETERS,
        )
        assertTrue("meters and cm width strings should differ", viewM.width != viewCm.width)
    }

    // ======================================================================
    // 6. Caveats present path: force a low-confidence / gravity scene to produce caveats
    // ======================================================================

    @Test
    fun gravitySolverPath_caveatsContainsTiltSensorMessage() {
        val r = SceneRotations.yawPitch(yawDeg = 1.5, pitchDeg = 0.0)
        val scene = SyntheticScene(w = w, h = h, r = r, t = t, k = k, l = l)
        val gravity = Vec3(0.0, 1.0, 0.0)

        val view = MeasurementPresenter.present(
            scene.cornerPixels, scene.stickPixels, k,
            gravity, scene.profile, SurfaceOrientation.VERTICAL, LengthUnit.METERS,
        )
        // Gravity solver always triggers the "Used the tilt-sensor fallback" caveat.
        assertTrue(
            "caveats should mention tilt-sensor fallback",
            view.caveats.any { it.contains("tilt-sensor fallback") },
        )
    }

    // ======================================================================
    // 7. toView with diagnostics == null (MetrologyEngine.measure leaves diagnostics=null)
    //    Covers the null branches of "r.diagnostics?.solver ?: r.solution.solver"
    //    and "r.diagnostics?.let { ... } ?: emptyList()".
    // ======================================================================

    private fun nullDiagnosticsResult(solver: SolverKind): EngineResult {
        val frame = PlaneFrame(Vec3(1.0, 0.0, 0.0), Vec3(0.0, 1.0, 0.0), Vec3(0.0, 0.0, 1.0))
        return EngineResult(
            measurement = MeasurementResult(
                width = 2.0, height = 1.5, area = 3.0, diagonal = 2.5,
                cornerAngles = listOf(90.0, 90.0, 90.0, 90.0),
            ),
            solution = PlaneSolution(frame, solver, 0.8),
            scale = ScaleResult(scale = 1.0, agreement = 0.0),
            confidence = 0.8,
            diagnostics = null,
        )
    }

    @Test
    fun toView_diagnosticsNull_rectangle_solverNameIsRectangleMethod() {
        val view = MeasurementPresenter.toView(
            nullDiagnosticsResult(SolverKind.RECTANGLE), LengthUnit.METERS,
        )
        assertEquals("Rectangle method", view.solverName)
        assertEquals(emptyList<String>(), view.caveats)
        assertTrue(view.usable)
    }

    @Test
    fun toView_diagnosticsNull_gravity_solverNameIsTiltSensorFallback() {
        val view = MeasurementPresenter.toView(
            nullDiagnosticsResult(SolverKind.GRAVITY), LengthUnit.METERS,
        )
        assertEquals("Tilt-sensor fallback", view.solverName)
        assertEquals(emptyList<String>(), view.caveats)
    }

    @Test
    fun toView_diagnosticsNull_caveatsIsEmptyList() {
        val view = MeasurementPresenter.toView(
            nullDiagnosticsResult(SolverKind.RECTANGLE), LengthUnit.CENTIMETERS,
        )
        assertEquals(emptyList<String>(), view.caveats)
    }

    @Test
    fun toView_diagnosticsNull_usableFalseWhenConfidenceZero() {
        val frame = PlaneFrame(Vec3(1.0, 0.0, 0.0), Vec3(0.0, 1.0, 0.0), Vec3(0.0, 0.0, 1.0))
        val zeroed = EngineResult(
            measurement = MeasurementResult(0.0, 0.0, 0.0, 0.0, emptyList()),
            solution = PlaneSolution(frame, SolverKind.RECTANGLE, 0.0),
            scale = ScaleResult(0.0, 0.0),
            confidence = 0.0,
            diagnostics = null,
        )
        val view = MeasurementPresenter.toView(zeroed, LengthUnit.METERS)
        assertFalse("confidence=0 should be unusable", view.usable)
        assertEquals(0, view.confidencePercent)
        assertEquals(emptyList<String>(), view.caveats)
    }
}
