package com.cocode.measureapp.core

import com.cocode.measureapp.core.dimensions.ReferenceDimensions
import java.util.Locale

/**
 * Pure, unit-aware helper for a single editable length field (Settings). Converts a stored
 * meters value to a compact value-only string in the user's [LengthUnit] and parses their text
 * back to meters, rejecting unparseable, non-finite or non-positive input (returns `null`).
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

    /**
     * Parses [text] as a number in [unit] to meters, or `null` unless the value is finite and
     * strictly positive both as typed and after conversion (rejects NaN, infinities, overflow
     * such as `1e309`, and tiny values that underflow to zero meters).
     */
    fun parseToMeters(text: String, unit: LengthUnit): Double? {
        val value = text.trim().toDoubleOrNull() ?: return null
        if (!ReferenceDimensions.isValidMeters(value)) return null
        val meters = value * perUnit(unit)
        return meters.takeIf(ReferenceDimensions::isValidMeters)
    }

    /** Correction shown in the editor while [text] is not a valid length. */
    const val CORRECTION_MESSAGE = "Enter a finite positive number"

    private fun trim(value: Double): String {
        val s = String.format(Locale.US, "%.4f", value)
        return s.trimEnd('0').trimEnd('.')
    }
}
