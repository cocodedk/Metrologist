package com.cocode.measureapp.core.validation

import com.cocode.measureapp.geometry.MeasurementFailureReason
import com.cocode.measureapp.geometry.MeasurementOutcome
import com.cocode.measureapp.geometry.Vec2
import com.cocode.measureapp.model.TextKey
import com.cocode.measureapp.model.UiText

/** Which marked quadrilateral a validation outcome refers to. */
enum class MarkerTarget { OBJECT, STICK }

/** The first validation rule a marked quadrilateral failed, checked in declaration order. */
enum class MarkerRule {
    POINT_COUNT,
    NON_FINITE,
    COINCIDENT_VERTICES,
    COLLINEAR_VERTICES,
    SELF_CROSSING,
    CONCAVE,
    ZERO_AREA,
}

/** Cyclic direction in the y-down image frame (clockwise = `TL, TR, BR, BL`). */
enum class Winding { CLOCKWISE, COUNTER_CLOCKWISE }

/**
 * Outcome of validating one marked quadrilateral in canonical image-pixel coordinates.
 * An [Accepted] outcome guarantees four finite, distinct corners forming a convex quad with
 * resolved area; it says nothing about real-world right angles.
 */
sealed class MarkerValidation {
    abstract val target: MarkerTarget

    /** Valid marks. Object [corners] are canonical `TL, TR, BR, BL`; stick order is kept. */
    data class Accepted(
        override val target: MarkerTarget,
        val corners: List<Vec2>,
        val winding: Winding,
    ) : MarkerValidation()

    /** Invalid marks: the failed [rule] and a user-actionable [message]. Marks are untouched. */
    data class Rejected(
        override val target: MarkerTarget,
        val rule: MarkerRule,
        val message: UiText,
    ) : MarkerValidation() {
        /** The rejection in plain English for exceptions and logs; the screens show [message]. */
        fun describe(): String = "invalid ${target.name.lowercase()} marks: ${rule.name.lowercase()}"

        /** The shared failure vocabulary for this rejection; never a measurement. */
        fun toFailure(): MeasurementOutcome.Failure = MeasurementOutcome.Failure(
            reason = when (target) {
                MarkerTarget.OBJECT -> MeasurementFailureReason.INVALID_OBJECT_CORNERS
                MarkerTarget.STICK -> MeasurementFailureReason.INVALID_STICK_CORNERS
            },
            detail = message,
        )
    }
}

/** Actionable correction text per target and rule. */
internal object MarkerMessages {
    fun of(target: MarkerTarget, rule: MarkerRule): UiText = UiText(
        when (target) {
            MarkerTarget.OBJECT -> when (rule) {
                MarkerRule.POINT_COUNT -> TextKey.OBJECT_POINT_COUNT
                MarkerRule.NON_FINITE -> TextKey.OBJECT_NON_FINITE
                MarkerRule.COINCIDENT_VERTICES -> TextKey.OBJECT_COINCIDENT
                MarkerRule.COLLINEAR_VERTICES -> TextKey.OBJECT_COLLINEAR
                MarkerRule.SELF_CROSSING -> TextKey.OBJECT_SELF_CROSSING
                MarkerRule.CONCAVE -> TextKey.OBJECT_CONCAVE
                MarkerRule.ZERO_AREA -> TextKey.OBJECT_ZERO_AREA
            }
            MarkerTarget.STICK -> when (rule) {
                MarkerRule.POINT_COUNT -> TextKey.STICK_POINT_COUNT
                MarkerRule.NON_FINITE -> TextKey.STICK_NON_FINITE
                MarkerRule.COINCIDENT_VERTICES -> TextKey.STICK_COINCIDENT
                MarkerRule.COLLINEAR_VERTICES -> TextKey.STICK_COLLINEAR
                MarkerRule.SELF_CROSSING -> TextKey.STICK_SELF_CROSSING
                MarkerRule.CONCAVE -> TextKey.STICK_CONCAVE
                MarkerRule.ZERO_AREA -> TextKey.STICK_ZERO_AREA
            }
        },
    )
}
