package com.cocode.measureapp.core.recovery

import com.cocode.measureapp.core.LengthUnit
import com.cocode.measureapp.core.MeasurementPresenter
import com.cocode.measureapp.core.dimensions.ReferenceDimensions
import com.cocode.measureapp.core.measurement.CorrectionText
import com.cocode.measureapp.core.measurement.RecoveryAction
import com.cocode.measureapp.geometry.ContractScenes
import com.cocode.measureapp.geometry.MeasurementFailureReason
import com.cocode.measureapp.geometry.MeasurementFailureReason.INVALID_OBJECT_CORNERS
import com.cocode.measureapp.geometry.MeasurementFailureReason.INVALID_REFERENCE_DIMENSIONS
import com.cocode.measureapp.geometry.MeasurementFailureReason.INVALID_STICK_CORNERS
import com.cocode.measureapp.geometry.MeasurementFailureReason.METADATA_UNAVAILABLE
import com.cocode.measureapp.geometry.MeasurementFailureReason.UNSUPPORTED_GEOMETRY
import com.cocode.measureapp.geometry.MeasurementOutcome
import com.cocode.measureapp.geometry.SceneRotations
import com.cocode.measureapp.geometry.SurfaceOrientation.HORIZONTAL
import com.cocode.measureapp.geometry.SurfaceOrientation.VERTICAL
import com.cocode.measureapp.geometry.eligibility.EligibilityFixtures as F
import com.cocode.measureapp.geometry.frames.AlignedGravity
import com.cocode.measureapp.geometry.frames.GravityAlignmentReason
import com.cocode.measureapp.ui.surface.MarkingFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue

/** Node 05 recovery scenarios; each runs in a fresh [RecoveryHarness]. */
object RecoveryCases {
    /** Collapsed stick with a valid object quad: caught, explained, marks and photo kept. */
    fun collapsedStickIsExplainedInTheMarkingFlow() = with(RecoveryHarness()) {
        val failed = capture().orientationSelected(VERTICAL).press(stick = collapsedStick)
        val f = assertFailure(failed, INVALID_STICK_CORNERS, "collapsed stick")
        assertTrue(failed.failureMessage!!, failed.failureMessage!!.startsWith(CorrectionText.headline(INVALID_STICK_CORNERS)))
        assertTrue("validator detail kept", failed.failureMessage!!.contains(f.detail!!))
        assertEquals(wall.cornerPixels, failed.corners)
        assertEquals(collapsedStick, failed.stick)
        assertEquals(RecoveryAction.REMARK, CorrectionText.action(f.reason))
    }

    /** Ray parallel to the selected plane and a both-ineligible scene: failures, no exceptions. */
    fun geometryFailuresReachThePresenter() = with(RecoveryHarness()) {
        val frontal = ContractScenes.wall(SceneRotations.yawPitch(0.0, 0.0))
        val level = sceneFor(frontal, AlignedGravity.Available(F.LEVEL, 0L))
        val parallel = capture().orientationSelected(HORIZONTAL).press(frontal.cornerPixels, frontal.stickPixels, level)
        assertFailure(parallel, UNSUPPORTED_GEOMETRY, "ray parallel to plane")
        val down = sceneFor(frontal, AlignedGravity.Available(F.STRAIGHT_DOWN, 0L))
        val unusable = capture().press(F.nonRectCorners, F.nonRectStick, down)
        val f = assertFailure(unusable, UNSUPPORTED_GEOMETRY, "no eligible solver")
        assertTrue("both methods explained", unusable.failureMessage!!.contains(f.detail!!))
        val missing = sceneFor(frontal, AlignedGravity.Unavailable(GravityAlignmentReason.STALE_SAMPLE))
        assertFailure(capture().press(F.nonRectCorners, F.nonRectStick, missing), METADATA_UNAVAILABLE, "stale gravity")
        val badK = scene.copy(intrinsics = wall.k.copy(fx = 0.0))
        val meta = assertFailure(capture().press(on = badK), METADATA_UNAVAILABLE, "missing intrinsics")
        assertEquals(RecoveryAction.RETAKE, CorrectionText.action(meta.reason))
        assertEquals(4, requests.size)
    }

    /** Success, edit to bad marks, fail, correct, succeed — no restart, no stale result. */
    fun correctionAfterSuccessNeverShowsTheOldResult() = with(RecoveryHarness()) {
        val ok = capture().press()
        assertSuccess(ok, "first success")
        val edited = ok.marksChanged(overlappingCorners, wall.stickPixels)
        assertEquals(ok.session.revision + 1, edited.session.revision)
        assertNoResult(edited, "after mark edit")
        val failed = edited.press(corners = overlappingCorners)
        assertFailure(failed, INVALID_OBJECT_CORNERS, "overlapping corners")
        assertEquals("marks kept for correction", overlappingCorners, failed.corners)
        val fixed = failed.press()
        assertSuccess(fixed, "corrected")
        assertEquals(listOf(true, false, true), outcomes.map { it.usable })
        // The legacy gravity placeholder never reaches the engine.
        assertTrue(requests.all { it.gravity == scene.alignedGravity && it.calibration == scene.calibration })
    }

    /** Invalid settings stop the attempt; photo, marks and surface survive the correction. */
    fun invalidSettingsKeepPhotoMarksAndSurface() = with(RecoveryHarness()) {
        val ok = capture().orientationSelected(HORIZONTAL).orientationSelected(VERTICAL).press()
        assertSuccess(ok, "before settings")
        val changed = ok.settingsChanged()
        assertNoResult(changed, "after settings change")
        assertEquals(ok.corners, changed.corners)
        assertEquals(VERTICAL, changed.orientation)
        val bad = ReferenceDimensions.check(Double.NaN, 0.04)
        val rejected = changed.press(ref = bad)
        val f = assertFailure(rejected, INVALID_REFERENCE_DIMENSIONS, "NaN length")
        assertEquals(RecoveryAction.SETTINGS, CorrectionText.action(f.reason))
        assertTrue(rejected.failureMessage!!, rejected.failureMessage!!.contains("stick length"))
        assertEquals("engine not run with invalid settings", 1, requests.size)
        val corrected = rejected.settingsChanged().press()
        assertSuccess(corrected, "after correction")
        assertEquals(VERTICAL, requests.last().orientation)
    }

    /** C11: fail, Retake, accept a new image, succeed; nothing from the old image survives. */
    fun retakeAfterFailureAcceptsNewCapture() = with(RecoveryHarness()) {
        val failed = capture().press(stick = collapsedStick)
        assertFailure(failed, INVALID_STICK_CORNERS, "before retake")
        val oldRequest = requests.single()
        val retaken = failed.retake()
        assertNoResult(retaken, "after retake")
        assertEquals(null, retaken.failureMessage)
        val fresh = capture(retaken)
        assertEquals(null, fresh.corners)
        assertSame("old completion rejected", fresh, fresh.completed(oldRequest, outcomes.single(), LengthUnit.METERS))
        val next = sceneFor(wall, shotId = 2)
        assertSuccess(fresh.press(on = next), "new capture")
        assertTrue(fresh.session.revision > failed.session.revision)
    }

    /** C14: node 03's real success and failure variants through the presenter. */
    fun sharedSchemaThroughPresenter() = with(RecoveryHarness()) {
        val success = MeasurementPresenter.present(assertSuccess(capture().press(), "success"), LengthUnit.METERS)
        assertTrue(success.usable)
        assertEquals(null, success.failure)
        assertTrue(success.width, success.width.endsWith(" m") && success.width.any(Char::isDigit))
        val failure = outcomesOf(capture().press(stick = collapsedStick))
        val view = MeasurementPresenter.present(failure, LengthUnit.METERS)
        assertFalse(view.usable)
        assertEquals(failure.reason, view.failure)
        assertEquals(0, view.confidencePercent)
        val messages = MeasurementFailureReason.entries.map { CorrectionText.headline(it) }
        assertEquals("a specific message per reason", messages.size, messages.toSet().size)
    }

    /** C15: success, surface change, late completion; unavailable until the new revision succeeds. */
    fun surfaceChangeRejectsLateCompletion() = with(RecoveryHarness()) {
        val ok = capture().press()
        val success = assertSuccess(ok, "wall")
        val floor = ok.orientationSelected(HORIZONTAL)
        assertNoResult(floor, "after surface change")
        assertEquals(ok.corners, floor.corners)
        assertSame(floor, floor.completed(requests.single(), success, LengthUnit.METERS))
        assertEquals(null, floor.session.complete(ok.session.revision, success).lastOutcome)
        val back = floor.orientationSelected(VERTICAL)
        assertNoResult(back, "switching back needs a new measurement")
        val again = back.press()
        assertSuccess(again, "new revision")
        assertNotEquals(ok.session.revision, again.session.revision)
    }

    private fun outcomesOf(flow: MarkingFlow): MeasurementOutcome.Failure = flow.session.failure!!
}
