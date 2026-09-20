package com.cocode.measureapp.core.validation

import com.cocode.measureapp.core.CornerOrdering
import com.cocode.measureapp.geometry.Vec2

/**
 * Validates marked quadrilaterals in canonical image-pixel coordinates before any projection.
 * Rejections never move, replace or invent a corner; the caller keeps the user's marks.
 */
object MarkerValidator {
    /**
     * Object marks in any tap order. Accepted corners are canonical clockwise
     * `TL, TR, BR, BL`; convex non-rectangles are valid input.
     */
    fun validateObject(points: List<Vec2>): MarkerValidation {
        QuadRules.inputRule(points)?.let { return reject(MarkerTarget.OBJECT, it) }
        val cycle = CornerOrdering.clockwiseCycle(points)
        QuadRules.cycleRule(cycle)?.let { return reject(MarkerTarget.OBJECT, it) }
        return MarkerValidation.Accepted(MarkerTarget.OBJECT, cycle, QuadRules.winding(cycle))
    }

    /**
     * Stick box marks in their given cyclic order, which defines the opposite-edge pairs
     * (`0-1`/`2-3` and `1-2`/`3-0`). Both windings are accepted and the order is returned
     * unchanged; a crossed or collapsed box is rejected rather than reordered.
     */
    fun validateStick(box: List<Vec2>): MarkerValidation {
        QuadRules.inputRule(box)?.let { return reject(MarkerTarget.STICK, it) }
        QuadRules.cycleRule(box)?.let { return reject(MarkerTarget.STICK, it) }
        return MarkerValidation.Accepted(MarkerTarget.STICK, box.toList(), QuadRules.winding(box))
    }

    /** Validates both quads; the object's rejection is reported first. */
    fun validateMarks(objectPoints: List<Vec2>, stickBox: List<Vec2>): MarkValidation {
        val obj = validateObject(objectPoints)
        val stick = validateStick(stickBox)
        return when {
            obj is MarkerValidation.Rejected -> MarkValidation.Rejected(obj)
            stick is MarkerValidation.Rejected -> MarkValidation.Rejected(stick)
            else -> MarkValidation.Accepted(
                (obj as MarkerValidation.Accepted).corners,
                (stick as MarkerValidation.Accepted).corners,
            )
        }
    }

    private fun reject(target: MarkerTarget, rule: MarkerRule) =
        MarkerValidation.Rejected(target, rule, MarkerMessages.of(target, rule))
}

/** Combined outcome for one measure attempt's object and stick marks. */
sealed class MarkValidation {
    /** Canonical object corners and the stick box in its original cyclic order. */
    data class Accepted(val objectCorners: List<Vec2>, val stickBox: List<Vec2>) : MarkValidation()

    /** The first failing quad's structured rejection. */
    data class Rejected(val rejection: MarkerValidation.Rejected) : MarkValidation()
}
