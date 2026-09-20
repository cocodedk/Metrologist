package com.cocode.measureapp.contracts

import android.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cocode.measureapp.capture.frames.SceneAligner
import com.cocode.measureapp.capture.frames.SceneAlignment
import com.cocode.measureapp.capture.recovery.CaptureOutcome
import com.cocode.measureapp.capture.recovery.ExposureTimestamp
import com.cocode.measureapp.capture.recovery.TimestampSource
import com.cocode.measureapp.core.dimensions.ReferenceCheck
import com.cocode.measureapp.geometry.CameraIntrinsics
import com.cocode.measureapp.geometry.MeasurementResult
import com.cocode.measureapp.geometry.SolverKind
import com.cocode.measureapp.geometry.Vec2
import com.cocode.measureapp.geometry.Vec3
import com.cocode.measureapp.geometry.frames.AlignedGravity
import com.cocode.measureapp.geometry.frames.CalibrationProvenance
import com.cocode.measureapp.geometry.frames.CalibrationStatus
import com.cocode.measureapp.geometry.frames.CameraMounting
import com.cocode.measureapp.geometry.frames.FrameAlignment
import com.cocode.measureapp.geometry.frames.FrameUnavailableReason
import com.cocode.measureapp.geometry.frames.LensFacing
import com.cocode.measureapp.geometry.frames.ProvenancedIntrinsics
import com.cocode.measureapp.geometry.frames.QuarterTurn
import com.cocode.measureapp.ui.CapturedImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/** Aligned projection (C03): pixels, intrinsics and gravity share one transform per capture. */
@RunWith(AndroidJUnit4::class)
class AlignmentContractsFlowTest {
    @get:Rule val rule = createComposeRule()

    private fun ray(k: CameraIntrinsics, p: Vec2) = Vec3((p.x - k.cx) / k.fx, (p.y - k.cy) / k.fy, 1.0).normalized()

    /** Every marking ray equals the rotated buffer ray of the same physical point. */
    private fun assertRaysMatch(a: FrameAlignment, kBuffer: CameraIntrinsics, kMarking: CameraIntrinsics, buffer: List<Vec2>) {
        buffer.forEach { p ->
            val expected = a.rotation.vector(ray(kBuffer, p)).normalized()
            Expect.vec(expected, ray(kMarking, a.toMarking(p)), 1e-9, "ray of $p at ${a.rotation}")
        }
    }

    private fun assertSameColour(expected: Int, actual: Int, where: String) {
        val channels = listOf<(Int) -> Int>({ Color.red(it) }, { Color.green(it) }, { Color.blue(it) })
        channels.forEach { c -> assertTrue("$where colour", abs(c(expected) - c(actual)) <= 2) }
    }

    @Test fun C03_rotatedCroppedResizedCapturesAlignRaysAndDimensions() {
        val f = TargetFixture.frontal
        val page = f.bitmap()
        val probes = listOf(Vec2(100.0, 80.0), Vec2(381.0, 261.0), Vec2(700.0, 450.0), Vec2(240.0, 350.0))
        for (r in QuarterTurn.entries) { // real bitmaps through the app's alignment, crop pending
            val shot = ShotRig.fromUpright(page, f.intrinsics, r, f.down, f.objectPixels, f.stickPixels)
            val img = (shot.outcome(r.ordinal + 1) as CaptureOutcome.Success).payload
            assertEquals(f.imageWidth, img.bitmap.width)
            assertEquals(f.imageHeight, img.bitmap.height)
            probes.forEach { p ->
                val x = p.x.toInt()
                val y = p.y.toInt()
                assertSameColour(page.getPixel(x, y), img.bitmap.getPixel(x, y), "$r pixel ($x, $y)")
            }
            val k = img.scene.intrinsics
            listOf(f.intrinsics.fx to k.fx, f.intrinsics.fy to k.fy, f.intrinsics.cx to k.cx, f.intrinsics.cy to k.cy)
                .forEach { (e, a) -> Expect.near(e, a, 1e-9, "$r intrinsics") }
            assertRaysMatch(img.scene.alignment!!, shot.kBuffer, k, shot.bufferObject + shot.bufferStick)
            val (obj, _) = shot.marks(img)
            obj.zip(f.objectPixels).forEach { (a, e) -> assertTrue("$r mark $e -> $a", a.distanceTo(e) < 1e-9) }
            Expect.vec(f.down, (img.scene.alignedGravity as AlignedGravity.Available).down, 1e-9, "$r gravity")
        }

        // Known crop and resize on a yawed wall, in every orientation, measured on the marking screen.
        val k = Targets.K_WALL
        val wall = Targets.WALL.copy(origin = Vec3(0.0, 0.0, 2.2), e1 = Rot.yaw(15.0)(Targets.WALL.e1))
        val target = Targets.WALL_RECT
        val bufObj = wall.pixels(target.obj, k)
        val bufStick = wall.pixels(target.stick, k)
        val d = FlowDriver(rule, FlowState(ReferenceCheck.Valid(0.5, 0.05))).start()
        val results: List<MeasurementResult> = QuarterTurn.entries.map { r ->
            val a = FrameAlignment(Targets.W, Targets.H, 100, 60, 1080, 840, 540, 420, r)
            val aligned = SceneAligner.align(
                r.ordinal + 10, a, ProvenancedIntrinsics(k, CalibrationProvenance.CALIBRATED),
                CameraMounting(LensFacing.BACK, ShotRig.SENSOR), (ShotRig.SENSOR.degrees - r.degrees + 360) % 360,
                ExposureTimestamp.Available(ShotRig.EXPOSURE, TimestampSource.REALTIME), ShotRig.samples(wall.down).second,
            ) as SceneAlignment.Aligned
            val scene = aligned.scene
            assertRaysMatch(a, k, scene.intrinsics, bufObj + bufStick)
            Expect.vec(r.vector(wall.down), (scene.alignedGravity as AlignedGravity.Available).down, 1e-9, "$r gravity")
            val img = CapturedImage(Scenes.blank(scene.imageWidth, scene.imageHeight), scene,
                Scenes.metadata(r.ordinal + 10, Targets.W, Targets.H))
            d.load(img, bufObj.map(a::toMarking), bufStick.map(a::toMarking))
            val ok = Expect.success(d.measure())
            assertEquals(SolverKind.RECTANGLE, ok.solver)
            assertEquals(CalibrationProvenance.CALIBRATED, ok.diagnostics!!.calibration)
            Expect.sameShape(target.oracle, ok.measurement, 1e-6, "$r")
            d.assertResults()
            d.clickResults("Re-mark")
            ok.measurement
        }
        results.forEach { Expect.sameShape(results[0], it, 1e-6, "orientation-independent") }

        // A missing or invalid transform never becomes a calibrated candidate.
        val invalid = SceneAligner.align(
            99, FrameAlignment(Targets.W, Targets.H, 100, 60, 1080, 840, 0, 420, QuarterTurn.R0),
            ProvenancedIntrinsics(k, CalibrationProvenance.CALIBRATED), CameraMounting(LensFacing.BACK, ShotRig.SENSOR),
            null, ExposureTimestamp.Available(ShotRig.EXPOSURE, TimestampSource.REALTIME), emptyList(),
        )
        assertEquals(SceneAlignment.Unavailable(FrameUnavailableReason.INVALID_SCALE), invalid)
        d.ui { storeReference(f.reference) }
        d.load(Scenes.unaligned(page, f.intrinsics, f.down), f.objectPixels, f.stickPixels)
        val legacy = Expect.success(d.measure())
        assertEquals(CalibrationStatus.UNAVAILABLE, legacy.diagnostics!!.calibration!!.status)
        assertTrue("not a guessed high-confidence result", legacy.confidence <= 0.3)
        d.assertShown("Camera calibration is unavailable")
        d.assertShown("Low confidence")
    }
}
