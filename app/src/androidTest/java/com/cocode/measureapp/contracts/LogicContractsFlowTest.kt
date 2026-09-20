package com.cocode.measureapp.contracts

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cocode.measureapp.core.LengthInput
import com.cocode.measureapp.core.MeasurementPresenter
import com.cocode.measureapp.core.dimensions.ReferenceCheck
import com.cocode.measureapp.core.dimensions.ReferenceDimensions
import com.cocode.measureapp.core.dimensions.ReferenceField
import com.cocode.measureapp.core.measurement.CorrectionText
import com.cocode.measureapp.core.validation.MarkerRule
import com.cocode.measureapp.core.validation.MarkerValidation
import com.cocode.measureapp.core.validation.MarkerValidator
import com.cocode.measureapp.geometry.MeasurementFailureReason as Reason
import com.cocode.measureapp.geometry.MeasurementOutcome
import com.cocode.measureapp.geometry.SurfaceOrientation.HORIZONTAL
import com.cocode.measureapp.geometry.SurfaceOrientation.VERTICAL
import com.cocode.measureapp.geometry.Vec2
import com.cocode.measureapp.geometry.frames.AlignedGravity
import com.cocode.measureapp.geometry.frames.GravityAlignmentReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Marking-flow contracts C05, C08, C09, C14 and C15 on the rendered calibration target. */
@RunWith(AndroidJUnit4::class)
class LogicContractsFlowTest {
    @get:Rule val rule = createComposeRule()

    private val f = TargetFixture.frontal

    private fun driver() = FlowDriver(rule, FlowState(f.reference)).start().also {
        it.load(f.image(), f.objectPixels, f.stickPixels)
    }

    @Test fun C05_visibleSurfaceSelectionIsTheEngineRequestAndSurvivesRemark() {
        val d = driver()
        d.assertSelected(VERTICAL)
        d.select(HORIZONTAL)
        d.assertSelected(HORIZONTAL)
        d.measure()
        assertEquals(HORIZONTAL, d.s.requests.last().orientation)
        d.assertMarking() // a wall selected as floor contradicts gravity: never silently re-labelled
        d.select(VERTICAL)
        Expect.success(d.measure())
        val wall = d.s.requests.last()
        assertEquals(VERTICAL, wall.orientation)
        d.assertResults()
        d.clickResults("Re-mark")
        d.assertMarking()
        d.assertSelected(VERTICAL)
        assertNotNull("navigation alone keeps the current result", d.s.flow.usableView)
        d.select(HORIZONTAL)
        d.assertNoUsableResult()
        val fresh = d.measure()
        val request = d.s.requests.last()
        assertEquals(HORIZONTAL, request.orientation)
        assertTrue("a newly computed request", request.revision > wall.revision)
        Expect.failure(fresh, Reason.UNSUPPORTED_GEOMETRY)
        d.assertMarking()
        d.assertShown(CorrectionText.headline(Reason.UNSUPPORTED_GEOMETRY))
    }

    @Test fun C08_invalidMarksStayOnMarkingWithDistinctCorrections() {
        val d = driver()
        val collinear = listOf(Vec2(100.0, 100.0), Vec2(250.0, 100.0), Vec2(400.0, 100.0), Vec2(550.0, 100.0))
        val broken = (MarkerValidator.validateObject(collinear) as MarkerValidation.Rejected).rule
        assertTrue(broken == MarkerRule.COLLINEAR_VERTICES || broken == MarkerRule.ZERO_AREA)
        d.ui { place(collinear, f.stickPixels) }
        val objFail = Expect.failure(d.measure(), Reason.INVALID_OBJECT_CORNERS)
        d.assertMarking()
        d.assertShown(objFail.detail!!)
        d.assertNoUsableResult()
        assertEquals("placements are preserved", collinear, d.s.flow.corners)

        val s = f.stickPixels
        val collapsed = listOf(s[0], s[1], s[1], s[0])
        d.ui { place(f.objectPixels, collapsed) }
        assertNull("an edit clears the old correction", d.s.flow.failureMessage)
        val stickFail = Expect.failure(d.measure(), Reason.INVALID_STICK_CORNERS)
        d.assertMarking()
        d.assertShown(stickFail.detail!!)
        assertNotEquals(objFail.detail, stickFail.detail)
        d.assertAbsent(objFail.detail!!)
        assertEquals(collapsed, d.s.flow.stick)

        d.click("Reset") // the marking UI's own fix: valid default boxes
        val retry = d.measure()
        assertFalse(retry is MeasurementOutcome.Failure &&
            (retry.reason == Reason.INVALID_OBJECT_CORNERS || retry.reason == Reason.INVALID_STICK_CORNERS))
        d.ui { place(f.objectPixels, f.stickPixels) }
        Expect.success(d.measure())
        d.assertResults()
    }

    @Test fun C09_invalidReferenceSettingsBlockMeasurementAndSameImageRetries() {
        val d = driver()
        val img = d.s.captured
        for (bad in listOf(Double.NaN, 0.0, -0.1, Double.POSITIVE_INFINITY)) {
            for (field in ReferenceField.entries) {
                val loaded = if (field == ReferenceField.LENGTH) {
                    ReferenceDimensions.fromStored(bad, 0.02, 1.0, 0.04)
                } else {
                    ReferenceDimensions.fromStored(0.1, bad, 1.0, 0.04)
                }
                d.ui { storeReference(loaded) }
                val engineRuns = d.s.requests.size
                d.click("Measure")
                assertEquals("invalid settings never reach the engine", engineRuns, d.s.requests.size)
                d.assertMarking()
                d.assertNoUsableResult()
                assertEquals(Reason.INVALID_REFERENCE_DIMENSIONS, d.s.flow.session.failure?.reason)
                d.assertShown(ReferenceDimensions.correctionMessage(field))
                val other = ReferenceField.entries.first { it != field }
                d.assertAbsent(ReferenceDimensions.correctionMessage(other))
                assertSame(img, d.s.captured)
                assertEquals(f.objectPixels, d.s.flow.corners)
            }
        }
        d.click("Settings")
        for (text in listOf("abc", "0", "-1", "NaN", "Infinity", "1e309")) {
            d.typeLength(text)
            d.assertShown(LengthInput.CORRECTION_MESSAGE)
            assertTrue("rejected text is never accepted", d.s.reference is ReferenceCheck.NeedsCorrection)
        }
        d.enterReference(unitIndex = 1, length = "10", width = "2") // centimetres
        val valid = d.s.reference as ReferenceCheck.Valid
        Expect.near(0.1, valid.lengthMeters, 1e-12, "entered length")
        Expect.near(0.02, valid.widthMeters, 1e-12, "entered width")
        d.back()
        Expect.sameResult(f.expected, Expect.success(d.measure()).measurement)
        assertSame("retried with the same image", img, d.s.captured)
        assertEquals(img!!.scene.intrinsics, d.s.requests.last().intrinsics)
        d.assertResults()
    }

    @Test fun C14_realSuccessAndFailureVariantsThroughPresenterAndResults() {
        val n = TargetFixture.nonrectangle
        val d = FlowDriver(rule, FlowState(n.reference)).start()
        d.load(n.image(gravity = AlignedGravity.Unavailable(GravityAlignmentReason.STALE_SAMPLE)), n.objectPixels, n.stickPixels)
        val fail = Expect.failure(d.measure(), Reason.METADATA_UNAVAILABLE)
        val view = MeasurementPresenter.present(fail, d.s.unit)
        assertFalse(view.usable)
        assertEquals(fail.reason, view.failure)
        assertEquals("—", view.width)
        assertTrue(view.cornerAngles.isEmpty())
        d.assertMarking()
        d.assertShown(CorrectionText.headline(fail.reason))
        d.assertNoUsableResult()
        assertFalse("no Export on the marking screen", d.has("Export", substring = false))
        d.ui { step = HostStep.Results } // forcing navigation cannot open a failure
        d.assertMarking()
        assertEquals(0, d.s.exports)

        d.load(n.image(), n.objectPixels, n.stickPixels)
        val ok = Expect.success(d.measure())
        assertTrue(MeasurementPresenter.present(ok, d.s.unit).usable)
        d.assertResults()
        d.assertExport(true)
        d.clickResults("Export")
        assertEquals(1, d.s.exports)
    }

    @Test fun C15_surfaceChangeInvalidatesAndALateOldCompletionCannotRestoreTheResult() {
        val d = driver()
        val img = d.s.captured
        Expect.success(d.measure())
        val oldRequest = d.s.requests.last()
        val oldOutcome = d.s.outcomes.last()
        d.assertResults()
        d.clickResults("Re-mark")
        d.select(HORIZONTAL)
        d.assertNoUsableResult()
        assertSame(img, d.s.captured)
        assertEquals(f.objectPixels, d.s.flow.corners)
        assertEquals(HORIZONTAL, d.s.flow.orientation)

        d.ui { flow = flow.completed(oldRequest, oldOutcome, unit) } // the old completion arrives late
        d.assertNoUsableResult()
        d.ui { step = HostStep.Results }
        d.assertMarking()

        d.select(VERTICAL) // same selection as the old request, but a newer revision
        d.ui { flow = flow.completed(oldRequest, oldOutcome, unit) }
        d.assertNoUsableResult()
        Expect.success(d.measure())
        assertTrue(d.s.requests.last().revision > oldRequest.revision)
        d.assertResults()
        d.assertExport(true)
    }
}
