package com.cocode.measureapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.cocode.measureapp.R
import com.cocode.measureapp.detect.DeferredStickDetector
import com.cocode.measureapp.detect.StickDetector
import com.cocode.measureapp.capture.gravity.GravitySample
import com.cocode.measureapp.geometry.SurfaceOrientation
import com.cocode.measureapp.geometry.levelTiltFromDeviceDown
import com.cocode.measureapp.geometry.surfaceFromTilt
import com.cocode.measureapp.geometry.Vec2
import com.cocode.measureapp.model.UiText
import com.cocode.measureapp.stick.StickBox
import com.cocode.measureapp.ui.measurement.MarkControls
import com.cocode.measureapp.ui.measurement.defaultObjectCorners
import com.cocode.measureapp.ui.measurement.defaultStickBox
import com.cocode.measureapp.ui.measurement.MarkStatus
import com.cocode.measureapp.ui.surface.SurfaceSelector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Marking screen: two adjustable 4-corner boxes (GreenRead over the object, StaffRed around the
 * stick) on the captured photo. One-finger drag near a handle moves the handle; one-finger drag
 * far from any handle pans. Two-finger pinch zooms + pans. A magnifier loupe aids precise placement.
 */
@Composable
fun MarkScreen(
    image: CapturedImage,
    orientation: SurfaceOrientation,
    onOrientationChanged: (SurfaceOrientation) -> Unit,
    initialCorners: List<Vec2>? = null,
    initialStick: List<Vec2>? = null,
    onMarkChanged: (List<Vec2>, List<Vec2>) -> Unit = { _, _ -> },
    detector: StickDetector = DeferredStickDetector,
    /**
     * The user's raw object corners and stick box. The caller validates and measures them with
     * the visible [orientation]; a rejection comes back as [failureMessage], marks untouched.
     */
    onMeasure: (List<Vec2>, List<Vec2>) -> Unit,
    /** Retake: leaves marking for a new capture. */
    onBack: () -> Unit,
    /** Correction text for the last failed attempt at the current input revision. */
    failureMessage: UiText? = null,
    onSettings: () -> Unit = {},
) {
    var hintShown by remember { mutableStateOf(false) }
    val capturedTilt = remember(image) {
        (image.metadata.gravity as? GravitySample.Available)
            ?.let { levelTiltFromDeviceDown(it.down, image.metadata.rotationDegrees) }
    }
    LaunchedEffect(capturedTilt) {
        surfaceFromTilt(capturedTilt)?.let(onOrientationChanged)
    }
    val reportMarks by rememberUpdatedState(onMarkChanged)
    val bmp = image.bitmap
    val img = remember(bmp) { bmp.asImageBitmap() }
    val w = bmp.width.toDouble(); val h = bmp.height.toDouble()
    fun defCorners() = defaultObjectCorners(w, h)
    fun defStick() = defaultStickBox(w, h)
    val resources = LocalResources.current
    val cornerDragHint = stringResource(R.string.mark_hint)
    val detectingNote = stringResource(R.string.mark_detecting)
    val objectLabel = stringResource(R.string.mark_label_object)
    val stickLabel = stringResource(R.string.mark_label_stick)

    val corners = remember { mutableStateListOf<Vec2>().apply { addAll(initialCorners ?: defCorners()) } }
    val stick   = remember { mutableStateListOf<Vec2>().apply { addAll(initialStick ?: defStick()) } }
    var resetGen by remember { mutableStateOf(0) }
    var note by remember { mutableStateOf(if (initialStick == null) detectingNote else cornerDragHint) }

    LaunchedEffect(image) {
        if (initialStick != null) return@LaunchedEffect   // re-marking: keep the user's stick box
        val gen = resetGen
        val r = withContext(Dispatchers.Default) { detector.detect(bmp) }
        val a = r?.points?.firstOrNull(); val b = r?.points?.lastOrNull()
        if (a != null && b != null && a.distanceTo(b) > 1.0 && resetGen == gen) {
            StickBox.fromDetectedEnds(a, b).forEachIndexed { i, corner -> stick[i] = corner }
            note = resources.getString(R.string.mark_detected, (r.confidence * 100).toInt())
            reportMarks(corners.toList(), stick.toList())
        } else if (resetGen == gen) {
            note = cornerDragHint
        }
    }

    // The photo IS the screen: the controls float over it on a scrim rather than sitting in a
    // band below it, and the hint and the surface question only appear when they are needed.
    Box(Modifier.fillMaxSize()) {
        MarkCanvas(
            bmp = bmp,
            img = img,
            corners = corners,
            stick = stick,
            objectLabel = objectLabel,
            stickLabel = stickLabel,
            viewResets = resetGen,
            onMarksEdited = { reportMarks(corners.toList(), stick.toList()) },
        )
        if (hintShown || failureMessage != null) {
            MarkStatus(note, failureMessage, Modifier.align(Alignment.TopStart))
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.55f)),
        ) {
            // Asked only when the camera's pitch leaves the answer open; otherwise the phone
            // already knows whether it was pointed at a wall or at a floor.
            if (surfaceFromTilt(capturedTilt) == null) {
                SurfaceSelector(orientation, onOrientationChanged)
            }
            MarkControls(
                onReset = {
                    resetGen++
                    corners.clear(); corners.addAll(defCorners())
                    stick.clear(); stick.addAll(defStick())
                    note = cornerDragHint
                    reportMarks(corners.toList(), stick.toList())
                },
                onRetake = onBack,
                onSettings = onSettings,
                onToggleHint = { hintShown = !hintShown },
                hintShown = hintShown,
                // Validation belongs to the measurement producer; invalid marks return as a failure.
                onMeasure = { onMeasure(corners.toList(), stick.toList()) },
            )
        }
    }
}
