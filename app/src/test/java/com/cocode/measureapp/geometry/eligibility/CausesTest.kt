package com.cocode.measureapp.geometry.eligibility

import com.cocode.measureapp.allKeys
import com.cocode.measureapp.geometry.rectangle.RectangleRejection
import com.cocode.measureapp.model.TextKey
import org.junit.Assert.assertEquals
import org.junit.Test

class CausesTest {
    @Test fun everyRectangleRejectionHasAMessage() {
        for (rejection in RectangleRejection.entries) {
            assertEquals("$rejection has exactly one message", 1, Causes.of(rejection).allKeys().size)
        }
    }

    @Test fun rejectionsThatAreTheSameForTheCustomerShareOneMessage() {
        val corners = setOf(
            RectangleRejection.INVALID_CORNER_COUNT, RectangleRejection.NON_FINITE_CORNERS,
            RectangleRejection.COINCIDENT_CORNERS, RectangleRejection.ZERO_VANISHING_VECTOR,
            RectangleRejection.COINCIDENT_DIRECTIONS,
        )
        for (r in corners) assertEquals(r.name, TextKey.CAUSE_CORNERS_UNUSABLE, Causes.of(r).key)
        assertEquals(TextKey.CAUSE_NO_LENS_DETAILS, Causes.of(RectangleRejection.INVALID_INTRINSICS).key)
        assertEquals(TextKey.CAUSE_VIEW_TOO_SHALLOW, Causes.of(RectangleRejection.GRAZING_VIEW).key)
        assertEquals(TextKey.CAUSE_VIEW_TOO_SHALLOW, Causes.of(RectangleRejection.PROJECTION_UNUSABLE).key)
        assertEquals(TextKey.CAUSE_SHAPE_TOO_SENSITIVE, Causes.of(RectangleRejection.ILL_CONDITIONED).key)
        assertEquals(TextKey.CAUSE_CALCULATION_UNSTABLE, Causes.of(RectangleRejection.NUMERICAL_FAILURE).key)
    }

    @Test fun adviceFollowsTheCause() {
        val t = Causes.then(Causes.CALCULATION_UNSTABLE, TextKey.ADVICE_CHECK_CORNERS)
        assertEquals(
            listOf(TextKey.SENTENCES, TextKey.CAUSE_CALCULATION_UNSTABLE, TextKey.ADVICE_CHECK_CORNERS),
            t.allKeys(),
        )
    }
}
