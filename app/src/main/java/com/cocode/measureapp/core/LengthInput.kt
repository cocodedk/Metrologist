package com.cocode.measureapp.core

import java.util.Locale

/**
 * Pure, unit-aware helper for a single editable length field (Settings). Converts a stored
 * meters value to a compact value-only string in the user's [LengthUnit] and parses their text
 * back to meters, rejecting unparseable or non-positive input (returns `null`).
 *
 * Feet are handled as decimal feet (e.g. `1.5` ft) for a simple single-field editor; display
 * elsewhere still uses the richer feet-inches formatting in [Units].
 */
object LengthInput {
    private const val METERS_PER_CM = 0.01
    private const val METERS_PER_FOOT = 0.3048

    private fun perUnit(unit: LengthUnit): Double = when (unit) {
        LengthUnit.METERS -> 1.0
        LengthUnit.CENTIMETERS -> METERS_PER_CM
        LengthUnit.FEET_INCHES -> METERS_PER_FOOT
    }

    /** Short label for the field's unit. */
    fun unitLabel(unit: LengthUnit): String = when (unit) {
        LengthUnit.METERS -> "m"
        LengthUnit.CENTIMETERS -> "cm"
        LengthUnit.FEET_INCHES -> "ft"
    }

    /** Stored [meters] as a compact value-only string in [unit] (trailing zeros trimmed). */
    fun format(meters: Double, unit: LengthUnit): String = trim(meters / perUnit(unit))

    /** Parses [text] as a number in [unit] to meters, or `null` if unparseable / non-positive. */
    fun parseToMeters(text: String, unit: LengthUnit): Double? {
        val value = text.trim().toDoubleOrNull() ?: return null
        if (value <= 0.0) return null
        return value * perUnit(unit)
    }

    private fun trim(value: Double): String {
        val s = String.format(Locale.US, "%.4f", value)
        return s.trimEnd('0').trimEnd('.')
    }
}
