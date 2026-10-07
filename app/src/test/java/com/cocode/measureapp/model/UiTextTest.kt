package com.cocode.measureapp.model

import com.cocode.measureapp.allKeys
import com.cocode.measureapp.mentions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiTextTest {
    @Test fun aMessageWithoutValuesHasNoArguments() {
        assertEquals(emptyList<UiText.TextArg>(), UiText(TextKey.CONFIDENCE_HIGH).args)
    }

    @Test fun numbersKeepTheirOrder() {
        val t = UiText.withNumbers(TextKey.CAUSE_STICK_DISAGREES, 12.5, 8.0)
        assertEquals(listOf(UiText.TextArg.Number(12.5), UiText.TextArg.Number(8.0)), t.args)
    }

    @Test fun nestedMessagesAreFoundOutermostFirst() {
        val inner = UiText(TextKey.CAUSE_NO_TILT_READING)
        val outer = UiText.withMessages(TextKey.TILT_METHOD_PROBLEM, inner)
        assertEquals(listOf(TextKey.TILT_METHOD_PROBLEM, TextKey.CAUSE_NO_TILT_READING), outer.allKeys())
        assertTrue(outer mentions TextKey.CAUSE_NO_TILT_READING)
        assertFalse(outer mentions TextKey.CAUSE_NOT_A_WALL)
    }

    @Test fun numbersAreNotMessages() {
        assertEquals(listOf(TextKey.CAUSE_NOT_A_WALL), UiText.withNumbers(TextKey.CAUSE_NOT_A_WALL, 12.0).allKeys())
    }
}
