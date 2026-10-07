package com.cocode.measureapp.geometry.eligibility

import com.cocode.measureapp.geometry.rectangle.RectangleRejection
import com.cocode.measureapp.model.TextKey
import com.cocode.measureapp.model.UiText

/**
 * The messages that explain why a method could not be used. A customer sees the cause in plain
 * words, not the internal rule name or the numbers behind it, so several rules share one message.
 */
internal object Causes {
    val CALCULATION_UNSTABLE = UiText(TextKey.CAUSE_CALCULATION_UNSTABLE)
    val NO_LENS_DETAILS = UiText(TextKey.CAUSE_NO_LENS_DETAILS)
    val VIEW_TOO_SHALLOW = UiText(TextKey.CAUSE_VIEW_TOO_SHALLOW)

    /** Why the marked corners gave no rectangle plane at all. */
    fun of(rejection: RectangleRejection): UiText = when (rejection) {
        RectangleRejection.INVALID_CORNER_COUNT,
        RectangleRejection.NON_FINITE_CORNERS,
        RectangleRejection.COINCIDENT_CORNERS,
        RectangleRejection.ZERO_VANISHING_VECTOR,
        RectangleRejection.COINCIDENT_DIRECTIONS -> UiText(TextKey.CAUSE_CORNERS_UNUSABLE)
        RectangleRejection.INVALID_INTRINSICS -> NO_LENS_DETAILS
        RectangleRejection.PROJECTION_UNUSABLE,
        RectangleRejection.GRAZING_VIEW -> VIEW_TOO_SHALLOW
        RectangleRejection.ILL_CONDITIONED -> UiText(TextKey.CAUSE_SHAPE_TOO_SENSITIVE)
        RectangleRejection.NUMERICAL_FAILURE -> CALCULATION_UNSTABLE
    }

    /** A cause followed by what to try, as two sentences. */
    fun then(cause: UiText, advice: TextKey): UiText =
        UiText.withMessages(TextKey.SENTENCES, cause, UiText(advice))
}
