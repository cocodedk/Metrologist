package com.cocode.measureapp.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
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
import com.cocode.measureapp.ui.theme.StaffRed
import java.util.concurrent.Executors

/** In-app CameraX capture. Records the photo plus the camera intrinsics and gravity. */
@OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)
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

    val gravity = remember { GravityProvider(context) }
    val captureExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        gravity.start()
        onDispose {
            gravity.stop()
            captureExecutor.shutdown()
        }
    }
    val imageCapture = remember { ImageCapture.Builder().build() }
    val boundCameraId = remember { mutableStateOf<String?>(null) }
    var capturing by remember { mutableStateOf(false) }

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
                        val camera = provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageCapture,
                        )
                        boundCameraId.value = Camera2CameraInfo.from(camera.cameraInfo).cameraId
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
                modifier = Modifier.fillMaxSize(),
            )
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

        Row(
            Modifier.align(Alignment.BottomCenter).padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Settings")
            }
            OutlinedButton(onClick = onHelp) {
                Icon(Icons.Default.Info, contentDescription = "Help", Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Help")
            }
            Button(
                enabled = hasPermission && !capturing,
                onClick = {
                    capturing = true
                    takePicture(context, imageCapture, gravity, captureExecutor, boundCameraId.value) { img ->
                        capturing = false
                        onCaptured(img)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = StaffRed),
                modifier = Modifier.height(48.dp),
            ) {
                Text(if (capturing) "Capturing…" else "Capture")
            }
        }
    }
}

@Composable
private fun PermissionDeniedPanel(
    onGrant: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Camera permission is needed to capture.")
        Button(onClick = onGrant) { Text("Grant permission") }
        OutlinedButton(onClick = onOpenSettings) { Text("Open settings") }
    }
}

