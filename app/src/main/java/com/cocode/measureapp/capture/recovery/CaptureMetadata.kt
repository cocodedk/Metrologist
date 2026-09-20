package com.cocode.measureapp.capture.recovery

import com.cocode.measureapp.capture.gravity.GravitySample

/** The camera's declared clock for exposure timestamps (`SENSOR_INFO_TIMESTAMP_SOURCE`). */
enum class TimestampSource { REALTIME, UNKNOWN }

/** Exposure time of one shot, or why it is unavailable. Never substituted by callback time. */
sealed interface ExposureTimestamp {
    data class Available(val nanos: Long, val source: TimestampSource) : ExposureTimestamp
    data class Unavailable(val reason: String) : ExposureTimestamp
}

/** Whether intrinsics come from the device's lens calibration or an approximation. */
enum class CalibrationQuality { DEVICE_CALIBRATED, APPROXIMATE }

/** Crop rectangle in frame pixels, copied out of the proxy (no Android `Rect`). */
data class CropRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top

    fun isNonEmptyWithin(frameWidth: Int, frameHeight: Int): Boolean =
        left >= 0 && top >= 0 && width > 0 && height > 0 && right <= frameWidth && bottom <= frameHeight
}

/**
 * Metadata identifying one shot, copied before its proxy is closed. [requestId] ties it to
 * the image delivered with it. [gravity] and [recentGravity] are the raw samples at copy
 * time; matching them to [exposure] and transforming to the image frame belongs to node 02.
 */
data class CaptureMetadata(
    val requestId: Int,
    val frameWidth: Int,
    val frameHeight: Int,
    val rotationDegrees: Int,
    val crop: CropRect,
    val exposure: ExposureTimestamp,
    val gravity: GravitySample,
    val recentGravity: List<GravitySample.Available>,
    val calibration: CalibrationQuality,
) {
    /**
     * Throws [IncompleteCaptureException] when required fields cannot describe an image of
     * [imageWidth] x [imageHeight]. Optional data (timestamp, gravity) is flagged, not required.
     */
    fun requireComplete(imageWidth: Int, imageHeight: Int) {
        if (imageWidth <= 0 || imageHeight <= 0) incomplete("empty image ${imageWidth}x$imageHeight")
        if (frameWidth <= 0 || frameHeight <= 0) incomplete("empty frame ${frameWidth}x$frameHeight")
        if (rotationDegrees !in VALID_ROTATIONS) incomplete("rotation $rotationDegrees")
        if (!crop.isNonEmptyWithin(frameWidth, frameHeight)) incomplete("crop $crop")
    }

    private fun incomplete(what: String): Nothing = throw IncompleteCaptureException(what)

    companion object {
        val VALID_ROTATIONS = setOf(0, 90, 180, 270)
    }
}

/** A capture whose image/metadata handoff is incomplete; a failure, never a partial success. */
class IncompleteCaptureException(what: String) : IllegalStateException("Incomplete capture metadata: $what")

/** Camera2 `SENSOR_INFO_TIMESTAMP_SOURCE_*` values, mirrored so this stays Android-free. */
const val TIMESTAMP_SOURCE_UNKNOWN = 0
const val TIMESTAMP_SOURCE_REALTIME = 1

/**
 * Maps the proxy's exposure time and the camera's declared source. A non-positive time or
 * a missing/unrecognised declaration is explicit unavailability, not a guess.
 */
fun exposureTimestampOf(frameTimestampNanos: Long, declaredSource: Int?): ExposureTimestamp = when {
    frameTimestampNanos <= 0L -> ExposureTimestamp.Unavailable("frame has no exposure timestamp")
    declaredSource == TIMESTAMP_SOURCE_REALTIME ->
        ExposureTimestamp.Available(frameTimestampNanos, TimestampSource.REALTIME)
    declaredSource == TIMESTAMP_SOURCE_UNKNOWN ->
        ExposureTimestamp.Available(frameTimestampNanos, TimestampSource.UNKNOWN)
    declaredSource == null -> ExposureTimestamp.Unavailable("camera timestamp source undeclared")
    else -> ExposureTimestamp.Unavailable("unrecognised timestamp source $declaredSource")
}
