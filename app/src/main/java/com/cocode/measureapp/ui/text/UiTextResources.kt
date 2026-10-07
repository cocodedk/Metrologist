package com.cocode.measureapp.ui.text

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.cocode.measureapp.R
import com.cocode.measureapp.model.TextKey
import com.cocode.measureapp.model.UiText

/** The string resource that holds the words for [this] message. */
@StringRes
internal fun TextKey.stringRes(): Int = when (this) {
        TextKey.CONFIDENCE_HIGH -> R.string.msg_confidence_high
        TextKey.CONFIDENCE_MEDIUM -> R.string.msg_confidence_medium
        TextKey.CONFIDENCE_LOW -> R.string.msg_confidence_low
        TextKey.CONFIDENCE_NOT_MEASURED -> R.string.msg_confidence_not_measured
        TextKey.CAVEAT_LOW_CONFIDENCE -> R.string.msg_caveat_low_confidence
        TextKey.CAVEAT_STICK_SPACING -> R.string.msg_caveat_stick_spacing
        TextKey.CAVEAT_TILT_SENSOR_METHOD -> R.string.msg_caveat_tilt_sensor_method
        TextKey.CAVEAT_STEEP_CAMERA -> R.string.msg_caveat_steep_camera
        TextKey.CAVEAT_LENS_APPROXIMATE -> R.string.msg_caveat_lens_approximate
        TextKey.CAVEAT_LENS_UNAVAILABLE -> R.string.msg_caveat_lens_unavailable
        TextKey.CAVEAT_NO_TILT_READING -> R.string.msg_caveat_no_tilt_reading
        TextKey.CAVEAT_WALL_ASSUMED -> R.string.msg_caveat_wall_assumed
        TextKey.CAVEAT_STICK_WIDTH_UNKNOWN -> R.string.msg_caveat_stick_width_unknown
        TextKey.FIX_OBJECT_CORNERS -> R.string.msg_fix_object_corners
        TextKey.FIX_STICK_BOX -> R.string.msg_fix_stick_box
        TextKey.FIX_REFERENCE_SIZE -> R.string.msg_fix_reference_size
        TextKey.FIX_RETAKE_PHOTO -> R.string.msg_fix_retake_photo
        TextKey.FIX_UNSUPPORTED_VIEW -> R.string.msg_fix_unsupported_view
        TextKey.FIX_UNSTABLE -> R.string.msg_fix_unstable
        TextKey.MEASURE_FAILED -> R.string.msg_measure_failed
        TextKey.SENTENCES -> R.string.msg_sentences
        TextKey.OBJECT_POINT_COUNT -> R.string.msg_object_point_count
        TextKey.OBJECT_NON_FINITE -> R.string.msg_object_non_finite
        TextKey.OBJECT_COINCIDENT -> R.string.msg_object_coincident
        TextKey.OBJECT_COLLINEAR -> R.string.msg_object_collinear
        TextKey.OBJECT_SELF_CROSSING -> R.string.msg_object_self_crossing
        TextKey.OBJECT_CONCAVE -> R.string.msg_object_concave
        TextKey.OBJECT_ZERO_AREA -> R.string.msg_object_zero_area
        TextKey.STICK_POINT_COUNT -> R.string.msg_stick_point_count
        TextKey.STICK_NON_FINITE -> R.string.msg_stick_non_finite
        TextKey.STICK_COINCIDENT -> R.string.msg_stick_coincident
        TextKey.STICK_COLLINEAR -> R.string.msg_stick_collinear
        TextKey.STICK_SELF_CROSSING -> R.string.msg_stick_self_crossing
        TextKey.STICK_CONCAVE -> R.string.msg_stick_concave
        TextKey.STICK_ZERO_AREA -> R.string.msg_stick_zero_area
        TextKey.REFERENCE_LENGTH_INVALID -> R.string.msg_reference_length_invalid
        TextKey.REFERENCE_WIDTH_INVALID -> R.string.msg_reference_width_invalid
        TextKey.CAUSE_CALCULATION_UNSTABLE -> R.string.msg_cause_calculation_unstable
        TextKey.CAUSE_NO_LENS_DETAILS -> R.string.msg_cause_no_lens_details
        TextKey.CAUSE_VIEW_TOO_SHALLOW -> R.string.msg_cause_view_too_shallow
        TextKey.CAUSE_CORNERS_UNUSABLE -> R.string.msg_cause_corners_unusable
        TextKey.CAUSE_SHAPE_TOO_SENSITIVE -> R.string.msg_cause_shape_too_sensitive
        TextKey.CAUSE_NOT_A_RECTANGLE -> R.string.msg_cause_not_a_rectangle
        TextKey.CAUSE_MARKS_OFF_SURFACE -> R.string.msg_cause_marks_off_surface
        TextKey.CAUSE_NO_TILT_READING -> R.string.msg_cause_no_tilt_reading
        TextKey.CAUSE_WALL_DIRECTION_UNKNOWN -> R.string.msg_cause_wall_direction_unknown
        TextKey.CAUSE_NOT_A_WALL -> R.string.msg_cause_not_a_wall
        TextKey.CAUSE_NOT_A_FLOOR -> R.string.msg_cause_not_a_floor
        TextKey.CAUSE_STICK_DISAGREES -> R.string.msg_cause_stick_disagrees
        TextKey.ADVICE_CHECK_CORNERS -> R.string.msg_advice_check_corners
        TextKey.ADVICE_SURFACE_CHOICE -> R.string.msg_advice_surface_choice
        TextKey.ADVICE_SURFACE_AND_CORNERS -> R.string.msg_advice_surface_and_corners
        TextKey.RECTANGLE_METHOD_PROBLEM -> R.string.msg_rectangle_method_problem
        TextKey.TILT_METHOD_PROBLEM -> R.string.msg_tilt_method_problem
        TextKey.NO_METHOD_USABLE -> R.string.msg_no_method_usable
        TextKey.NOTE_LENS_APPROXIMATE -> R.string.msg_note_lens_approximate
        TextKey.NOTE_LENS_UNAVAILABLE -> R.string.msg_note_lens_unavailable
        TextKey.CAPTURE_START_FAILED -> R.string.msg_capture_start_failed
        TextKey.CAPTURE_MISMATCHED -> R.string.msg_capture_mismatched
        TextKey.CAPTURE_CONVERSION_FAILED -> R.string.msg_capture_conversion_failed
        TextKey.CAPTURE_CAMERA_ERROR -> R.string.msg_capture_camera_error
        TextKey.CAMERA_UNAVAILABLE -> R.string.msg_camera_unavailable
}

/** The words for this message in the phone's language; nested messages are worded first. */
fun UiText.resolve(context: Context): String =
    context.getString(key.stringRes(), *args.map { it.resolve(context) }.toTypedArray())

private fun UiText.TextArg.resolve(context: Context): Any = when (this) {
    is UiText.TextArg.Number -> value
    is UiText.TextArg.Message -> text.resolve(context)
}

/** [resolve] for a composable; it follows a change of language. */
@Composable
fun UiText.asString(): String {
    LocalConfiguration.current
    return resolve(LocalContext.current)
}
