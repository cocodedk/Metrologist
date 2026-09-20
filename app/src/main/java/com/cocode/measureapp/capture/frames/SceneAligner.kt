package com.cocode.measureapp.capture.frames

import com.cocode.measureapp.capture.gravity.GravitySample
import com.cocode.measureapp.capture.recovery.CaptureMetadata
import com.cocode.measureapp.capture.recovery.ExposureTimestamp
import com.cocode.measureapp.geometry.Vec3
import com.cocode.measureapp.geometry.frames.AlignedGravity
import com.cocode.measureapp.geometry.frames.CameraMounting
import com.cocode.measureapp.geometry.frames.FrameAlignment
import com.cocode.measureapp.geometry.frames.FrameResult
import com.cocode.measureapp.geometry.frames.FrameUnavailableReason
import com.cocode.measureapp.geometry.frames.GravityAlignmentReason
import com.cocode.measureapp.geometry.frames.ProvenancedIntrinsics
import com.cocode.measureapp.geometry.frames.bitmapHoldsFullBuffer
import com.cocode.measureapp.model.CapturedScene

/** An aligned scene plus what the bitmap still needs, or why the capture cannot be described. */
sealed interface SceneAlignment {
    /** [cropPending]: the delivered bitmap is the full buffer and must still be cropped. */
    data class Aligned(val scene: CapturedScene, val cropPending: Boolean) : SceneAlignment
    data class Unavailable(val reason: FrameUnavailableReason) : SceneAlignment
}

/**
 * Builds the canonical marking-frame scene: pixels, intrinsics and exposure-time gravity all go
 * through ONE [FrameAlignment]. Gravity additionally goes through the camera mounting (device
 * -> buffer) exactly once. Pure; the Android adapter only copies values in.
 */
object SceneAligner {
    /** Legacy vector for [CapturedScene.gravity] when no aligned sample exists. Not a reading. */
    val LEGACY_PLACEHOLDER = Vec3(0.0, 1.0, 0.0)

    fun fromMetadata(
        metadata: CaptureMetadata,
        bitmapWidth: Int,
        bitmapHeight: Int,
        lens: LensCharacteristics?,
        targetRotationDegrees: Int?,
    ): SceneAlignment {
        val c = metadata.crop
        val frame = FrameAlignment.forBitmap(
            metadata.frameWidth, metadata.frameHeight, c.left, c.top, c.right, c.bottom, metadata.rotationDegrees,
        )
        val alignment = when (frame) {
            is FrameResult.Unavailable -> return SceneAlignment.Unavailable(frame.reason)
            is FrameResult.Aligned -> frame.alignment
        }
        val cropPending = alignment.bitmapHoldsFullBuffer(bitmapWidth, bitmapHeight)
            ?: return SceneAlignment.Unavailable(FrameUnavailableReason.BITMAP_SIZE_UNEXPLAINED)
        val intrinsics = BufferIntrinsics.estimate(lens, metadata.frameWidth, metadata.frameHeight)
        val mounting = CameraMounting.of(lens?.facing, lens?.sensorOrientation)
        return when (val r = align(metadata.requestId, alignment, intrinsics, mounting, targetRotationDegrees,
            metadata.exposure, metadata.recentGravity)) {
            is SceneAlignment.Aligned -> r.copy(cropPending = cropPending)
            is SceneAlignment.Unavailable -> r
        }
    }

    fun align(
        shotId: Int,
        alignment: FrameAlignment,
        buffer: ProvenancedIntrinsics,
        mounting: CameraMounting?,
        targetRotationDegrees: Int?,
        exposure: ExposureTimestamp,
        samples: List<GravitySample.Available>,
    ): SceneAlignment {
        when (val v = FrameAlignment.validated(alignment)) {
            is FrameResult.Unavailable -> return SceneAlignment.Unavailable(v.reason)
            is FrameResult.Aligned -> Unit
        }
        val k = alignment.intrinsics(buffer.intrinsics)
            ?: return SceneAlignment.Unavailable(FrameUnavailableReason.INVALID_INTRINSICS)
        val gravity = alignGravity(alignment, mounting, targetRotationDegrees, exposure, samples)
        val scene = CapturedScene(
            imageWidth = alignment.markingWidth,
            imageHeight = alignment.markingHeight,
            intrinsics = k,
            gravity = (gravity as? AlignedGravity.Available)?.down ?: LEGACY_PLACEHOLDER,
            shotId = shotId,
            calibration = buffer.provenance,
            alignedGravity = gravity,
            alignment = alignment,
        )
        return SceneAlignment.Aligned(scene, cropPending = false)
    }

    /** Matched device-axes sample -> buffer camera axes (mounting) -> marking axes (alignment). */
    fun alignGravity(
        alignment: FrameAlignment,
        mounting: CameraMounting?,
        targetRotationDegrees: Int?,
        exposure: ExposureTimestamp,
        samples: List<GravitySample.Available>,
    ): AlignedGravity {
        if (mounting == null || !mounting.explainsBufferRotation(alignment.rotation, targetRotationDegrees)) {
            return AlignedGravity.Unavailable(GravityAlignmentReason.MOUNTING_UNAVAILABLE)
        }
        return when (val m = ShotGravityMatcher.match(exposure, samples)) {
            is ShotGravity.Unmatched -> AlignedGravity.Unavailable(m.reason)
            is ShotGravity.Matched -> AlignedGravity.Available(
                alignment.cameraVector(mounting.deviceToBuffer(m.sample.down)).normalized(),
                m.ageNanos,
            )
        }
    }
}
