package com.cocode.measureapp.core

import com.cocode.measureapp.geometry.MeasurementOutcome

/**
 * Revision-bound measurement state owned at the `MeasureApp` state boundary.
 *
 * [revision] identifies the current image/input revision. [invalidate] is the single shared
 * invalidation operation: mark edits, settings changes, surface changes and Retake all route
 * through it rather than applying their own stale-result policies. The photo, marks and
 * orientation live outside this type and are retained by the caller; only the outcome and
 * export eligibility are cleared here.
 *
 * Immutable so it can be held directly in Compose state.
 */
data class MeasurementSession(
    val revision: Int = 0,
    val lastOutcome: MeasurementOutcome? = null,
) {
    /** The success bound to the current revision, or `null` when none is usable. */
    val usableResult: MeasurementOutcome.Success?
        get() = lastOutcome?.successOrNull()

    /** The failure recorded for the current revision, if the last attempt failed. */
    val failure: MeasurementOutcome.Failure?
        get() = lastOutcome as? MeasurementOutcome.Failure

    /** Export is permitted only for a success at the current revision. */
    val exportEnabled: Boolean
        get() = usableResult != null

    /**
     * Starts a new input revision: clears any success or failure and export eligibility.
     * Completions tagged with an earlier revision are rejected afterwards by [complete].
     */
    fun invalidate(): MeasurementSession = MeasurementSession(revision = revision + 1)

    /**
     * Records [outcome] computed for [forRevision]. A completion for any other revision
     * (late or unknown) is ignored and the session is returned unchanged. A failure at the
     * current revision replaces an earlier success, so it can never be shown or exported.
     */
    fun complete(forRevision: Int, outcome: MeasurementOutcome): MeasurementSession =
        if (forRevision == revision) copy(lastOutcome = outcome) else this
}
