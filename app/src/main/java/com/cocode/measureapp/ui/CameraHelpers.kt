package com.cocode.measureapp.ui

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.view.Surface
import android.view.WindowManager
import android.widget.Toast
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.core.content.ContextCompat
import com.cocode.measureapp.capture.GravityProvider
import com.cocode.measureapp.capture.IntrinsicsExtractor
import com.cocode.measureapp.geometry.CameraIntrinsics
import com.cocode.measureapp.model.CapturedScene

/** Current display rotation in degrees (0/90/180/270) — keeps the tilt level orientation-aware. */
@Suppress("DEPRECATION")
internal fun displayRotationDegrees(context: Context): Int =
    when ((context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation) {
        Surface.ROTATION_90 -> 90
        Surface.ROTATION_180 -> 180
        Surface.ROTATION_270 -> 270
        else -> 0
    }

/** Reads camera intrinsics from the bound camera, falling back to the first back-facing camera. */
internal fun readIntrinsics(context: Context, boundId: String?, w: Int, h: Int): CameraIntrinsics {
    val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    if (boundId != null) return IntrinsicsExtractor.extract(cm.getCameraCharacteristics(boundId), w, h)
    val id = cm.cameraIdList.firstOrNull {
        cm.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) ==
            CameraCharacteristics.LENS_FACING_BACK
    } ?: cm.cameraIdList.firstOrNull()
    return if (id != null) IntrinsicsExtractor.extract(cm.getCameraCharacteristics(id), w, h)
    else { val f = maxOf(w, h).toDouble(); CameraIntrinsics(f, f, w / 2.0, h / 2.0) }
}

/** Fires CameraX capture; delivers result (or a toast on error) on the main thread. */
internal fun takePicture(
    context: Context,
    imageCapture: ImageCapture,
    gravity: GravityProvider,
    executor: java.util.concurrent.Executor,
    cameraId: String?,
    onCaptured: (CapturedImage) -> Unit,
) {
    imageCapture.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
        override fun onCaptureSuccess(image: ImageProxy) {
            val bitmap = image.toBitmap(); image.close()
            val intrinsics = readIntrinsics(context, cameraId, bitmap.width, bitmap.height)
            val scene = CapturedScene(bitmap.width, bitmap.height, intrinsics, gravity.current())
            ContextCompat.getMainExecutor(context).execute { onCaptured(CapturedImage(bitmap, scene)) }
        }
        override fun onError(exception: ImageCaptureException) {
            ContextCompat.getMainExecutor(context).execute {
                Toast.makeText(context, "Capture failed", Toast.LENGTH_SHORT).show()
            }
        }
    })
}
