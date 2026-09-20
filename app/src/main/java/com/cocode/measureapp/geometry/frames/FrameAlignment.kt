package com.cocode.measureapp.geometry.frames

import com.cocode.measureapp.geometry.CameraIntrinsics
import com.cocode.measureapp.geometry.Vec2
import com.cocode.measureapp.geometry.Vec3

/** Why a buffer-to-marking transform could not be established. */
enum class FrameUnavailableReason {
    INVALID_BUFFER, INVALID_CROP, INVALID_SCALE, INVALID_ROTATION, BITMAP_SIZE_UNEXPLAINED,
    INVALID_INTRINSICS,
}

/**
 * The single transform from camera-buffer pixels to the canonical upright marking bitmap:
 * subtract the crop origin, scale the crop to [scaledWidth] x [scaledHeight], then rotate
 * clockwise by [rotation]. Pixels, intrinsics and camera vectors each go through it exactly
 * once; the display frame (level overlay) is a separate, unrelated transform.
 */
data class FrameAlignment(
    val bufferWidth: Int,
    val bufferHeight: Int,
    val cropLeft: Int,
    val cropTop: Int,
    val cropWidth: Int,
    val cropHeight: Int,
    val scaledWidth: Int,
    val scaledHeight: Int,
    val rotation: QuarterTurn,
) {
    val scaleX: Double get() = scaledWidth.toDouble() / cropWidth
    val scaleY: Double get() = scaledHeight.toDouble() / cropHeight
    val markingWidth: Int get() = if (rotation.swapsAxes) scaledHeight else scaledWidth
    val markingHeight: Int get() = if (rotation.swapsAxes) scaledWidth else scaledHeight

    /** Buffer pixel -> marking pixel. */
    fun toMarking(p: Vec2): Vec2 = rotation.pixel(
        Vec2((p.x - cropLeft) * scaleX, (p.y - cropTop) * scaleY),
        scaledWidth.toDouble(), scaledHeight.toDouble(),
    )

    /** Marking pixel -> buffer pixel (exact inverse of [toMarking]). */
    fun toBuffer(p: Vec2): Vec2 {
        val s = rotation.inverse().pixel(p, markingWidth.toDouble(), markingHeight.toDouble())
        return Vec2(s.x / scaleX + cropLeft, s.y / scaleY + cropTop)
    }

    /** Buffer intrinsics -> intrinsics describing the marking pixels, or null if not finite/positive. */
    fun intrinsics(buffer: CameraIntrinsics): CameraIntrinsics? {
        if (!buffer.isUsable()) return null
        val scaled = CameraIntrinsics(
            fx = buffer.fx * scaleX,
            fy = buffer.fy * scaleY,
            cx = (buffer.cx - cropLeft) * scaleX,
            cy = (buffer.cy - cropTop) * scaleY,
        )
        return rotation.intrinsics(scaled, scaledWidth.toDouble(), scaledHeight.toDouble())
            .takeIf { it.isUsable() }
    }

    /** Buffer camera-frame vector -> marking camera-frame vector (crop and scale leave rays unchanged). */
    fun cameraVector(v: Vec3): Vec3 = rotation.vector(v)

    companion object {
        /**
         * Validates the capture metadata and explains the delivered bitmap: its size must equal
         * either the full buffer (crop not yet applied) or the crop rectangle (already applied).
         * The returned transform maps buffer pixels onto the upright bitmap at scale 1.
         */
        fun forBitmap(
            bufferWidth: Int, bufferHeight: Int,
            cropLeft: Int, cropTop: Int, cropRight: Int, cropBottom: Int,
            rotationDegrees: Int,
        ): FrameResult {
            if (bufferWidth <= 0 || bufferHeight <= 0) return FrameResult.Unavailable(FrameUnavailableReason.INVALID_BUFFER)
            val cw = cropRight - cropLeft
            val ch = cropBottom - cropTop
            if (cropLeft < 0 || cropTop < 0 || cw <= 0 || ch <= 0 || cropRight > bufferWidth || cropBottom > bufferHeight) {
                return FrameResult.Unavailable(FrameUnavailableReason.INVALID_CROP)
            }
            val turn = rotationDegrees.takeIf { it in 0..270 }?.let { QuarterTurn.of(it) }
                ?: return FrameResult.Unavailable(FrameUnavailableReason.INVALID_ROTATION)
            return FrameResult.Aligned(
                FrameAlignment(bufferWidth, bufferHeight, cropLeft, cropTop, cw, ch, cw, ch, turn),
            )
        }

        /** Full validation of an explicit transform, including a resize of the crop. */
        fun validated(a: FrameAlignment): FrameResult = when {
            a.bufferWidth <= 0 || a.bufferHeight <= 0 -> FrameResult.Unavailable(FrameUnavailableReason.INVALID_BUFFER)
            a.cropLeft < 0 || a.cropTop < 0 || a.cropWidth <= 0 || a.cropHeight <= 0 ||
                a.cropLeft + a.cropWidth > a.bufferWidth || a.cropTop + a.cropHeight > a.bufferHeight ->
                FrameResult.Unavailable(FrameUnavailableReason.INVALID_CROP)
            a.scaledWidth <= 0 || a.scaledHeight <= 0 -> FrameResult.Unavailable(FrameUnavailableReason.INVALID_SCALE)
            else -> FrameResult.Aligned(a)
        }
    }
}

/** A validated transform, or why none exists. Never a guessed identity. */
sealed interface FrameResult {
    data class Aligned(val alignment: FrameAlignment) : FrameResult
    data class Unavailable(val reason: FrameUnavailableReason) : FrameResult
}

/** Whether a bitmap of [width] x [height] is the full buffer (crop pending) or already the crop. */
fun FrameAlignment.bitmapHoldsFullBuffer(width: Int, height: Int): Boolean? = when {
    width == bufferWidth && height == bufferHeight -> true
    width == cropWidth && height == cropHeight -> false
    else -> null
}
