package com.cocode.measureapp.core

import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt

enum class LengthUnit { METERS, CENTIMETERS, FEET_INCHES }

object Units {
    private const val METERS_PER_INCH = 0.0254
    private const val INCHES_PER_FOOT = 12
    private const val SQ_M_TO_SQ_FT = 10.7639

    fun formatLength(meters: Double, unit: LengthUnit): String = when (unit) {
        LengthUnit.METERS -> String.format(Locale.US, "%.2f m", meters)
        LengthUnit.CENTIMETERS -> String.format(Locale.US, "%.1f cm", meters * 100)
        LengthUnit.FEET_INCHES -> formatFeetInches(meters)
    }

    fun formatArea(squareMeters: Double, unit: LengthUnit): String = when (unit) {
        LengthUnit.METERS -> String.format(Locale.US, "%.2f m²", squareMeters)
        LengthUnit.CENTIMETERS -> String.format(Locale.US, "%.0f cm²", squareMeters * 10000)
        LengthUnit.FEET_INCHES -> String.format(Locale.US, "%.2f ft²", squareMeters * SQ_M_TO_SQ_FT)
    }

    private fun formatFeetInches(meters: Double): String {
        val totalInches = meters / METERS_PER_INCH
        var feet = floor(totalInches / INCHES_PER_FOOT).toInt()
        var inches = (totalInches - feet * INCHES_PER_FOOT).roundToInt()
        if (inches == INCHES_PER_FOOT) {
            feet += 1
            inches = 0
        }
        return String.format(Locale.US, "%d' %d\"", feet, inches)
    }
}
