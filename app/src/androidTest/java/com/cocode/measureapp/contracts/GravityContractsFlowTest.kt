package com.cocode.measureapp.contracts

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cocode.measureapp.capture.frames.SceneAligner
import com.cocode.measureapp.capture.frames.SceneAlignment
import com.cocode.measureapp.capture.gravity.GravitySample
import com.cocode.measureapp.capture.recovery.CaptureOutcome
import com.cocode.measureapp.capture.recovery.CropRect
import com.cocode.measureapp.capture.recovery.ExposureTimestamp
import com.cocode.measureapp.capture.recovery.TimestampSource
import com.cocode.measureapp.core.dimensions.ReferenceCheck
import com.cocode.measureapp.geometry.MeasurementFailureReason as Reason
import com.cocode.measureapp.geometry.MeasurementOutcome
import com.cocode.measureapp.geometry.SolverKind
import com.cocode.measureapp.geometry.SurfaceOrientation.HORIZONTAL
import com.cocode.measureapp.geometry.eligibility.SurfaceConsistency
import com.cocode.measureapp.geometry.frames.AlignedGravity
import com.cocode.measureapp.geometry.frames.CalibrationProvenance
import com.cocode.measureapp.geometry.frames.CalibrationStatus
import com.cocode.measureapp.geometry.frames.CameraMounting
import com.cocode.measureapp.geometry.frames.FrameAlignment
import com.cocode.measureapp.geometry.frames.GravityAlignmentReason
import com.cocode.measureapp.geometry.frames.LensFacing
import com.cocode.measureapp.geometry.frames.ProvenancedIntrinsics
import com.cocode.measureapp.geometry.frames.QuarterTurn
import com.cocode.measureapp.ui.CapturedImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Aligned gravity (C04): a tilted floor measured by the gravity branch in all four orientations. */
@RunWith(AndroidJUnit4::class)
class GravityContractsFlowTest {
    @get:Rule val rule = createComposeRule()

    private val k = Targets.K_FLOOR
    private val floor = Targets.TILTED_FLOOR
    private val fresh = ShotRig.samples(floor.down)
    private val stale = ShotRig.samples(floor.down, age = 500_000_000L)
    private lateinit var d: FlowDriver

    /** Captures [target] through the real bitmap alignment and places its marks on floor/table. */
    private fun capture(
        target: Planar,
        r: QuarterTurn,
        gravity: Pair<GravitySample, List<GravitySample.Available>>,
    ): CapturedImage {
        val raw = Scenes.blank(Targets.W, Targets.H)
        val shot = ShotRig.frame(raw, CropRect(0, 0, Targets.W, Targets.H), k, r, gravity,
            floor.pixels(target.obj, k), floor.pixels(target.stick, k), calibratedLens = false)
        val img = (shot.outcome(r.ordinal + 1) as CaptureOutcome.Success).payload
        val (obj, stick) = shot.marks(img)
        d.load(img, obj, stick)
        d.select(HORIZONTAL)
        return img
    }

    /** The same shot with trusted intrinsics, aligned by [SceneAligner] with rotation [r]. */
    private fun calibrated(target: Planar, r: QuarterTurn, samples: List<GravitySample.Available>): CapturedImage {
        val a = FrameAlignment(Targets.W, Targets.H, 0, 0, Targets.W, Targets.H, Targets.W, Targets.H, r)
        val scene = (SceneAligner.align(
            40, a, ProvenancedIntrinsics(k, CalibrationProvenance.CALIBRATED), CameraMounting(LensFacing.BACK, ShotRig.SENSOR),
            (ShotRig.SENSOR.degrees - r.degrees + 360) % 360,
            ExposureTimestamp.Available(ShotRig.EXPOSURE, TimestampSource.REALTIME), samples,
        ) as SceneAlignment.Aligned).scene
        val img = CapturedImage(Scenes.blank(scene.imageWidth, scene.imageHeight), scene, Scenes.metadata(40, Targets.W, Targets.H))
        d.load(img, floor.pixels(target.obj, k).map(a::toMarking), floor.pixels(target.stick, k).map(a::toMarking))
        d.select(HORIZONTAL)
        return img
    }

    /** Measures on the marking screen; the scene's calibration survives into diagnostics and UI. */
    private fun measured(img: CapturedImage, solver: SolverKind, target: Planar): MeasurementOutcome.Success {
        val ok = Expect.success(d.measure())
        assertEquals(solver, ok.solver)
        assertEquals(HORIZONTAL, ok.orientation)
        assertEquals(img.scene.calibration, ok.diagnostics!!.calibration)
        Expect.sameShape(target.oracle, ok.measurement, 1e-6, "$solver")
        d.assertResults()
        if (img.scene.calibration.status == CalibrationStatus.APPROXIMATE) d.assertShown("Camera calibration is approximate")
        d.clickResults("Re-mark")
        return ok
    }

    @Test fun C04_gravityBranchOnATiltedFloorIsOrientationIndependentAndKeepsProvenance() {
        d = FlowDriver(rule, FlowState(ReferenceCheck.Valid(0.5, 0.05))).start()
        assertTrue("the tilted camera sees gravity with a forward component", floor.down.z > 0.5)

        val fallback = QuarterTurn.entries.map { r ->
            val img = capture(Targets.FLOOR_QUAD, r, fresh)
            assertEquals(CalibrationProvenance.FOV_GUESS, img.scene.calibration)
            val down = (img.scene.alignedGravity as AlignedGravity.Available).down
            Expect.vec(r.vector(floor.down), down, 1e-9, "$r aligned gravity")
            measured(img, SolverKind.GRAVITY, Targets.FLOOR_QUAD).measurement
        }
        fallback.forEach { Expect.sameShape(fallback[0], it, 1e-6, "equivalent orientations") }

        // A stale sample is unavailable, never a default upright vector: the gravity branch is off.
        val staleImg = capture(Targets.FLOOR_QUAD, QuarterTurn.R90, stale)
        assertEquals(AlignedGravity.Unavailable(GravityAlignmentReason.STALE_SAMPLE), staleImg.scene.alignedGravity)
        val fail = Expect.failure(d.measure(), Reason.METADATA_UNAVAILABLE)
        assertTrue(fail.detail!!.contains("STALE_SAMPLE"))
        d.assertMarking()

        // The independently eligible rectangle still measures, keeping the fallback provenance.
        val rectImg = capture(Targets.FLOOR_RECT, QuarterTurn.R90, stale)
        val rect = measured(rectImg, SolverKind.RECTANGLE, Targets.FLOOR_RECT).diagnostics!!
        assertEquals(SurfaceConsistency.UNVERIFIED_NO_GRAVITY, rect.surfaceConsistency)
        assertEquals(GravityAlignmentReason.STALE_SAMPLE, rect.gravityUnavailable)

        // Trusted intrinsics through both branches keep their calibrated provenance.
        val gravityOk = measured(calibrated(Targets.FLOOR_QUAD, QuarterTurn.R270, fresh.second), SolverKind.GRAVITY, Targets.FLOOR_QUAD)
        assertEquals(CalibrationStatus.CALIBRATED, gravityOk.diagnostics!!.calibration!!.status)
        Expect.sameShape(fallback[0], gravityOk.measurement, 1e-6, "calibrated vs fallback")
        val rectOk = measured(calibrated(Targets.FLOOR_RECT, QuarterTurn.R180, stale.second), SolverKind.RECTANGLE, Targets.FLOOR_RECT)
        assertEquals(CalibrationStatus.CALIBRATED, rectOk.diagnostics!!.calibration!!.status)
    }
}
