package com.cocode.measureapp.model

import com.cocode.measureapp.geometry.CameraIntrinsics
import com.cocode.measureapp.geometry.Vec3
import com.cocode.measureapp.geometry.frames.AlignedGravity
import com.cocode.measureapp.geometry.frames.CalibrationProvenance
import com.cocode.measureapp.geometry.frames.FrameAlignment
import com.cocode.measureapp.geometry.frames.GravityAlignmentReason

/**
 * Camera data captured at the moment of the shot. Coordinates are pixels of the canonical,
 * upright marking bitmap; [intrinsics] and [alignedGravity] describe that same frame.
 * The bitmap itself is held by the UI layer; this is the pure metadata the engine needs.
 *
 * Scenes built by [com.cocode.measureapp.capture.frames.SceneAligner] carry [shotId] (the
 * capture request that produced the bitmap), [alignment] and real [calibration]. The 4-arg
 * legacy form keeps [CalibrationProvenance.UNAVAILABLE] and unaligned gravity: its values
 * were never verified against the marking frame and must not be treated as calibrated.
 */
data class CapturedScene(
    val imageWidth: Int,
    val imageHeight: Int,
    val intrinsics: CameraIntrinsics,
    /**
     * Legacy non-null vector for consumers not yet migrated to [alignedGravity]. When
     * [alignedGravity] is unavailable this is NOT a reading; consumers must check it.
     */
    val gravity: Vec3,
    val shotId: Int? = null,
    val calibration: CalibrationProvenance = CalibrationProvenance.UNAVAILABLE,
    val alignedGravity: AlignedGravity = AlignedGravity.Unavailable(GravityAlignmentReason.FRAME_NOT_ALIGNED),
    val alignment: FrameAlignment? = null,
)
