package com.cocode.measureapp.core.dimensions

/** Which app-level reference dimension a [ReferenceCheck.NeedsCorrection] refers to. */
enum class ReferenceField { LENGTH, WIDTH }

/**
 * Validated app reference dimensions (metres) or an explicit requirement to correct them.
 * Invalid values are carried only as raw diagnostics, never as accepted settings.
 */
sealed class ReferenceCheck {
    /** Both dimensions are finite and strictly positive; safe to build a measurement profile. */
    data class Valid(val lengthMeters: Double, val widthMeters: Double) : ReferenceCheck()

    /** At least one dimension is invalid; measurement must be prevented until it is corrected. */
    data class NeedsCorrection(
        val fields: Set<ReferenceField>,
        val rawLengthMeters: Double,
        val rawWidthMeters: Double,
    ) : ReferenceCheck() {
        init {
            require(fields.isNotEmpty()) { "NeedsCorrection requires at least one field" }
        }

        /** User-facing explanation naming each dimension that needs correction. */
        val message: String
            get() = fields.sortedBy { it.ordinal }.joinToString(" ") {
                ReferenceDimensions.correctionMessage(it)
            }
    }
}

/**
 * Single source of truth for reference-dimension validity at the input, storage and profile
 * boundaries. The app's length and width must both be finite and strictly positive in metres;
 * the engine-only "unknown width = 0" is deliberately NOT accepted here.
 */
object ReferenceDimensions {
    /** True when [meters] is a finite, strictly positive length. */
    fun isValidMeters(meters: Double): Boolean = meters.isFinite() && meters > 0.0

    /** Checks [lengthMeters] and [widthMeters] together, naming every invalid dimension. */
    fun check(lengthMeters: Double, widthMeters: Double): ReferenceCheck {
        val bad = buildSet {
            if (!isValidMeters(lengthMeters)) add(ReferenceField.LENGTH)
            if (!isValidMeters(widthMeters)) add(ReferenceField.WIDTH)
        }
        return if (bad.isEmpty()) {
            ReferenceCheck.Valid(lengthMeters, widthMeters)
        } else {
            ReferenceCheck.NeedsCorrection(bad, lengthMeters, widthMeters)
        }
    }

    /**
     * Interprets persisted values: an absent key (`null`) means "never set" and takes the
     * documented default; a present but invalid value is flagged, never replaced.
     */
    fun fromStored(
        storedLength: Double?,
        storedWidth: Double?,
        defaultLength: Double,
        defaultWidth: Double,
    ): ReferenceCheck = check(storedLength ?: defaultLength, storedWidth ?: defaultWidth)

    /** Guards a storage write: returns [meters] or throws for a non-finite / non-positive value. */
    fun requireValid(field: ReferenceField, meters: Double): Double {
        require(isValidMeters(meters)) { "${correctionMessage(field)} Got $meters." }
        return meters
    }

    /** Correction text for one [field]. */
    fun correctionMessage(field: ReferenceField): String = when (field) {
        ReferenceField.LENGTH -> "Reference stick length must be a finite positive number."
        ReferenceField.WIDTH -> "Reference stick width must be a finite positive number."
    }
}
