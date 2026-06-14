package com.cocode.measureapp.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * TDD spec for [LengthInput] — the pure, unit-aware editable-field helper used by Settings to
 * show a stored meters value in the user's chosen unit and parse their text back to meters,
 * rejecting non-positive / unparseable input.
 */
class LengthInputTest {

    // ---- format: meters -> value-only string in the chosen unit ----

    @Test fun formatMeters() {
        assertEquals("1.5", LengthInput.format(1.5, LengthUnit.METERS))
    }

    @Test fun formatCentimeters() {
        assertEquals("150", LengthInput.format(1.5, LengthUnit.CENTIMETERS))
    }

    @Test fun formatFeetInchesAsDecimalFeet() {
        // 0.3048 m = exactly 1 foot.
        assertEquals("1", LengthInput.format(0.3048, LengthUnit.FEET_INCHES))
    }

    @Test fun formatTrimsTrailingZeros() {
        assertEquals("2", LengthInput.format(2.0, LengthUnit.METERS))
        assertEquals("40", LengthInput.format(0.4, LengthUnit.CENTIMETERS))
    }

    // ---- parseToMeters: user text in unit -> meters, or null ----

    @Test fun parseMetersValue() {
        assertEquals(1.5, LengthInput.parseToMeters("1.5", LengthUnit.METERS)!!, 1e-12)
    }

    @Test fun parseCentimetersValueConvertsToMeters() {
        assertEquals(0.04, LengthInput.parseToMeters("4", LengthUnit.CENTIMETERS)!!, 1e-12)
    }

    @Test fun parseFeetValueConvertsToMeters() {
        assertEquals(0.3048, LengthInput.parseToMeters("1", LengthUnit.FEET_INCHES)!!, 1e-9)
    }

    @Test fun parseRejectsUnparseable() {
        assertNull(LengthInput.parseToMeters("abc", LengthUnit.METERS))
        assertNull(LengthInput.parseToMeters("", LengthUnit.METERS))
    }

    @Test fun parseRejectsNonPositive() {
        assertNull(LengthInput.parseToMeters("0", LengthUnit.METERS))
        assertNull(LengthInput.parseToMeters("-1", LengthUnit.CENTIMETERS))
    }

    @Test fun parseAcceptsWhitespaceAroundNumber() {
        assertEquals(2.0, LengthInput.parseToMeters("  2 ", LengthUnit.METERS)!!, 1e-12)
    }

    // ---- unit label for the field ----

    @Test fun unitLabels() {
        assertEquals("m", LengthInput.unitLabel(LengthUnit.METERS))
        assertEquals("cm", LengthInput.unitLabel(LengthUnit.CENTIMETERS))
        assertEquals("ft", LengthInput.unitLabel(LengthUnit.FEET_INCHES))
    }
}
