package com.cocode.measureapp.model

/**
 * Every sentence the pure code can ask the screens to show. The words live in
 * `res/values/strings.xml` (`msg_<name in lower case>`), and `UiTextResources` in the UI layer
 * maps each key to its string. A key with arguments says so: numbers fill `%1$.0f` style
 * placeholders and nested messages fill `%1$s`.
 */
enum class TextKey {
    // How sure the result is.
    CONFIDENCE_HIGH,
    CONFIDENCE_MEDIUM,
    CONFIDENCE_LOW,
    CONFIDENCE_NOT_MEASURED,

    // Notes under a result.
    CAVEAT_LOW_CONFIDENCE,
    CAVEAT_STICK_SPACING,
    CAVEAT_TILT_SENSOR_METHOD,
    CAVEAT_STEEP_CAMERA,
    CAVEAT_LENS_APPROXIMATE,
    CAVEAT_LENS_UNAVAILABLE,
    CAVEAT_NO_TILT_READING,
    CAVEAT_WALL_ASSUMED,
    CAVEAT_STICK_WIDTH_UNKNOWN,

    // What to do after a failed measurement (the headline of the correction text).
    FIX_OBJECT_CORNERS,
    FIX_STICK_BOX,
    FIX_REFERENCE_SIZE,
    FIX_RETAKE_PHOTO,
    FIX_UNSUPPORTED_VIEW,
    FIX_UNSTABLE,
    MEASURE_FAILED,

    /** Two messages one after the other: `%1$s %2$s`. */
    SENTENCES,

    // Marks that cannot be used, one per rule and per box.
    OBJECT_POINT_COUNT,
    OBJECT_NON_FINITE,
    OBJECT_COINCIDENT,
    OBJECT_COLLINEAR,
    OBJECT_SELF_CROSSING,
    OBJECT_CONCAVE,
    OBJECT_ZERO_AREA,
    STICK_POINT_COUNT,
    STICK_NON_FINITE,
    STICK_COINCIDENT,
    STICK_COLLINEAR,
    STICK_SELF_CROSSING,
    STICK_CONCAVE,
    STICK_ZERO_AREA,

    // The stick size in Settings is not a usable number.
    REFERENCE_LENGTH_INVALID,
    REFERENCE_WIDTH_INVALID,

    // Why a measuring method could not be used.
    CAUSE_CALCULATION_UNSTABLE,
    CAUSE_NO_LENS_DETAILS,
    CAUSE_VIEW_TOO_SHALLOW,
    CAUSE_CORNERS_UNUSABLE,
    CAUSE_SHAPE_TOO_SENSITIVE,
    CAUSE_NOT_A_RECTANGLE,
    CAUSE_MARKS_OFF_SURFACE,
    CAUSE_NO_TILT_READING,
    CAUSE_WALL_DIRECTION_UNKNOWN,

    /** One number: degrees away from upright. */
    CAUSE_NOT_A_WALL,

    /** One number: degrees away from level. */
    CAUSE_NOT_A_FLOOR,

    /** Two numbers: how far the stick's two sizes differ, and the most that is accepted, in percent. */
    CAUSE_STICK_DISAGREES,

    // What to try next, added after a cause.
    ADVICE_CHECK_CORNERS,
    ADVICE_SURFACE_CHOICE,
    ADVICE_SURFACE_AND_CORNERS,

    /** One message: the cause, spoken for the rectangle method. */
    RECTANGLE_METHOD_PROBLEM,

    /** One message: the cause, spoken for the tilt-sensor method. */
    TILT_METHOD_PROBLEM,

    /** Two messages: the rectangle method's cause, then the tilt-sensor method's. */
    NO_METHOD_USABLE,
    NOTE_LENS_APPROXIMATE,
    NOTE_LENS_UNAVAILABLE,

    // The camera could not give a photo.
    CAPTURE_START_FAILED,
    CAPTURE_MISMATCHED,
    CAPTURE_CONVERSION_FAILED,
    CAPTURE_CAMERA_ERROR,
    CAMERA_UNAVAILABLE,
}
