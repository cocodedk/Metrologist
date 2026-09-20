package com.cocode.measureapp.geometry.frames

import com.cocode.measureapp.geometry.Vec3

/** Why a shot carries no usable camera-frame gravity. None of these may become a default vector. */
enum class GravityAlignmentReason {
    /** No exposure timestamp, or its clock source was not declared. */
    EXPOSURE_TIME_UNAVAILABLE,

    /** Camera timestamp source is UNKNOWN: not comparable with sensor `elapsedRealtimeNanos`. */
    CAMERA_CLOCK_NOT_REALTIME,

    /** Sample clock differs from the camera's REALTIME clock. */
    CLOCK_MISMATCH,

    /** No valid sample at or before exposure (only none, or only later ones). */
    NO_SAMPLE_AT_OR_BEFORE_EXPOSURE,

    /** The newest sample at or before exposure is older than the age limit. */
    STALE_SAMPLE,

    /** Camera facing/sensor orientation unknown, or it contradicts the reported buffer rotation. */
    MOUNTING_UNAVAILABLE,

    /** The scene was built without the capture-frame transform. */
    FRAME_NOT_ALIGNED,
}

/**
 * Physical-down gravity in the SAME camera frame used to back-project the marking pixels, or
 * why none is available. [down] is a unit vector; [ageNanos] is exposure minus sample time.
 */
sealed interface AlignedGravity {
    data class Available(val down: Vec3, val ageNanos: Long) : AlignedGravity
    data class Unavailable(val reason: GravityAlignmentReason) : AlignedGravity
}
