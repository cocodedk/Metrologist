package com.cocode.measureapp.geometry

import com.cocode.measureapp.geometry.eligibility.Assessment
import com.cocode.measureapp.geometry.eligibility.PlaneAssumption

/** Outcome of ranking the ELIGIBLE candidates; ineligible ones never re-enter. */
sealed interface Selection {
    /** The [chosen] candidate and a human-readable [reason]. */
    data class Chosen(val chosen: Assessment.Eligible, val reason: String) : Selection

    /** Neither candidate is eligible; both verdicts are kept for an explanatory failure. */
    data class NoneEligible(val rectangle: Assessment.Ineligible, val gravity: Assessment.Ineligible) : Selection
}

/**
 * Ranks rectangle and gravity candidates AFTER eligibility and surface checks.
 *
 * 1. Only [Assessment.Eligible] candidates are ranked; there is no low-confidence fallback.
 * 2. A plane whose orientation is only assumed (wall azimuth) ranks below a resolved one.
 * 3. Otherwise the higher confidence wins; ties prefer the rectangle.
 * 4. With none eligible the result is [Selection.NoneEligible], never a placeholder plane.
 */
object SolverSelector {
    fun select(rectangle: Assessment, gravity: Assessment): Selection {
        val eligible = listOf(rectangle, gravity).filterIsInstance<Assessment.Eligible>()
        if (eligible.isEmpty()) {
            return Selection.NoneEligible(rectangle as Assessment.Ineligible, gravity as Assessment.Ineligible)
        }
        val best = eligible.sortedWith(
            compareBy<Assessment.Eligible>({ it.assumption == PlaneAssumption.WALL_FACES_CAMERA })
                .thenByDescending { it.confidence }
                .thenBy { it.solver != SolverKind.RECTANGLE },
        ).first()
        val other = listOf(rectangle, gravity).first { it !== best }
        val why = when (other) {
            is Assessment.Ineligible -> "other method ineligible: ${other.detail}"
            is Assessment.Eligible -> "ranked above ${other.solver} (${other.assumption}, confidence ${other.confidence})"
        }
        return Selection.Chosen(best, "${best.solver} (${best.assumption}); $why")
    }
}
