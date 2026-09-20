package com.cocode.measureapp.core

import com.cocode.measureapp.geometry.MeasurementFailureReason
import com.cocode.measureapp.geometry.MeasurementOutcome
import com.cocode.measureapp.geometry.MeasurementResult
import com.cocode.measureapp.geometry.ScaleResult
import com.cocode.measureapp.geometry.SolverKind
import com.cocode.measureapp.geometry.SurfaceOrientation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** C15/C11: revision-bound invalidation — stale or failed attempts never leave a usable result. */
class MeasurementSessionContractTest {
    private fun success(width: Double = 1.2) = MeasurementOutcome.Success(
        MeasurementResult(width, 0.8, width * 0.8, 1.5, listOf(90.0, 90.0, 90.0, 90.0)),
        SolverKind.RECTANGLE,
        SurfaceOrientation.VERTICAL,
        ScaleResult(2.0, 0.01),
        0.9,
    )

    private val stickFailure = MeasurementOutcome.Failure(
        MeasurementFailureReason.INVALID_STICK_CORNERS, "stick box collapsed",
    )

    @Test
    fun freshSessionHasNothingToShowOrExport() {
        val session = MeasurementSession()
        assertNull(session.usableResult)
        assertNull(session.failure)
        assertFalse(session.exportEnabled)
    }

    @Test
    fun successAtCurrentRevisionIsUsableAndExportable() {
        val ok = success()
        val session = MeasurementSession().let { it.complete(it.revision, ok) }
        assertSame(ok, session.usableResult)
        assertTrue(session.exportEnabled)
    }

    @Test
    fun invalidateClearsResultAndExportAndAdvancesRevision() {
        val measured = MeasurementSession().let { it.complete(it.revision, success()) }
        val invalidated = measured.invalidate()
        assertEquals(measured.revision + 1, invalidated.revision)
        assertNull(invalidated.usableResult)
        assertNull(invalidated.lastOutcome)
        assertFalse(invalidated.exportEnabled)
    }

    @Test
    fun lateCompletionFromOlderRevisionCannotRestoreStaleResult() {
        // Measure, change orientation (invalidate), then the old completion arrives.
        val start = MeasurementSession()
        val oldRevision = start.revision
        val changed = start.complete(oldRevision, success()).invalidate()
        val afterLate = changed.complete(oldRevision, success(width = 3.0))
        assertSame(changed, afterLate)
        assertNull(afterLate.usableResult)
        assertFalse(afterLate.exportEnabled)

        val fresh = success(width = 1.3)
        val afterNew = afterLate.complete(afterLate.revision, fresh)
        assertSame(fresh, afterNew.usableResult)
        assertTrue(afterNew.exportEnabled)
    }

    @Test
    fun completionForUnknownFutureRevisionIsIgnored() {
        val session = MeasurementSession()
        assertSame(session, session.complete(session.revision + 1, success()))
    }

    @Test
    fun failureAfterEditNeverExposesPreviousSuccess() {
        val measured = MeasurementSession().let { it.complete(it.revision, success()) }
        val edited = measured.invalidate()
        val failed = edited.complete(edited.revision, stickFailure)
        assertNull(failed.usableResult)
        assertFalse(failed.exportEnabled)
        assertEquals(MeasurementFailureReason.INVALID_STICK_CORNERS, failed.failure?.reason)
    }

    @Test
    fun failureAtSameRevisionReplacesEarlierSuccess() {
        val measured = MeasurementSession().let { it.complete(it.revision, success()) }
        val failed = measured.complete(measured.revision, stickFailure)
        assertNull(failed.usableResult)
        assertFalse(failed.exportEnabled)
        assertSame(stickFailure, failed.failure)
    }

    @Test
    fun correctionAfterFailureSucceedsWithoutNewSession() {
        val failed = MeasurementSession().let { it.complete(it.revision, stickFailure) }
        val corrected = failed.invalidate()
        assertNull(corrected.failure)
        val ok = success(width = 1.25)
        val retried = corrected.complete(corrected.revision, ok)
        assertSame(ok, retried.usableResult)
        assertNull(retried.failure)
        assertTrue(retried.exportEnabled)
    }

    @Test
    fun retakeAfterFailureDoesNotCarryErrorOrResult() {
        val failed = MeasurementSession().let { it.complete(it.revision, stickFailure) }
        val retake = failed.invalidate()
        assertNull(retake.lastOutcome)
        assertNull(retake.failure)
        assertFalse(retake.exportEnabled)
        // A late completion from the failed image cannot re-enter the new capture.
        assertSame(retake, retake.complete(failed.revision, success()))
    }

    @Test
    fun repeatedInvalidationRejectsEveryEarlierRevision() {
        var session = MeasurementSession()
        val revisions = mutableListOf<Int>()
        repeat(3) {
            revisions += session.revision
            session = session.invalidate()
        }
        assertEquals(revisions.toSet().size, revisions.size)
        for (old in revisions) assertSame(session, session.complete(old, success()))
    }
}
