package com.cocode.measureapp.geometry.eligibility

import com.cocode.measureapp.core.DiagnosticsText
import com.cocode.measureapp.core.LengthUnit
import com.cocode.measureapp.core.MeasurementSession
import com.cocode.measureapp.core.Units
import com.cocode.measureapp.geometry.ContractScenes
import com.cocode.measureapp.geometry.MeasurementFailureReason
import com.cocode.measureapp.geometry.MeasurementOutcome
import com.cocode.measureapp.geometry.MetrologyEngine
import com.cocode.measureapp.geometry.SceneRotations
import com.cocode.measureapp.geometry.SolverKind
import com.cocode.measureapp.geometry.SurfaceOrientation
import com.cocode.measureapp.geometry.Vec2
import com.cocode.measureapp.geometry.Vec3
import com.cocode.measureapp.geometry.eligibility.EligibilityFixtures as F
import com.cocode.measureapp.geometry.frames.CalibrationProvenance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue

/** Contract C10 outcomes: engine -> revision-bound session -> formatting eligibility. */
object OutcomeCases {
    private val oblique = ContractScenes.wall(SceneRotations.yawPitch(25.0, 20.0))
    private val frontal = ContractScenes.wall(SceneRotations.yawPitch(0.0, 0.0))

    /** Eligible rectangle, gravity surface, both ineligible, missing gravity, projection singularity. */
    fun engineToSessionEdge() {
        val cases = listOf(
            "eligible rectangle" to F.scene(oblique, F.available(oblique.gravityCam)),
            "gravity surface" to F.input(F.nonRectCorners, F.nonRectStick, F.available(F.STRAIGHT_DOWN), SurfaceOrientation.HORIZONTAL),
            "both ineligible" to F.input(F.nonRectCorners, F.nonRectStick, F.available(F.STRAIGHT_DOWN), SurfaceOrientation.VERTICAL),
            "missing gravity" to F.input(F.nonRectCorners, F.nonRectStick, F.MISSING, SurfaceOrientation.VERTICAL),
            "projection singularity" to F.scene(frontal, F.available(F.LEVEL), SurfaceOrientation.HORIZONTAL),
        )
        val expected = mapOf(
            "eligible rectangle" to SolverKind.RECTANGLE, "gravity surface" to SolverKind.GRAVITY,
            "both ineligible" to MeasurementFailureReason.UNSUPPORTED_GEOMETRY,
            "missing gravity" to MeasurementFailureReason.METADATA_UNAVAILABLE,
            "projection singularity" to MeasurementFailureReason.UNSUPPORTED_GEOMETRY,
        )
        for ((label, input) in cases) {
            val outcome = MetrologyEngine.evaluate(
                input.corners, input.stick, input.intrinsics, input.calibration, input.gravity,
                input.profile, input.orientation, F.REVISION,
            )
            val session = MeasurementSession(revision = F.REVISION).complete(F.REVISION, outcome)
            assertSame(label, outcome, session.lastOutcome)
            // A late completion for an older revision can never replace the current state.
            assertNull(label, MeasurementSession(revision = F.REVISION + 1).complete(F.REVISION, outcome).lastOutcome)
            when (outcome) {
                is MeasurementOutcome.Success -> {
                    F.success(outcome, label)
                    assertEquals(label, expected[label], outcome.solver)
                    assertTrue(label, session.exportEnabled)
                    val m = session.usableResult!!.measurement
                    assertFalse(Units.formatLength(m.width, LengthUnit.METERS).isBlank())
                    assertFalse(Units.formatArea(m.area, LengthUnit.METERS).isBlank())
                    DiagnosticsText.caveats(outcome.diagnostics!!)
                }
                is MeasurementOutcome.Failure -> {
                    F.failure(outcome, label)
                    assertEquals(label, expected[label], outcome.reason)
                    assertFalse(label, session.exportEnabled)
                    assertNull(label, session.usableResult)
                    assertSame(label, outcome, session.failure)
                }
            }
        }
    }

    /** Both-candidate failure keeps each method's actionable explanation. */
    fun bothIneligibleExplainsEachMethod() {
        val f = F.failure(F.evaluate(F.scene(frontal, F.available(F.LEVEL), SurfaceOrientation.HORIZONTAL)), "singular")
        val detail = f.detail!!
        assertTrue(detail, detail.contains("Rectangle method") && detail.contains("not a floor/table"))
        assertTrue(detail, detail.contains("Tilt-sensor method") && detail.contains("both sides of the plane"))
        val down = F.failure(
            F.evaluate(F.input(F.nonRectCorners, F.nonRectStick, F.available(F.STRAIGHT_DOWN), SurfaceOrientation.VERTICAL)),
            "straight-down wall",
        )
        assertTrue(down.detail!!, down.detail!!.contains("straight up or down"))
    }

    /** Invalid inputs become structured failures, never exceptions or zero measurements. */
    fun invalidInputsAreFailures() {
        val g = F.available(oblique.gravityCam)
        val nan = oblique.cornerPixels.dropLast(1) + Vec2(Double.NaN, 1.0)
        assertEquals(MeasurementFailureReason.INVALID_OBJECT_CORNERS, F.failure(F.evaluate(F.scene(oblique, g, corners = nan)), "nan").reason)
        val collapsed = List(4) { oblique.stickPixels[0] }
        val stickFail = F.evaluate(F.input(oblique.cornerPixels, collapsed, g, SurfaceOrientation.VERTICAL, k = oblique.k))
        assertEquals(MeasurementFailureReason.INVALID_STICK_CORNERS, F.failure(stickFail, "stick").reason)
        val badK = oblique.k.copy(fx = 0.0)
        assertEquals(MeasurementFailureReason.METADATA_UNAVAILABLE, F.failure(F.evaluate(F.scene(oblique, g, k = badK)), "k").reason)
        // A zero "gravity" vector is not a reading: the rectangle stays usable but unverified.
        val zero = F.success(F.evaluate(F.scene(oblique, F.available(Vec3(0.0, 0.0, 0.0)))), "zero gravity")
        assertEquals(SurfaceConsistency.UNVERIFIED_NO_GRAVITY, zero.diagnostics!!.surfaceConsistency)
        assertTrue(zero.diagnostics!!.cameraTiltDeg.isNaN())
    }

    /** Calibration provenance survives success and unavailable calibration is capped and visible. */
    fun provenanceStaysVisible() {
        val i = F.scene(oblique, F.available(oblique.gravityCam), calibration = CalibrationProvenance.UNAVAILABLE)
        val ok = F.success(F.evaluate(i), "uncalibrated")
        assertEquals(CalibrationProvenance.UNAVAILABLE, ok.diagnostics!!.calibration)
        assertTrue(ok.confidence <= EligibilityTolerances.UNAVAILABLE_CALIBRATION_CAP)
        assertTrue(DiagnosticsText.caveats(ok.diagnostics!!).any { it.contains("unavailable") })
        val exact = F.success(F.evaluate(i.copy(calibration = CalibrationProvenance.CALIBRATED)), "calibrated")
        assertTrue("calibrated ${exact.confidence} > uncalibrated ${ok.confidence}", exact.confidence > ok.confidence)
        // The legacy adapter reports the same engine decision in EngineResult form.
        val legacy = MetrologyEngine.measureHybrid(
            oblique.cornerPixels, oblique.stickPixels, oblique.k, oblique.profile, oblique.gravityCam,
            SurfaceOrientation.VERTICAL, CalibrationProvenance.CALIBRATED,
        )
        assertEquals(exact.measurement, legacy.measurement)
        assertEquals(exact.confidence, legacy.confidence, 0.0)
    }
}
