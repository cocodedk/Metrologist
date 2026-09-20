package com.cocode.measureapp.core

import com.cocode.measureapp.geometry.CameraIntrinsics
import com.cocode.measureapp.geometry.EngineResult
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
 * Tests for [MeasurementPresenter] and [MeasurementView] over the legacy engine adapter.
 * Scenes use independent pinhole projection, so each is a genuine round-trip check.
 */
class MeasurementPresenterTest {
    private val k = CameraIntrinsics(fx = 1500.0, fy = 1500.0, cx = 960.0, cy = 540.0)
    private val t = Vec3(0.0, 0.0, 6.0)
    private val level = Vec3(0.0, 1.0, 0.0)

    /** 25° yaw + 20° pitch: the rectangle candidate is eligible and wins. */
    private val oblique = SyntheticScene(w = 3.0, h = 2.0, r = SceneRotations.yawPitch(25.0, 20.0), t = t, k = k, l = 1.0)

    /** Near-frontal 1.5° yaw wall, used with a floor selection that no plane can satisfy. */
    private val nearFrontal = SyntheticScene(w = 3.0, h = 2.0, r = SceneRotations.yawPitch(1.5, 0.0), t = t, k = k, l = 1.0)

    private fun presentOblique(unit: LengthUnit = LengthUnit.METERS) = MeasurementPresenter.present(
        oblique.cornerPixels, oblique.stickPixels, k, oblique.gravityCam, oblique.profile, SurfaceOrientation.VERTICAL, unit,
    )

    private fun presentUnsupported() = MeasurementPresenter.present(
        nearFrontal.cornerPixels, nearFrontal.stickPixels, k, level, nearFrontal.profile,
        SurfaceOrientation.HORIZONTAL, LengthUnit.METERS,
    )

    /** Head-on wall 6 m away: pixel of plane point `(x, y)`. */
    private fun headOn(x: Double, y: Double) = Vec2(k.fx * x / 6.0 + k.cx, k.fy * y / 6.0 + k.cy)

    /**
     * A slanted parallelogram on a frontal wall with a level phone: its edges are far from
     * perpendicular in 3D, so the rectangle is ineligible and the tilt-sensor wall plane is used.
     */
    private fun presentGravityWall() = MeasurementPresenter.present(
        listOf(headOn(-1.0, -0.5), headOn(1.0, -0.5), headOn(1.4, 0.5), headOn(-0.6, 0.5)),
        listOf(headOn(-0.5, 0.76), headOn(0.5, 0.76), headOn(0.5, 0.84), headOn(-0.5, 0.84)),
        k, level, StickProfile(1.0, width = 0.08), SurfaceOrientation.VERTICAL, LengthUnit.METERS,
    )

    @Test fun usableObliqueScene_usableIsTrue() = assertTrue(presentOblique().usable)

    @Test fun usableObliqueScene_formattedStringsNonEmpty() {
        val v = presentOblique()
        for (s in listOf(v.width, v.height, v.area, v.diagonal)) assertTrue(s.isNotEmpty())
    }

    @Test fun usableObliqueScene_fourCornerAnglesNear90() {
        val v = presentOblique()
        assertEquals(4, v.cornerAngles.size)
        for (angle in v.cornerAngles) assertEquals("corner angle near 90°", 90.0, angle, 10.0)
    }

    @Test fun usableObliqueScene_cornerAnglesRoundedTo1Decimal() {
        for (angle in presentOblique().cornerAngles) {
            assertEquals("angle $angle should already be 1-decimal rounded", kotlin.math.round(angle * 10) / 10.0, angle, 1e-9)
        }
    }

    @Test fun usableObliqueScene_confidenceLabelSensible() {
        val valid = setOf("High confidence", "Medium confidence", "Low confidence")
        assertTrue(presentOblique().confidenceLabel in valid)
    }

    @Test fun usableObliqueScene_confidencePercentInRange() {
        val p = presentOblique().confidencePercent
        assertTrue("confidencePercent in 1..100, was $p", p in 1..100)
    }

    @Test fun usableObliqueScene_solverNameIsRectangleMethod() =
        assertEquals("Rectangle method", presentOblique().solverName)

    @Test fun usableObliqueScene_uncalibratedLegacyInputStaysVisible() {
        // The legacy request carries no calibration provenance, so it must never read as exact.
        val v = presentOblique()
        assertNotNull(v.caveats)
        assertTrue(v.caveats.any { it.contains("calibration is unavailable") })
        assertEquals("Low confidence", v.confidenceLabel)
    }

    @Test fun zeroConfidenceScene_usableIsFalse() = assertFalse(presentUnsupported().usable)

    @Test fun zeroConfidenceScene_confidencePercentIsZero() = assertEquals(0, presentUnsupported().confidencePercent)

    @Test fun gravitySolverPath_solverNameIsTiltSensorFallback() =
        assertEquals("Tilt-sensor fallback", presentGravityWall().solverName)

    @Test fun gravitySolverPath_usableIsTrue() = assertTrue(presentGravityWall().usable)

    @Test fun gravitySolverPath_caveatsContainsTiltSensorMessage() {
        val caveats = presentGravityWall().caveats
        assertTrue(caveats.any { it.contains("tilt-sensor fallback") })
        assertTrue("assumed wall azimuth is visible", caveats.any { it.contains("faces the camera") })
    }

    @Test fun gravitySolverPath_keepsTheGenuineNonRectangularAngles() {
        // The slanted corner is atan(1 / 0.4) + 90 = 111.8 degrees, not squared off.
        assertEquals(111.8, presentGravityWall().cornerAngles[1], 0.05)
    }

    private fun view(usable: Boolean = true, width: String = "1.00 m", caveats: List<String> = emptyList()) = MeasurementView(
        usable = usable, width = width, height = "2.00 m", area = "2.00 m²", diagonal = "2.24 m",
        cornerAngles = listOf(90.0, 90.0, 90.0, 90.0), confidenceLabel = "High confidence",
        confidencePercent = 85, solverName = "Tilt-sensor fallback", caveats = caveats,
    )

    @Test fun measurementView_dataClassCopyAndEquality() {
        val v1 = view()
        val v2 = v1.copy(usable = false)
        assertFalse(v2.usable)
        assertEquals(v1.width, v2.width)
        assertEquals(v1, v1)
        assertTrue(v1 != v2)
    }

    @Test fun measurementView_toStringContainsFieldValues() {
        val s = view(width = "3.00 m", caveats = listOf("some caveat")).toString()
        assertTrue(s.contains("3.00 m"))
        assertTrue(s.contains("Tilt-sensor fallback"))
    }

    @Test fun measurementView_hashCodeConsistent() {
        val v = view(usable = false)
        assertEquals(v.hashCode(), v.hashCode())
    }

    @Test fun differentUnits_metersFormatsWithM() {
        val v = presentOblique(LengthUnit.METERS)
        assertTrue(v.width.endsWith(" m"))
        assertTrue(v.area.endsWith(" m²"))
    }

    @Test fun differentUnits_centimetersFormatsWithCm() {
        val v = presentOblique(LengthUnit.CENTIMETERS)
        assertTrue(v.width.endsWith(" cm"))
        assertTrue(v.area.endsWith(" cm²"))
    }

    @Test fun differentUnits_feetInchesFormatsWithFtSuffix() {
        val v = presentOblique(LengthUnit.FEET_INCHES)
        assertTrue(v.width.contains("'"))
        assertTrue(v.area.endsWith(" ft²"))
    }

    @Test fun differentUnits_metersAndCentimetersDiffer() =
        assertTrue(presentOblique(LengthUnit.METERS).width != presentOblique(LengthUnit.CENTIMETERS).width)

    /** toView with diagnostics == null covers the fallback branches. */
    private fun nullDiagnostics(solver: SolverKind, confidence: Double = 0.8, m: MeasurementResult? = null): EngineResult {
        val frame = PlaneFrame(Vec3(1.0, 0.0, 0.0), Vec3(0.0, 1.0, 0.0), Vec3(0.0, 0.0, 1.0))
        val measurement = m ?: MeasurementResult(2.0, 1.5, 3.0, 2.5, listOf(90.0, 90.0, 90.0, 90.0))
        return EngineResult(measurement, PlaneSolution(frame, solver, confidence), ScaleResult(1.0, 0.0), confidence, null)
    }

    @Test fun toView_diagnosticsNull_rectangle_solverNameIsRectangleMethod() {
        val v = MeasurementPresenter.toView(nullDiagnostics(SolverKind.RECTANGLE), LengthUnit.METERS)
        assertEquals("Rectangle method", v.solverName)
        assertEquals(emptyList<String>(), v.caveats)
        assertTrue(v.usable)
    }

    @Test fun toView_diagnosticsNull_gravity_solverNameIsTiltSensorFallback() {
        val v = MeasurementPresenter.toView(nullDiagnostics(SolverKind.GRAVITY), LengthUnit.METERS)
        assertEquals("Tilt-sensor fallback", v.solverName)
        assertEquals(emptyList<String>(), v.caveats)
    }

    @Test fun toView_diagnosticsNull_caveatsIsEmptyList() =
        assertEquals(emptyList<String>(), MeasurementPresenter.toView(nullDiagnostics(SolverKind.RECTANGLE), LengthUnit.CENTIMETERS).caveats)

    @Test fun toView_diagnosticsNull_usableFalseWhenConfidenceZero() {
        val zeroed = nullDiagnostics(SolverKind.RECTANGLE, 0.0, MeasurementResult(0.0, 0.0, 0.0, 0.0, emptyList()))
        val v = MeasurementPresenter.toView(zeroed, LengthUnit.METERS)
        assertFalse(v.usable)
        assertEquals(0, v.confidencePercent)
        assertEquals(emptyList<String>(), v.caveats)
        assertFalse("zeroed placeholder is not shown as dimensions", v.width.any(Char::isDigit))
    }
}
