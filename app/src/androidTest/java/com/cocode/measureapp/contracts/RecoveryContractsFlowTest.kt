package com.cocode.measureapp.contracts

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cocode.measureapp.core.MeasurementPresenter
import com.cocode.measureapp.core.dimensions.ReferenceCheck
import com.cocode.measureapp.core.measurement.CorrectionText
import com.cocode.measureapp.geometry.MeasurementFailureReason as Reason
import com.cocode.measureapp.geometry.MeasurementOutcome
import com.cocode.measureapp.geometry.SolverKind
import com.cocode.measureapp.geometry.SurfaceOrientation.HORIZONTAL
import com.cocode.measureapp.geometry.Vec2
import com.cocode.measureapp.geometry.eligibility.SurfaceConsistency
import com.cocode.measureapp.geometry.frames.AlignedGravity
import com.cocode.measureapp.geometry.frames.CalibrationProvenance
import com.cocode.measureapp.geometry.frames.CalibrationStatus
import com.cocode.measureapp.geometry.frames.GravityAlignmentReason
import com.cocode.measureapp.geometry.frames.QuarterTurn
import com.cocode.measureapp.ui.CapturedImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Engine-to-presenter outcomes (C10) and Retake recovery (C11) through the marking flow. */
@RunWith(AndroidJUnit4::class)
class RecoveryContractsFlowTest {
    @get:Rule val rule = createComposeRule()

    private val frontal = TargetFixture.frontal
    private val quad = TargetFixture.nonrectangle
    private val noGravity = AlignedGravity.Unavailable(GravityAlignmentReason.NO_SAMPLE_AT_OR_BEFORE_EXPOSURE)

    /** A success is formatted on Results with its evidence carried into diagnostics. */
    private fun FlowDriver.succeeds(img: CapturedImage, solver: SolverKind): MeasurementOutcome.Success {
        val ok = Expect.success(measure())
        val diagnostics = ok.diagnostics!!
        assertEquals(solver, ok.solver)
        assertEquals(s.requests.last().revision, diagnostics.revision)
        assertEquals(img.scene.calibration, diagnostics.calibration)
        assertTrue(ok.confidence > 0.0 && ok.confidence <= 1.0)
        assertTrue(MeasurementPresenter.present(ok, s.unit).usable)
        assertResults()
        assertDisplayed(ok.measurement)
        assertExport(true)
        clickResults("Re-mark")
        return ok
    }

    /** A failure keeps its reason, explains it on the marking screen and formats nothing. */
    private fun FlowDriver.fails(reason: Reason, vararg detail: String): MeasurementOutcome.Failure {
        val fail = Expect.failure(measure(), reason)
        detail.forEach { assertTrue("detail should mention '$it': ${fail.detail}", fail.detail!!.contains(it)) }
        val view = MeasurementPresenter.present(fail, s.unit)
        assertFalse(view.usable)
        assertEquals("—", view.width)
        assertMarking()
        assertNoUsableResult()
        assertShown(CorrectionText.headline(reason))
        return fail
    }

    @Test fun C10_engineToPresenterPathOnlyFormatsSuccesses() {
        val d = FlowDriver(rule, FlowState(frontal.reference)).start()

        d.load(frontal.image(), frontal.objectPixels, frontal.stickPixels) // eligible rectangle
        val rect = d.succeeds(d.s.captured!!, SolverKind.RECTANGLE)
        assertEquals(SurfaceConsistency.CONSISTENT, rect.diagnostics!!.surfaceConsistency)

        d.load(quad.image(), quad.objectPixels, quad.stickPixels) // supported gravity wall
        d.succeeds(d.s.captured!!, SolverKind.GRAVITY)

        d.select(HORIZONTAL) // both candidates ineligible
        d.fails(Reason.UNSUPPORTED_GEOMETRY, "Rectangle method", "Tilt-sensor method")

        d.load(quad.image(gravity = noGravity), quad.objectPixels, quad.stickPixels) // missing gravity
        d.fails(Reason.METADATA_UNAVAILABLE, "NO_SAMPLE_AT_OR_BEFORE_EXPOSURE")

        // Without gravity a real rectangle still succeeds, its surface explicitly unverified;
        // approximate intrinsics stay approximate through the successful solve.
        d.load(frontal.image(gravity = noGravity, calibration = CalibrationProvenance.FOV_GUESS),
            frontal.objectPixels, frontal.stickPixels)
        val unverified = d.s.captured!!
        Expect.success(d.measure()).let { ok ->
            assertEquals(SurfaceConsistency.UNVERIFIED_NO_GRAVITY, ok.diagnostics!!.surfaceConsistency)
            assertEquals(CalibrationStatus.APPROXIMATE, ok.diagnostics!!.calibration!!.status)
            assertEquals(unverified.scene.calibration, ok.diagnostics!!.calibration)
            assertTrue(ok.confidence <= 0.6)
        }
        d.assertShown("No tilt reading at capture")
        d.assertShown("Camera calibration is approximate")
        d.clickResults("Re-mark")

        // Projection singularity: the stick is marked above the floor's horizon.
        val k = Targets.K_WALL
        val floor = Placement(Targets.FLOOR.origin.copy(y = 0.8), Targets.FLOOR.e1, Targets.FLOOR.e2, Targets.FLOOR.down)
        val obj = floor.pixels(Targets.FLOOR_RECT.obj, k)
        val skyStick = listOf(Vec2(560.0, 300.0), Vec2(720.0, 300.0), Vec2(720.0, 320.0), Vec2(560.0, 320.0))
        d.ui { storeReference(ReferenceCheck.Valid(0.5, 0.05)) }
        d.load(Scenes.image(Scenes.blank(Targets.W, Targets.H), k, AlignedGravity.Available(floor.down, 0L),
            CalibrationProvenance.CALIBRATED), obj, skyStick)
        d.select(HORIZONTAL)
        d.fails(Reason.UNSUPPORTED_GEOMETRY, "both sides")
    }

    @Test fun C11_retakeAfterFailedMeasurementCapturesAgainWithoutOldResult() {
        val d = FlowDriver(rule, FlowState(quad.reference)).start()
        d.load(quad.image(gravity = noGravity), quad.objectPixels, quad.stickPixels)
        val fail = d.fails(Reason.METADATA_UNAVAILABLE)
        val oldRevision = d.s.flow.session.revision
        val headline = CorrectionText.headline(fail.reason)

        d.click("Retake")
        assertEquals(HostStep.Capture, d.s.step)
        d.assertNoUsableResult()
        assertNull(d.s.flow.currentView)
        d.assertCapture(enabled = true) // no busy state or error carried over
        d.assertAbsent(headline)

        d.click("Capture")
        d.assertCapture(enabled = false)
        val pending = d.s.camera.pending.single()
        val shot = ShotRig.fromUpright(quad.bitmap(), quad.intrinsics, QuarterTurn.R90, quad.down,
            quad.objectPixels, quad.stickPixels)
        d.ui { camera.finish(pending, shot.outcome(pending.id)) }

        assertEquals(HostStep.Mark, d.s.step)
        val img = d.s.captured!!
        assertEquals(pending.id, img.metadata.requestId)
        assertEquals(pending.id, img.scene.shotId)
        assertNull("no result from the old image", d.s.flow.currentView)
        d.assertAbsent(headline)
        assertTrue(d.s.flow.session.revision > oldRevision)

        val (obj, stick) = shot.marks(img)
        d.ui { place(obj, stick) }
        val ok = d.succeeds(img, SolverKind.GRAVITY)
        Expect.sameResult(quad.expected, ok.measurement)
    }
}
