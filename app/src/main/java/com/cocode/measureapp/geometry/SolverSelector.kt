package com.cocode.measureapp.geometry

/** A chosen [PlaneSolution] together with a human-readable [reason] for the choice. */
data class Selection(val solution: PlaneSolution, val reason: String)

/**
 * Auto-picks between the rectangle and gravity plane solvers.
 *
 * Three-way logic:
 * 1. Rectangle when it is present and confidently conditioned
 *    (`confidence >= minRectangleConfidence`).
 * 2. Otherwise gravity when it is present and usable (`confidence > 0`).
 * 3. Otherwise the higher-confidence non-null solution (rectangle preferred on ties).
 *
 * Both inputs being null is impossible in the pipeline: [GravitySolver] always returns a
 * value. The third branch still tolerates a null on either side defensively.
 */
object SolverSelector {
    fun select(
        rectangle: PlaneSolution?,
        gravity: PlaneSolution?,
        minRectangleConfidence: Double = 0.15,
    ): Selection {
        if (rectangle != null && rectangle.confidence >= minRectangleConfidence) {
            return Selection(rectangle, "rectangle: corners well-conditioned")
        }
        if (gravity != null && gravity.confidence > 0.0) {
            val rectConf = rectangle?.confidence
            return Selection(gravity, "fallback: rectangle null/low-confidence ($rectConf)")
        }
        return betterNonNull(rectangle, gravity)
    }

    /** Pick the higher-confidence non-null solution; ties (and equal confidence) favor rectangle. */
    private fun betterNonNull(rectangle: PlaneSolution?, gravity: PlaneSolution?): Selection {
        val rectConf = rectangle?.confidence ?: Double.NEGATIVE_INFINITY
        val gravConf = gravity?.confidence ?: Double.NEGATIVE_INFINITY
        val chosen = if (rectangle != null && rectConf >= gravConf) rectangle else gravity!!
        val reason = "low overall confidence: chose ${chosen.solver} (${chosen.confidence})"
        return Selection(chosen, reason)
    }
}
