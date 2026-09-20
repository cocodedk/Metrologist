package com.cocode.measureapp.ui.capture

import android.content.Context
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.core.content.ContextCompat
import com.cocode.measureapp.capture.GravityProvider
import com.cocode.measureapp.capture.recovery.CalibrationQuality
import com.cocode.measureapp.capture.recovery.CaptureMetadata
import com.cocode.measureapp.capture.recovery.CaptureOutcome
import com.cocode.measureapp.capture.recovery.CropRect
import com.cocode.measureapp.capture.recovery.consumeFrame
import com.cocode.measureapp.capture.recovery.exposureTimestampOf
import com.cocode.measureapp.geometry.frames.CalibrationStatus
import com.cocode.measureapp.ui.CapturedImage
import com.cocode.measureapp.ui.alignCapturedShot
import com.cocode.measureapp.ui.readLens
import com.cocode.measureapp.ui.surfaceRotationDegrees
import java.util.concurrent.Executor

/** User-facing message for a camera-reported capture error. */
internal const val CAMERA_ERROR = "Capture failed. Check the camera and try again."

/**
 * Fires one CameraX capture for [requestId]. Exactly one outcome is posted to [onOutcome] on
 * the main thread; the acquired proxy is closed on every path. Synchronous start failures
 * propagate to the caller ([com.cocode.measureapp.capture.recovery.CaptureController.launch]).
 */
internal fun startCapture(
    context: Context,
    imageCapture: ImageCapture,
    executor: Executor,
    requestId: Int,
    cameraId: String?,
    gravity: GravityProvider,
    onOutcome: (Int, CaptureOutcome<CapturedImage>) -> Unit,
) {
    val main = ContextCompat.getMainExecutor(context)
    // The target rotation this shot is taken with; only cross-checks the camera mounting.
    val targetDegrees = surfaceRotationDegrees(imageCapture.targetRotation)
    imageCapture.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
        override fun onCaptureSuccess(image: ImageProxy) {
            val outcome = consumeFrame(image) {
                buildCapturedImage(context, image, requestId, cameraId, gravity, targetDegrees)
            }
            main.execute { onOutcome(requestId, outcome) }
        }

        override fun onError(exception: ImageCaptureException) {
            main.execute { onOutcome(requestId, CaptureOutcome.Failure(CAMERA_ERROR, exception)) }
        }
    })
}

/**
 * Copies all shot metadata from [image] before conversion (the caller closes the proxy), then
 * hands off the canonical upright bitmap together with its aligned scene. Gravity comes only
 * from exposure-matched samples; no live reading is substituted.
 */
private fun buildCapturedImage(
    context: Context,
    image: ImageProxy,
    requestId: Int,
    cameraId: String?,
    gravity: GravityProvider,
    targetDegrees: Int,
): CapturedImage {
    val lens = readLens(context, cameraId)
    val crop = image.cropRect
    val metadata = CaptureMetadata(
        requestId = requestId,
        frameWidth = image.width,
        frameHeight = image.height,
        rotationDegrees = image.imageInfo.rotationDegrees,
        crop = CropRect(crop.left, crop.top, crop.right, crop.bottom),
        exposure = exposureTimestampOf(image.imageInfo.timestamp, lens?.timestampSource),
        gravity = gravity.latestSample(),
        recentGravity = gravity.recentSamples(),
        // Provisional; replaced below by the aligned scene's own provenance.
        calibration = CalibrationQuality.APPROXIMATE,
    )
    val raw = image.toBitmap()
    try {
        metadata.requireComplete(raw.width, raw.height)
        val (upright, scene) = alignCapturedShot(raw, metadata, lens, targetDegrees)
        val quality = if (scene.calibration.status == CalibrationStatus.CALIBRATED) {
            CalibrationQuality.DEVICE_CALIBRATED
        } else {
            CalibrationQuality.APPROXIMATE
        }
        return CapturedImage(upright, scene, metadata.copy(calibration = quality))
    } catch (e: Throwable) {
        if (!raw.isRecycled) raw.recycle()
        throw e
    }
}
