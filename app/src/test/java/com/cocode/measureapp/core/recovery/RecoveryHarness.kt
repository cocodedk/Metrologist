package com.cocode.measureapp.core.recovery

import com.cocode.measureapp.core.LengthUnit
import com.cocode.measureapp.core.dimensions.ReferenceCheck
import com.cocode.measureapp.core.dimensions.ReferenceDimensions
import com.cocode.measureapp.core.measurement.MeasurementAttempt
import com.cocode.measureapp.core.measurement.OutcomeEngine
import com.cocode.measureapp.core.measurement.OutcomeRequest
import com.cocode.measureapp.geometry.ContractScenes
import com.cocode.measureapp.geometry.MeasurementFailureReason
import com.cocode.measureapp.geometry.MeasurementOutcome
import com.cocode.measureapp.geometry.SceneRotations
import com.cocode.measureapp.geometry.SyntheticScene
import com.cocode.measureapp.geometry.Vec2
import com.cocode.measureapp.geometry.Vec3
import com.cocode.measureapp.geometry.frames.AlignedGravity
import com.cocode.measureapp.geometry.frames.CalibrationProvenance
import com.cocode.measureapp.model.CapturedScene
import com.cocode.measureapp.ui.surface.MarkingFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

/**
 * Drives [MarkingFlow] in the order `MeasureApp` wires its callbacks: capture -> mark edits
 * (`onMarkChanged`) -> Measure (`marksChanged` then `attempt`) -> Results / Re-mark / Settings
 * / Retake, with node 03's real producer behind a recording engine. The Compose taps and a
 * physical camera are not exercised here; those belong to the Android integration task.
 */
class RecoveryHarness {
    /** Oblique 2 x 1 m wall (25 deg yaw, 20 deg pitch), 1 x 0.04 m stick, contract K. */
    val wall: SyntheticScene = ContractScenes.wall(SceneRotations.yawPitch(25.0, 20.0))

    /** The scene's aligned, calibrated metadata. The legacy vector is deliberately wrong. */
    val scene = sceneFor(wall)

    val reference: ReferenceCheck = ReferenceDimensions.check(1.0, 0.04)
    val requests = mutableListOf<OutcomeRequest>()
    val outcomes = mutableListOf<MeasurementOutcome>()
    val engine: OutcomeEngine = { r ->
        requests += r
        MeasurementAttempt.engine(r).also { outcomes += it }
    }

    /** Collapsed acceptance stick: TR == BR and BL == TL. */
    val collapsedStick = listOf(Vec2(875.0, 750.0), Vec2(1125.0, 750.0), Vec2(1125.0, 750.0), Vec2(875.0, 750.0))

    /** Object marks with a repeated corner. */
    val overlappingCorners = wall.cornerPixels.take(3) + wall.cornerPixels[0]

    fun sceneFor(
        s: SyntheticScene,
        gravity: AlignedGravity = AlignedGravity.Available(s.gravityCam, 0L),
        calibration: CalibrationProvenance = CalibrationProvenance.CALIBRATED,
        shotId: Int = 1,
    ) = CapturedScene(
        imageWidth = 2000, imageHeight = 1500, intrinsics = s.k, gravity = LEGACY_PLACEHOLDER,
        shotId = shotId, calibration = calibration, alignedGravity = gravity,
    )

    /** Photo accepted from the camera: `onCaptured` -> `flow.captured()`. */
    fun capture(from: MarkingFlow = MarkingFlow()): MarkingFlow = from.captured()

    /** Measure button, exactly as `MeasureApp.onMeasure` runs it. */
    fun MarkingFlow.press(
        corners: List<Vec2> = wall.cornerPixels,
        stick: List<Vec2> = wall.stickPixels,
        on: CapturedScene = scene,
        ref: ReferenceCheck = reference,
    ): MarkingFlow = marksChanged(corners, stick).attempt(corners, stick, on, ref, LengthUnit.METERS, engine)

    fun assertSuccess(flow: MarkingFlow, label: String): MeasurementOutcome.Success {
        val s = flow.session.usableResult ?: throw AssertionError("$label: expected success, got ${flow.session.lastOutcome}")
        val view = flow.usableView
        assertNotNull("$label: Results has a view", view)
        assertTrue(label, flow.exportEnabled)
        assertNull(label, flow.failureMessage)
        assertEquals("$label: bound to the current revision", flow.session.revision, s.diagnostics!!.revision)
        assertEquals(label, 2.0, s.measurement.width, 0.02)
        assertEquals(label, 1.0, s.measurement.height, 0.01)
        return s
    }

    fun assertFailure(flow: MarkingFlow, reason: MeasurementFailureReason, label: String): MeasurementOutcome.Failure {
        val f = flow.session.failure ?: throw AssertionError("$label: expected failure, got ${flow.session.lastOutcome}")
        assertEquals(label, reason, f.reason)
        assertNoResult(flow, label)
        val view = flow.currentView!!
        assertFalse(label, view.usable)
        assertEquals(label, reason, view.failure)
        assertEquals(label, view.message, flow.failureMessage)
        assertTrue("$label: no fabricated dimensions", listOf(view.width, view.height, view.area).none { it.any(Char::isDigit) })
        return f
    }

    /** Neither Results nor Export may use anything for the current revision. */
    fun assertNoResult(flow: MarkingFlow, label: String) {
        assertNull("$label: no usable view", flow.usableView)
        assertNull("$label: no usable outcome", flow.session.usableResult)
        assertFalse("$label: export disabled", flow.exportEnabled)
        assertFalse("$label: session export disabled", flow.session.exportEnabled)
    }

    companion object {
        /** Legacy `CapturedScene.gravity` placeholder; the production path must never read it. */
        val LEGACY_PLACEHOLDER = Vec3(1.0, 0.0, 0.0)
    }
}
