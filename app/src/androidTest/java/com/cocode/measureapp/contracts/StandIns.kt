package com.cocode.measureapp.contracts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.cocode.measureapp.capture.recovery.CaptureController
import com.cocode.measureapp.capture.recovery.CaptureOutcome
import com.cocode.measureapp.capture.recovery.CaptureState
import com.cocode.measureapp.core.dimensions.ReferenceCheck
import com.cocode.measureapp.core.dimensions.ReferenceDimensions
import com.cocode.measureapp.ui.CapturedImage
import com.cocode.measureapp.ui.SettingsScreen
import com.cocode.measureapp.ui.capture.CameraControls

/** Stand-in for CameraX: holds every started request until the test finishes it. */
class FakeCamera {
    class Pending(val screen: Int, val id: Int, val callback: (Int, CaptureOutcome<CapturedImage>) -> Unit)

    val pending = mutableListOf<Pending>()

    /** Delivers the terminal callback of [p] on the calling (main) thread, as the app posts it. */
    fun finish(p: Pending, outcome: CaptureOutcome<CapturedImage>) {
        pending.remove(p)
        p.callback(p.id, outcome)
    }
}

/** The real Settings screen over the host's validated reference, as `MeasureApp` stores it. */
@Composable
internal fun SettingsStandIn(s: FlowState) {
    val (length, width) = when (val r = s.reference) {
        is ReferenceCheck.Valid -> r.lengthMeters to r.widthMeters
        is ReferenceCheck.NeedsCorrection -> r.rawLengthMeters to r.rawWidthMeters
    }
    SettingsScreen(
        stickLengthMeters = length,
        stickWidthMeters = width,
        unit = s.unit,
        onLength = { v -> s.storeReference(ReferenceDimensions.check(v, width)) },
        onWidth = { v -> s.storeReference(ReferenceDimensions.check(length, v)) },
        onUnit = { u -> s.storeUnit(u) },
        onBack = { s.step = s.settingsReturn },
    )
}

/** `CameraScreen`'s request lifecycle and controls, with [FakeCamera] in place of CameraX. */
@Composable
internal fun CameraStandIn(s: FlowState) {
    val screen = s.cameraScreen
    var state by remember { mutableStateOf(CaptureState()) }
    val controller = remember {
        CaptureController<CapturedImage>(
            discard = { s.discarded += it; it.bitmap.recycle() },
            requestIdOf = { it.metadata.requestId },
            onStateChanged = { state = it },
        )
    }
    DisposableEffect(Unit) { onDispose { controller.dispose() } }
    Box(Modifier.fillMaxSize()) {
        CameraControls(
            captureEnabled = state.canCapture(permissionGranted = true, cameraReady = true),
            capturing = state.busy,
            message = state.error,
            onSettings = { s.settingsReturn = HostStep.Capture; s.step = HostStep.Settings },
            onHelp = {},
            onCapture = {
                controller.launch { id ->
                    s.camera.pending += FakeCamera.Pending(screen, id) { rid, outcome ->
                        controller.complete(rid, outcome)?.let { s.onCaptured(it) }
                    }
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
