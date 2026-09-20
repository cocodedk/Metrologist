package com.cocode.measureapp.core.validation

import com.cocode.measureapp.geometry.MeasurementFailureReason
import com.cocode.measureapp.geometry.MeasurementOutcome
import com.cocode.measureapp.geometry.Vec2

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
        val message: String,
    ) : MarkerValidation() {
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
    fun of(target: MarkerTarget, rule: MarkerRule): String {
        val what = if (target == MarkerTarget.OBJECT) "object corners" else "stick box corners"
        val fix = if (target == MarkerTarget.OBJECT) {
            "place one on each corner of the object"
        } else {
            "drag them around the stick so opposite sides stay opposite"
        }
        return when (rule) {
            MarkerRule.POINT_COUNT -> "Mark exactly 4 $what"
            MarkerRule.NON_FINITE -> "Some $what are not valid image positions; $fix"
            MarkerRule.COINCIDENT_VERTICES ->
                "The 4 $what must be distinct: two of them overlap; spread them apart"
            MarkerRule.COLLINEAR_VERTICES -> "Three or more $what lie on one line; $fix"
            MarkerRule.SELF_CROSSING -> "The $what cross over each other; $fix"
            MarkerRule.CONCAVE -> "One of the $what sits inside the others; $fix"
            MarkerRule.ZERO_AREA -> "The $what enclose no area; $fix"
        }
    }
}
