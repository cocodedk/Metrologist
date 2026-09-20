package com.cocode.measureapp.contracts

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cocode.measureapp.core.DiagnosticsText
import com.cocode.measureapp.core.measurement.CorrectionText
import com.cocode.measureapp.geometry.MeasurementFailureReason as Reason
import com.cocode.measureapp.geometry.MeasurementOutcome
import com.cocode.measureapp.geometry.SolverKind
import com.cocode.measureapp.geometry.SurfaceOrientation
import com.cocode.measureapp.geometry.Vec2
import com.cocode.measureapp.geometry.eligibility.PlaneAssumption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * User-requested regression on the rebuilt calibration page (`tools/calibration-target.html`),
 * rendered at 3 px/mm with synthetic pinhole metadata. It exercises the real marking callback,
 * engine, presenter and Results screen. It is NOT a physical-camera test.
 */
@RunWith(AndroidJUnit4::class)
class CalibrationTargetFlowTest {
    @get:Rule val rule = createComposeRule()

    /** Marks the page, measures and checks engine numbers (1e-6) and the formatted Results. */
    private fun FlowDriver.measurePage(t: TargetFixture): MeasurementOutcome.Success {
        load(t.image(), t.objectPixels, t.stickPixels)
        assertSelected(t.orientation)
        val ok = Expect.success(measure())
        assertEquals(t.orientation, s.requests.last().orientation)
        Expect.sameResult(t.expected, ok.measurement, 1e-6, t.name)
        assertResults()
        assertDisplayed(t.expected)
        assertShown(DiagnosticsText.confidenceLabel(ok.confidence))
        assertExport(true)
        return ok
    }

    /** Re-marking and a surface change both drop the result; nothing stale can be shown again. */
    private fun FlowDriver.staleResultsCannotSurvive(t: TargetFixture) {
        val img = s.captured
        clickResults("Re-mark")
        assertMarking()
        val moved = t.objectPixels.mapIndexed { i, p -> if (i == 2) Vec2(p.x - 25.0, p.y - 15.0) else p }
        ui { place(moved, t.stickPixels) }
        assertNoUsableResult()
        ui { step = HostStep.Results }
        assertMarking()
        val remarked = Expect.success(measure())
        assertNotEquals("a new mark gives a newly computed result", t.expected.width, remarked.measurement.width, 1e-6)
        assertResults()

        clickResults("Re-mark")
        select(SurfaceOrientation.HORIZONTAL)
        assertNoUsableResult()
        assertSame("photo kept", img, s.captured)
        assertEquals("marks kept", moved, s.flow.corners)
        Expect.failure(measure(), Reason.UNSUPPORTED_GEOMETRY) // a wall page is not a floor/table
        assertMarking()
        assertShown(CorrectionText.headline(Reason.UNSUPPORTED_GEOMETRY))
        assertTrue(s.flow.failureMessage != null)
    }

    @Test fun C10_calibrationPageFrontalRectangleMatchesItsGroundTruth() {
        val t = TargetFixture.frontal
        val d = FlowDriver(rule, FlowState(t.reference)).start()
        val ok = d.measurePage(t)
        assertEquals(SolverKind.RECTANGLE, ok.solver)
        assertEquals(PlaneAssumption.RECTANGLE_TARGET, ok.diagnostics!!.assumption)
        ok.measurement.cornerAngles.forEach { Expect.near(90.0, it, 1e-6, "square corner") }
        d.staleResultsCannotSurvive(t)
    }

    @Test fun C07_calibrationPageNonrectangleKeepsItsAnglesAndIsNotAConfidentRectangle() {
        val t = TargetFixture.nonrectangle
        val d = FlowDriver(rule, FlowState(t.reference)).start()
        val ok = d.measurePage(t)
        assertNotEquals("a nonrectangle must not become a rectangle solution", SolverKind.RECTANGLE, ok.solver)
        assertEquals(PlaneAssumption.WALL_FACES_CAMERA, ok.diagnostics!!.assumption)
        assertTrue("confidence ${ok.confidence} must stay below Medium", ok.confidence < 0.4)
        assertTrue(ok.measurement.cornerAngles.count { abs(it - 90.0) > 3.0 } >= 3)
        d.assertShown("Low confidence")
        d.assertAbsent("High confidence")
        d.assertShown("Assumed the wall faces the camera")
        d.staleResultsCannotSurvive(t)
    }
}
