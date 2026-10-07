package com.cocode.measureapp.core.measurement

import com.cocode.measureapp.SOME_TEXT
import com.cocode.measureapp.allKeys
import com.cocode.measureapp.geometry.MeasurementFailureReason
import com.cocode.measureapp.geometry.MeasurementOutcome
import com.cocode.measureapp.model.TextKey
import org.junit.Assert.assertEquals
import org.junit.Test

class CorrectionTextTest {
    @Test fun everyReasonHasItsOwnHeadline() {
        val keys = MeasurementFailureReason.entries.map { CorrectionText.headline(it).key }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test fun aFailureWithoutDetailShowsOnlyTheHeadline() {
        val failure = MeasurementOutcome.Failure(MeasurementFailureReason.INVALID_STICK_CORNERS)
        assertEquals(CorrectionText.headline(failure.reason), CorrectionText.message(failure))
    }

    @Test fun theDetailFollowsTheHeadline() {
        val failure = MeasurementOutcome.Failure(MeasurementFailureReason.NUMERICAL_FAILURE, SOME_TEXT)
        assertEquals(
            listOf(TextKey.SENTENCES, TextKey.FIX_UNSTABLE, SOME_TEXT.key),
            CorrectionText.message(failure).allKeys(),
        )
    }
}
