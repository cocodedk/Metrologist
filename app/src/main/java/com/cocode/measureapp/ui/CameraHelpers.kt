package com.cocode.measureapp.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.hardware.camera2.CameraManager
import android.view.Surface
import android.view.WindowManager
import com.cocode.measureapp.capture.IntrinsicsExtractor
import com.cocode.measureapp.capture.frames.LensCharacteristics
import com.cocode.measureapp.capture.frames.SceneAligner
import com.cocode.measureapp.capture.frames.SceneAlignment
import com.cocode.measureapp.capture.recovery.CaptureMetadata
import com.cocode.measureapp.capture.recovery.IncompleteCaptureException
import com.cocode.measureapp.geometry.frames.FrameAlignment
import com.cocode.measureapp.model.CapturedScene

/**
 * Current display rotation in degrees (0/90/180/270). This is the DISPLAY frame, used by the
 * level overlay only; it is never applied to an already aligned measurement vector.
 */
@Suppress("DEPRECATION")
internal fun displayRotationDegrees(context: Context): Int =
    surfaceRotationDegrees((context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation)

/** A `Surface.ROTATION_*` constant (display or capture target rotation) in degrees. */
internal fun surfaceRotationDegrees(rotation: Int): Int = when (rotation) {
    Surface.ROTATION_90 -> 90
    Surface.ROTATION_180 -> 180
    Surface.ROTATION_270 -> 270
    else -> 0
}

/** Plain copies of the bound camera's characteristics, or null when they cannot be read. */
internal fun readLens(context: Context, cameraId: String?): LensCharacteristics? {
    if (cameraId == null) return null
    val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    return runCatching { IntrinsicsExtractor.read(cm.getCameraCharacteristics(cameraId)) }.getOrNull()
}

/**
 * Turns the raw bitmap of one shot into the canonical upright marking bitmap and its aligned
 * scene (intrinsics, calibration provenance, exposure-time gravity, shot identity). Call while
 * [metadata] describes the same shot, before any other transform. [lens] describes the bound
 * camera (null when unreadable: approximate intrinsics, gravity unavailable).
 * [targetRotationDegrees] is the capture's target rotation when known; it only cross-checks the
 * camera mounting. Throws [IncompleteCaptureException] when the frame cannot be described;
 * [raw] is then left to the caller to recycle. On success [raw] is recycled if a new bitmap
 * was produced.
 */
internal fun alignCapturedShot(
    raw: Bitmap,
    metadata: CaptureMetadata,
    lens: LensCharacteristics?,
    targetRotationDegrees: Int? = null,
): Pair<Bitmap, CapturedScene> =
    when (val r = SceneAligner.fromMetadata(metadata, raw.width, raw.height, lens, targetRotationDegrees)) {
        is SceneAlignment.Unavailable -> throw IncompleteCaptureException("frame transform ${r.reason}")
        is SceneAlignment.Aligned -> uprightBitmap(raw, r.scene.alignment!!, r.cropPending) to r.scene
    }

/** Applies crop (if still pending), scale and clockwise rotation once, exactly as [FrameAlignment]. */
private fun uprightBitmap(raw: Bitmap, a: FrameAlignment, cropPending: Boolean): Bitmap {
    val m = Matrix().apply {
        postScale(a.scaleX.toFloat(), a.scaleY.toFloat())
        postRotate(a.rotation.degrees.toFloat())
    }
    val x = if (cropPending) a.cropLeft else 0
    val y = if (cropPending) a.cropTop else 0
    val out = Bitmap.createBitmap(raw, x, y, a.cropWidth, a.cropHeight, m, true)
    if (out.width != a.markingWidth || out.height != a.markingHeight) {
        val what = "upright bitmap ${out.width}x${out.height} != ${a.markingWidth}x${a.markingHeight}"
        if (out !== raw) out.recycle()
        throw IncompleteCaptureException(what)
    }
    if (out !== raw) raw.recycle()
    return out
}
// Capture requests and their recovery live in ui/capture/CaptureRequest.kt.
