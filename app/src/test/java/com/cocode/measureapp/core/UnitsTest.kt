package com.cocode.measureapp.core

import org.junit.Assert.assertEquals
import org.junit.Test

class UnitsTest {
    // --- formatLength: METERS ("%.2f m") ---
    @Test fun lengthMetersOne() {
        assertEquals("1.00 m", Units.formatLength(1.0, LengthUnit.METERS))
    }

    @Test fun lengthMetersZero() {
        assertEquals("0.00 m", Units.formatLength(0.0, LengthUnit.METERS))
    }

    @Test fun lengthMetersLarge() {
        assertEquals("12345.68 m", Units.formatLength(12345.678, LengthUnit.METERS))
    }

    // --- formatLength: CENTIMETERS (meters * 100, "%.1f cm") ---
    @Test fun lengthCentimetersOne() {
        assertEquals("100.0 cm", Units.formatLength(1.0, LengthUnit.CENTIMETERS))
    }

    @Test fun lengthCentimetersZero() {
        assertEquals("0.0 cm", Units.formatLength(0.0, LengthUnit.CENTIMETERS))
    }

    @Test fun lengthCentimetersFraction() {
        assertEquals("250.0 cm", Units.formatLength(2.5, LengthUnit.CENTIMETERS))
    }

    // --- formatLength: FEET_INCHES ---
    @Test fun lengthFeetInchesOne() {
        // totalInches = 39.370..., feet = 3, inches = round(3.37) = 3
        assertEquals("3' 3\"", Units.formatLength(1.0, LengthUnit.FEET_INCHES))
    }

    @Test fun lengthFeetInchesZero() {
        assertEquals("0' 0\"", Units.formatLength(0.0, LengthUnit.FEET_INCHES))
    }

    @Test fun lengthFeetInchesLarge() {
        // totalInches = 3937.0..., feet = 328, inches = round(1.0) = 1
        assertEquals("328' 1\"", Units.formatLength(100.0, LengthUnit.FEET_INCHES))
    }

    // The inch-rounding CARRY-to-12 branch: inches round up to 12 -> roll to next foot.
    @Test fun lengthFeetInchesCarryToNextFoot() {
        // 0.29464 m -> totalInches = 11.6, feet = 0, inches = round(11.6) = 12 -> carry
        assertEquals("1' 0\"", Units.formatLength(0.29464, LengthUnit.FEET_INCHES))
    }

    // --- formatArea: METERS ("%.2f m²") ---
    @Test fun areaMetersOne() {
        assertEquals("1.00 m²", Units.formatArea(1.0, LengthUnit.METERS))
    }

    @Test fun areaMetersZero() {
        assertEquals("0.00 m²", Units.formatArea(0.0, LengthUnit.METERS))
    }

    @Test fun areaMetersLarge() {
        assertEquals("100.00 m²", Units.formatArea(100.0, LengthUnit.METERS))
    }

    // --- formatArea: CENTIMETERS (* 10000, "%.0f cm²") ---
    @Test fun areaCentimetersOne() {
        assertEquals("10000 cm²", Units.formatArea(1.0, LengthUnit.CENTIMETERS))
    }

    @Test fun areaCentimetersZero() {
        assertEquals("0 cm²", Units.formatArea(0.0, LengthUnit.CENTIMETERS))
    }

    // --- formatArea: FEET_INCHES (* 10.7639, "%.2f ft²") ---
    @Test fun areaFeetInchesOne() {
        assertEquals("10.76 ft²", Units.formatArea(1.0, LengthUnit.FEET_INCHES))
    }

    @Test fun areaFeetInchesZero() {
        assertEquals("0.00 ft²", Units.formatArea(0.0, LengthUnit.FEET_INCHES))
    }

    @Test fun areaFeetInchesLarge() {
        // 100 * 10.7639 = 1076.39
        assertEquals("1076.39 ft²", Units.formatArea(100.0, LengthUnit.FEET_INCHES))
    }

    // Exercise every generated enum member for coverage.
    @Test fun enumMembersAreReachable() {
        assertEquals(3, LengthUnit.values().size)
        assertEquals(LengthUnit.METERS, LengthUnit.valueOf("METERS"))
        assertEquals(LengthUnit.CENTIMETERS, LengthUnit.valueOf("CENTIMETERS"))
        assertEquals(LengthUnit.FEET_INCHES, LengthUnit.valueOf("FEET_INCHES"))
    }
}
