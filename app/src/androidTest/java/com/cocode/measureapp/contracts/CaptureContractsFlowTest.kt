package com.cocode.measureapp.contracts

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cocode.measureapp.capture.GravityProvider
import com.cocode.measureapp.capture.gravity.GravitySample
import com.cocode.measureapp.capture.gravity.SensorClock
import com.cocode.measureapp.capture.recovery.CONVERSION_FAILED
import com.cocode.measureapp.capture.recovery.CaptureController
import com.cocode.measureapp.capture.recovery.CaptureOutcome
import com.cocode.measureapp.capture.recovery.CropRect
import com.cocode.measureapp.capture.recovery.ExposureTimestamp
import com.cocode.measureapp.capture.recovery.TIMESTAMP_SOURCE_REALTIME
import com.cocode.measureapp.capture.recovery.TIMESTAMP_SOURCE_UNKNOWN
import com.cocode.measureapp.capture.recovery.TimestampSource
import com.cocode.measureapp.geometry.MeasurementFailureReason as Reason
import com.cocode.measureapp.geometry.SolverKind
import com.cocode.measureapp.geometry.Vec3
import com.cocode.measureapp.geometry.frames.AlignedGravity
import com.cocode.measureapp.geometry.frames.GravityAlignmentReason
import com.cocode.measureapp.geometry.frames.QuarterTurn
import com.cocode.measureapp.ui.CapturedImage
import com.cocode.measureapp.ui.capture.CAMERA_ERROR
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Capture handoff (C01) and the physical-down vector (C02) from callbacks to the marking flow. */
@RunWith(AndroidJUnit4::class)
class CaptureContractsFlowTest {
    @get:Rule val rule = createComposeRule()

    private val f = TargetFixture.frontal
    private val quad = TargetFixture.nonrectangle

    private fun shot(source: Int? = TIMESTAMP_SOURCE_REALTIME) =
        ShotRig.fromUpright(f.bitmap(), f.intrinsics, QuarterTurn.R90, f.down, f.objectPixels, f.stickPixels, source)

    private fun FlowDriver.captureNow(): FakeCamera.Pending {
        click("Capture")
        assertCapture(enabled = false)
        return s.camera.pending.last()
    }

    /** One successful capture with timestamp [source]; returns the image that reached marking. */
    private fun FlowDriver.captureWith(source: Int?): CapturedImage {
        val p = captureNow()
        val shot = shot(source)
        ui { camera.finish(p, shot.outcome(p.id)) }
        assertTrue("every path releases the frame", shot.proxy.closed)
        assertEquals(HostStep.Mark, s.step)
        val img = s.delivered.last()
        assertEquals(p.id, img.metadata.requestId)
        assertEquals(p.id, img.scene.shotId)
        assertEquals(img.scene.imageWidth, img.bitmap.width)
        assertEquals(img.scene.imageHeight, img.bitmap.height)
        assertTrue("metadata was copied before closing", runCatching { shot.proxy.timestamp }.isFailure)
        return img
    }

    @Test fun C01_onlyTheActiveSuccessfulRequestReachesAlignmentAndEveryProxyIsReleased() {
        val d = FlowDriver(rule, FlowState(f.reference, start = HostStep.Capture)).start()
        d.assertCapture(enabled = true)

        val cameraError = d.captureNow()
        d.ui { camera.finish(cameraError, CaptureOutcome.Failure(CAMERA_ERROR)) }
        d.assertShown(CAMERA_ERROR)
        d.assertCapture(enabled = true)

        val converting = d.captureNow()
        val broken = shot().let { Shot(FakeProxy(f.bitmap(), CropRect(0, 0, 10_000, 10), 90, ShotRig.EXPOSURE),
            it.lens, it.kBuffer, it.targetRotation, it.latest, it.recent, it.bufferObject, it.bufferStick) }
        val failed = broken.outcome(converting.id)
        assertTrue(failed is CaptureOutcome.Failure && failed.message == CONVERSION_FAILED)
        assertTrue(broken.proxy.closed)
        d.ui { camera.finish(converting, failed) }
        d.assertShown(CONVERSION_FAILED)
        d.assertCapture(enabled = true)

        val late = d.captureNow()
        d.click("Settings") // navigation disposes the screen that owns the request
        d.back()
        d.assertCapture(enabled = true) // a fresh screen, not the old busy state
        val lateShot = shot()
        d.ui { camera.finish(late, lateShot.outcome(late.id)) }
        assertTrue(lateShot.proxy.closed)
        assertEquals(1, d.s.discarded.size)

        val mismatched = d.captureNow()
        d.ui { camera.finish(mismatched, shot().outcome(mismatched.id + 100)) }
        d.assertShown(CaptureController.MISMATCHED)
        assertEquals(2, d.s.discarded.size)
        assertTrue("nothing reached alignment/marking yet", d.s.delivered.isEmpty())
        assertEquals(HostStep.Capture, d.s.step)

        val realtime = d.captureWith(TIMESTAMP_SOURCE_REALTIME)
        assertEquals(ExposureTimestamp.Available(ShotRig.EXPOSURE, TimestampSource.REALTIME), realtime.metadata.exposure)
        assertTrue(realtime.scene.alignedGravity is AlignedGravity.Available)
        d.click("Retake")
        val unknown = d.captureWith(TIMESTAMP_SOURCE_UNKNOWN)
        assertEquals(ExposureTimestamp.Available(ShotRig.EXPOSURE, TimestampSource.UNKNOWN), unknown.metadata.exposure)
        assertEquals(AlignedGravity.Unavailable(GravityAlignmentReason.CAMERA_CLOCK_NOT_REALTIME), unknown.scene.alignedGravity)
        d.click("Retake")
        val undeclared = d.captureWith(null)
        assertTrue(undeclared.metadata.exposure is ExposureTimestamp.Unavailable)
        assertEquals(AlignedGravity.Unavailable(GravityAlignmentReason.EXPOSURE_TIME_UNAVAILABLE), undeclared.scene.alignedGravity)
        assertEquals(3, d.s.delivered.size)

        // The delivered image and its own metadata measure the page on the marking screen.
        d.ui { place(f.objectPixels, f.stickPixels) }
        Expect.sameResult(f.expected, Expect.success(d.measure()).measurement)
    }

    @Test fun C02_stationaryGravityThroughProviderAndCaptureTransform() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val t = ShotRig.EXPOSURE - 20_000_000L

        fun provider(vararg raw: Float): GravityProvider = GravityProvider(context).also { p ->
            if (raw.isNotEmpty()) p.onSensorChanged(ShotRig.gravityEvent(raw, t))
        }

        fun captured(p: GravityProvider, target: TargetFixture): Pair<Shot, CapturedImage> {
            val s = ShotRig.fromUpright(target.bitmap(), target.intrinsics, QuarterTurn.R90, null,
                target.objectPixels, target.stickPixels, gravity = p.latestSample() to p.recentSamples())
            return s to (s.outcome(7) as CaptureOutcome.Success).payload
        }

        val upright = provider(0f, 9.81f, 0f) // natural portrait, standing still
        val sample = upright.latestSample() as GravitySample.Available
        Expect.vec(Vec3(0.0, -1.0, 0.0), sample.down, 1e-6, "device-axis physical down")
        assertEquals("sensor-event time, not receipt time", t, sample.timestampNanos)
        assertEquals(SensorClock.ELAPSED_REALTIME_NANOS, sample.clock)
        val (uprightShot, uprightImg) = captured(upright, quad)
        val down = (uprightImg.scene.alignedGravity as AlignedGravity.Available).down
        Expect.vec(Vec3(0.0, 1.0, 0.0), down, 1e-9, "upright camera-frame down")

        val flat = provider(0f, 0f, 9.81f) // lying flat, back camera looking at the floor
        val flatDown = (captured(flat, quad).second.scene.alignedGravity as AlignedGravity.Available).down
        assertTrue("looking straight down has positive gravity z: $flatDown", flatDown.z > 0.999)

        val missing = provider()
        assertTrue(missing.latestSample() is GravitySample.Unavailable)
        assertTrue(missing.recentSamples().isEmpty())
        val (missingShot, missingImg) = captured(missing, quad)
        assertEquals(AlignedGravity.Unavailable(GravityAlignmentReason.NO_SAMPLE_AT_OR_BEFORE_EXPOSURE),
            missingImg.scene.alignedGravity)

        val d = FlowDriver(rule, FlowState(quad.reference)).start()
        val (obj, stick) = uprightShot.marks(uprightImg)
        d.load(uprightImg, obj, stick)
        val ok = Expect.success(d.measure())
        assertEquals(SolverKind.GRAVITY, ok.solver)
        Expect.sameResult(quad.expected, ok.measurement)
        d.assertResults()
        d.clickResults("Re-mark")

        val (mObj, mStick) = missingShot.marks(missingImg)
        d.load(missingImg, mObj, mStick)
        val fail = Expect.failure(d.measure(), Reason.METADATA_UNAVAILABLE)
        assertTrue(fail.detail!!.contains("Tilt-sensor method"))
        d.assertMarking()
    }
}
