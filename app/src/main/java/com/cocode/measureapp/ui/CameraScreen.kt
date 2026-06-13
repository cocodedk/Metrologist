package com.cocode.measureapp.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cocode.measureapp.capture.GravityProvider
import com.cocode.measureapp.capture.IntrinsicsExtractor
import com.cocode.measureapp.geometry.CameraIntrinsics
import com.cocode.measureapp.model.CapturedScene
import java.util.concurrent.Executors

/** In-app CameraX capture. Records the photo plus the camera intrinsics and gravity. */
@Composable
fun CameraScreen(onCaptured: (CapturedImage) -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasPermission = granted }
    LaunchedEffect(Unit) { if (!hasPermission) permLauncher.launch(Manifest.permission.CAMERA) }

    val gravity = remember { GravityProvider(context) }
    DisposableEffect(Unit) {
        gravity.start()
        onDispose { gravity.stop() }
    }
    val imageCapture = remember { ImageCapture.Builder().build() }

    Box(Modifier.fillMaxSize()) {
        if (hasPermission) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val providerFuture = ProcessCameraProvider.getInstance(ctx)
                    providerFuture.addListener({
                        val provider = providerFuture.get()
                        val preview = Preview.Builder().build()
                        preview.setSurfaceProvider(previewView.surfaceProvider)
                        provider.unbindAll()
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageCapture,
                        )
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text("Camera permission is needed to capture.", Modifier.align(Alignment.Center))
        }

        Row(Modifier.align(Alignment.BottomCenter).padding(24.dp)) {
            Button(onClick = onSettings) { Text("Settings") }
            Spacer(Modifier.width(16.dp))
            Button(
                enabled = hasPermission,
                onClick = { capture(context, imageCapture, gravity, onCaptured) },
            ) { Text("Capture") }
        }
    }
}

private fun capture(
    context: Context,
    imageCapture: ImageCapture,
    gravity: GravityProvider,
    onCaptured: (CapturedImage) -> Unit,
) {
    val executor = Executors.newSingleThreadExecutor()
    imageCapture.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
        override fun onCaptureSuccess(image: ImageProxy) {
            val bitmap = image.toBitmap()
            image.close()
            val intrinsics = readIntrinsics(context, bitmap.width, bitmap.height)
            val scene = CapturedScene(bitmap.width, bitmap.height, intrinsics, gravity.current())
            ContextCompat.getMainExecutor(context).execute {
                onCaptured(CapturedImage(bitmap, scene))
            }
        }

        override fun onError(exception: ImageCaptureException) {}
    })
}

private fun readIntrinsics(context: Context, w: Int, h: Int): CameraIntrinsics {
    val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    val id = cm.cameraIdList.firstOrNull {
        cm.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) ==
            CameraCharacteristics.LENS_FACING_BACK
    } ?: cm.cameraIdList.firstOrNull()
    return if (id != null) {
        IntrinsicsExtractor.extract(cm.getCameraCharacteristics(id), w, h)
    } else {
        val f = maxOf(w, h).toDouble()
        CameraIntrinsics(f, f, w / 2.0, h / 2.0)
    }
}
