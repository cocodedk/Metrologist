package com.cocode.measureapp.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cocode.measureapp.capture.GravityProvider
import com.cocode.measureapp.capture.gravity.levelReadingOf
import com.cocode.measureapp.capture.recovery.CaptureController
import com.cocode.measureapp.capture.recovery.CaptureState
import com.cocode.measureapp.ui.capture.CAMERA_UNAVAILABLE
import com.cocode.measureapp.ui.capture.CameraControls
import com.cocode.measureapp.ui.capture.CameraPreview
import com.cocode.measureapp.ui.capture.PermissionDeniedPanel
import com.cocode.measureapp.ui.capture.startCapture
import java.util.concurrent.Executors
import kotlinx.coroutines.delay

/**
 * In-app CameraX capture. Records the photo plus the camera intrinsics and gravity.
 * Each screen instance owns a fresh [CaptureController]: every request ends in success,
 * failure or cancellation, and late results after leaving the screen are discarded.
 */
@Composable
fun CameraScreen(
    onCaptured: (CapturedImage) -> Unit,
    onSettings: () -> Unit,
    onHelp: () -> Unit,
) {
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

    val deliver by rememberUpdatedState(onCaptured)
    val gravity = remember { GravityProvider(context) }
    val captureExecutor = remember { Executors.newSingleThreadExecutor() }
    var captureState by remember { mutableStateOf(CaptureState()) }
    val controller = remember {
        CaptureController<CapturedImage>(
            discard = { it.bitmap.recycle() },
            requestIdOf = { it.metadata.requestId },
            onStateChanged = { captureState = it },
        )
    }
    DisposableEffect(Unit) {
        gravity.start()
        onDispose {
            gravity.stop()
            controller.dispose()
            // A pending request still owes a callback on this executor; shut down after it.
            if (controller.released) captureExecutor.shutdown()
        }
    }
    val imageCapture = remember { ImageCapture.Builder().build() }
    var boundCameraId by remember { mutableStateOf<String?>(null) }
    var bindError by remember { mutableStateOf<String?>(null) }
    var tilt by remember { mutableStateOf(levelReadingOf(gravity.latestSample(), 0)) }
    LaunchedEffect(Unit) {
        while (true) {
            // Device-axis down -> current display axes; the image-frame transform is not applied here.
            tilt = levelReadingOf(gravity.latestSample(), displayRotationDegrees(context))
            delay(50)
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (hasPermission) {
            CameraPreview(
                lifecycleOwner = lifecycleOwner,
                imageCapture = imageCapture,
                onBound = { id -> boundCameraId = id; bindError = null },
                onBindFailed = { boundCameraId = null; bindError = CAMERA_UNAVAILABLE },
                modifier = Modifier.fillMaxSize(),
            )
            LevelOverlay(tilt, Modifier.align(Alignment.Center))
        } else {
            PermissionDeniedPanel(
                onGrant = { permLauncher.launch(Manifest.permission.CAMERA) },
                onOpenSettings = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        },
                    )
                },
                modifier = Modifier.align(Alignment.Center),
            )
        }

        CameraControls(
            captureEnabled = captureState.canCapture(hasPermission, cameraReady = boundCameraId != null),
            capturing = captureState.busy,
            message = captureState.error ?: bindError,
            onSettings = onSettings,
            onHelp = onHelp,
            onCapture = {
                val cameraId = boundCameraId
                controller.launch { id ->
                    startCapture(context, imageCapture, captureExecutor, id, cameraId, gravity) { rid, outcome ->
                        controller.complete(rid, outcome)?.let { deliver(it) }
                        if (controller.released) captureExecutor.shutdown()
                    }
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
