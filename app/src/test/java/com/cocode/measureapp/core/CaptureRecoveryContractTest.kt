package com.cocode.measureapp.core

import com.cocode.measureapp.capture.gravity.GravitySample
import com.cocode.measureapp.capture.gravity.GravityUnavailableReason
import com.cocode.measureapp.capture.recovery.CalibrationQuality
import com.cocode.measureapp.capture.recovery.CaptureController
import com.cocode.measureapp.capture.recovery.CaptureMetadata
import com.cocode.measureapp.capture.recovery.CaptureOutcome
import com.cocode.measureapp.capture.recovery.CropRect
import com.cocode.measureapp.capture.recovery.ExposureTimestamp
import com.cocode.measureapp.capture.recovery.IncompleteCaptureException
import com.cocode.measureapp.capture.recovery.TimestampSource
import com.cocode.measureapp.capture.recovery.consumeFrame
import com.cocode.measureapp.capture.recovery.exposureTimestampOf
import com.cocode.measureapp.geometry.MeasurementFailureReason
import com.cocode.measureapp.geometry.MeasurementOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** N08/C01: every capture request ends once, releases its frame and restores retry. */
class CaptureRecoveryContractTest {
    /** Stand-in for an `ImageProxy`: metadata becomes unreadable once closed. */
    private class FakeFrame(val timestamp: Long = 5_000_000_123L, val source: Int? = 1) : AutoCloseable {
        var closed = 0
        fun assertOpen() = check(closed == 0) { "frame read after close" }
        override fun close() { closed++ }
    }

    private data class Shot(val metadata: CaptureMetadata, var discarded: Boolean = false)

    private fun metadata(id: Int, frame: FakeFrame, rotation: Int = 90): CaptureMetadata {
        frame.assertOpen()
        return CaptureMetadata(
            id, 4000, 3000, rotation, CropRect(0, 0, 4000, 3000),
            exposureTimestampOf(frame.timestamp, frame.source),
            GravitySample.Unavailable(GravityUnavailableReason.NO_SAMPLE_YET), emptyList(),
            CalibrationQuality.APPROXIMATE,
        )
    }

    /** Mirrors `onCaptureSuccess`: copy metadata, convert, close, then complete on main. */
    private fun succeed(c: CaptureController<Shot>, id: Int, frame: FakeFrame, shotId: Int = id) =
        c.complete(id, consumeFrame(frame) { Shot(metadata(shotId, frame).also { it.requireComplete(4000, 3000) }) })

    private val delivered = mutableListOf<Shot>()
    private fun screen() = CaptureController<Shot>(discard = { it.discarded = true }, requestIdOf = { it.metadata.requestId })
    private fun CaptureController<Shot>.retryEnabled() = state.canCapture(permissionGranted = true, cameraReady = true)

    @Test
    fun C01_activeCaptureIsBusyAndBlocksSecondRequest() {
        val c = screen()
        val id = c.begin()
        assertNotNull(id)
        assertTrue(c.state.busy)
        assertFalse(c.retryEnabled())
        assertNull(c.begin())
        assertNull(c.launch { error("must not start a duplicate capture") })
    }

    @Test
    fun C01_successDeliversOneMatchingImageWithExposureMetadataAfterClose() {
        val c = screen(); val frame = FakeFrame()
        val id = c.begin()!!
        val shot = succeed(c, id, frame)!!.also(delivered::add)
        assertEquals(1, frame.closed)
        assertEquals(id, shot.metadata.requestId)
        // Survives proxy closure; exposure time is the frame's value, not callback time.
        assertEquals(ExposureTimestamp.Available(5_000_000_123L, TimestampSource.REALTIME), shot.metadata.exposure)
        assertTrue(shot.metadata.gravity is GravitySample.Unavailable)
        assertFalse(c.state.busy)
        assertTrue(c.retryEnabled())
        assertNull("a repeated callback is not a second success", succeed(c, id, FakeFrame()))
        assertEquals(1, delivered.size)
    }

    @Test
    fun C01_cameraErrorShowsMessageThenRetrySucceedsOnSameScreen() {
        val c = screen()
        val first = c.begin()!!
        assertNull(c.complete(first, CaptureOutcome.Failure("Capture failed. Try again.")))
        assertEquals("Capture failed. Try again.", c.state.error)
        assertTrue(c.retryEnabled())
        assertFalse("retry still requires a ready camera", c.state.canCapture(true, cameraReady = false))
        assertFalse("retry still requires permission", c.state.canCapture(false, cameraReady = true))
        val second = c.begin()!!
        assertNull("a new attempt clears the old message", c.state.error)
        assertEquals(second, succeed(c, second, FakeFrame())!!.metadata.requestId)
        assertTrue(c.retryEnabled())
    }

    @Test
    fun C01_conversionOrMetadataFailureClosesFrameAndRestoresRetry() {
        val c = screen()
        val id = c.begin()!!; val frame = FakeFrame()
        val outcome = consumeFrame<Shot>(frame) { error("toBitmap failed") }
        assertTrue(outcome is CaptureOutcome.Failure)
        assertNull(c.complete(id, outcome))
        assertEquals(1, frame.closed)
        assertNotNull(c.state.error); assertTrue(c.retryEnabled())

        val next = c.begin()!!; val badFrame = FakeFrame()
        val incomplete = consumeFrame(badFrame) { Shot(metadata(next, badFrame, rotation = 45).also { it.requireComplete(4000, 3000) }) }
        assertTrue((incomplete as CaptureOutcome.Failure).cause is IncompleteCaptureException)
        assertNull(c.complete(next, incomplete))
        assertEquals(1, badFrame.closed)
        assertTrue(c.retryEnabled())
    }

    @Test
    fun C01_synchronousStartFailureClearsBusyWithoutFabricatedSuccess() {
        val c = screen()
        val id = c.launch { throw IllegalStateException("camera not bound") }
        assertNotNull(id)
        assertFalse(c.state.busy)
        assertEquals(CaptureController.START_FAILED, c.state.error)
        assertNull("a late callback for the failed start is not delivered", succeed(c, id!!, FakeFrame()))
        assertTrue(c.retryEnabled())
    }

    @Test
    fun C01_lateCallbackAfterLeavingScreenIsDiscardedAndRetakeStartsFresh() {
        val old = screen(); val frame = FakeFrame()
        val id = old.begin()!!
        old.dispose()
        assertFalse("executor must outlive the pending request", old.released)
        val late = consumeFrame(frame) { Shot(metadata(id, frame)) }
        assertNull(old.complete(id, late))
        assertEquals(1, frame.closed)
        assertTrue((late as CaptureOutcome.Success<Shot>).payload.discarded)
        assertTrue(old.released)

        val retake = screen()   // new screen instance: no inherited busy flag
        assertTrue(retake.retryEnabled())
        assertNotNull(succeed(retake, retake.begin()!!, FakeFrame()))
    }

    @Test
    fun C01_shotFromAnotherRequestIsRejectedNotMixed() {
        val c = screen()
        val id = c.begin()!!
        val frame = FakeFrame()
        val outcome = consumeFrame(frame) { Shot(metadata(id + 7, frame)) }
        assertNull(c.complete(id, outcome))
        assertTrue((outcome as CaptureOutcome.Success<Shot>).payload.discarded)
        assertEquals(CaptureController.MISMATCHED, c.state.error)
        assertTrue(c.retryEnabled())
    }

    @Test
    fun C01_cancellationIsTerminalAndClearsBusy() {
        val c = screen()
        assertNull(c.complete(c.begin()!!, CaptureOutcome.Cancelled))
        assertFalse(c.state.busy); assertNull(c.state.error); assertTrue(c.retryEnabled())
    }

    @Test
    fun C01_timestampSourcesArePreservedOrExplicitlyUnavailable() {
        assertEquals(ExposureTimestamp.Available(42L, TimestampSource.REALTIME), exposureTimestampOf(42L, 1))
        assertEquals(ExposureTimestamp.Available(42L, TimestampSource.UNKNOWN), exposureTimestampOf(42L, 0))
        assertTrue(exposureTimestampOf(0L, 1) is ExposureTimestamp.Unavailable)
        assertTrue(exposureTimestampOf(42L, null) is ExposureTimestamp.Unavailable)
        assertTrue(exposureTimestampOf(42L, 9) is ExposureTimestamp.Unavailable)
        // Unavailable exposure time is optional: the capture still succeeds, flagged for node 02.
        val c = screen(); val frame = FakeFrame(timestamp = 0L, source = null)
        val shot = succeed(c, c.begin()!!, frame)!!
        assertTrue(shot.metadata.exposure is ExposureTimestamp.Unavailable)
    }

    @Test
    fun C11_retakeAfterFailedMeasurementInvalidatesAndCapturesFreshly() {
        var session = MeasurementSession()
        session = session.complete(session.revision, MeasurementOutcome.Failure(
            MeasurementFailureReason.INVALID_STICK_CORNERS, "stick box collapsed",
        ))
        val stale = screen().apply { begin() }   // old screen left busy
        stale.dispose()
        session = session.invalidate()
        assertNull(session.lastOutcome)
        val fresh = screen()
        val shot = succeed(fresh, fresh.begin()!!, FakeFrame())
        assertNotNull(shot); assertFalse(fresh.state.busy)
    }
}
