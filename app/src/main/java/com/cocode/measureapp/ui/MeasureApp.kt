package com.cocode.measureapp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.cocode.measureapp.core.LengthUnit
import com.cocode.measureapp.core.MeasurementView
import com.cocode.measureapp.data.SettingsRepository
import com.cocode.measureapp.detect.OpenCvStickDetector
import com.cocode.measureapp.export.AnnotatedExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private enum class Step { Capture, Mark, Results, Settings, Help }

/** Top-level flow: capture -> mark -> results, with a settings side-screen. */
@Composable
fun MeasureApp() {
    val context = LocalContext.current
    val repo = remember { SettingsRepository(context.applicationContext) }
    val detector = remember { OpenCvStickDetector() }
    val scope = rememberCoroutineScope()
    val stickLength by repo.stickLengthMeters.collectAsState(initial = SettingsRepository.DEFAULT_LENGTH_M)
    val stickWidth by repo.stickWidthMeters.collectAsState(initial = SettingsRepository.DEFAULT_WIDTH_M)
    val unit by repo.unit.collectAsState(initial = LengthUnit.METERS)

    var step by remember { mutableStateOf(Step.Capture) }
    var captured by remember { mutableStateOf<CapturedImage?>(null) }
    var view by remember { mutableStateOf<MeasurementView?>(null) }

    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        when (step) {
        Step.Capture -> CameraScreen(
            onCaptured = { img -> captured = img; step = Step.Mark },
            onSettings = { step = Step.Settings },
            onHelp = { step = Step.Help },
        )

        Step.Help -> HelpScreen(onBack = { step = Step.Capture })

        Step.Mark -> {
            val img = captured
            if (img == null) {
                LaunchedEffect(Unit) { step = Step.Capture }
            } else {
                MarkScreen(
                    image = img,
                    stickLengthMeters = stickLength,
                    stickWidthMeters = stickWidth,
                    unit = unit,
                    detector = detector,
                    onMeasured = { v -> view = v; step = Step.Results },
                    onBack = { step = Step.Capture },
                )
            }
        }

        Step.Results -> {
            val v = view
            val img = captured
            if (v == null) {
                LaunchedEffect(Unit) { step = Step.Capture }
            } else {
                ResultsScreen(
                    view = v,
                    onExport = {
                        if (img != null) scope.launch(Dispatchers.IO) {
                            AnnotatedExporter.shareAnnotated(context, img.bitmap, v)
                        }
                    },
                    onDone = { step = Step.Capture },
                )
            }
        }

        Step.Settings -> SettingsScreen(
            stickLengthMeters = stickLength,
            stickWidthMeters = stickWidth,
            unit = unit,
            onLength = { value -> scope.launch { repo.setStickLengthMeters(value) } },
            onWidth = { value -> scope.launch { repo.setStickWidthMeters(value) } },
            onUnit = { value -> scope.launch { repo.setUnit(value) } },
            onBack = { step = Step.Capture },
        )
        }
    }
}
