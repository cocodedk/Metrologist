package com.cocode.measureapp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.cocode.measureapp.core.LengthUnit
import com.cocode.measureapp.core.dimensions.ReferenceCheck
import com.cocode.measureapp.data.SettingsRepository
import com.cocode.measureapp.detect.OpenCvStickDetector
import com.cocode.measureapp.export.AnnotatedExporter
import com.cocode.measureapp.ui.surface.MarkingFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private enum class Step { Capture, Mark, Results, Settings, Help }

/** Top-level flow: capture -> mark -> results, with settings + help side-screens. */
@Composable
fun MeasureApp() {
    val context = LocalContext.current
    val repo     = remember { SettingsRepository(context.applicationContext) }
    val detector = remember { OpenCvStickDetector() }
    val scope    = rememberCoroutineScope()
    val stickLength by repo.stickLengthMeters.collectAsState(initial = SettingsRepository.DEFAULT_LENGTH_M)
    val stickWidth  by repo.stickWidthMeters.collectAsState(initial = SettingsRepository.DEFAULT_WIDTH_M)
    val unit        by repo.unit.collectAsState(initial = LengthUnit.METERS)
    // Validated dimensions; null until loaded. Raw values are never turned into a profile here.
    val reference: ReferenceCheck? by repo.referenceDimensions.collectAsState(initial = null)

    var step     by remember { mutableStateOf(Step.Capture) }
    var captured by remember { mutableStateOf<CapturedImage?>(null) }
    // Marks, surface choice and the revision-bound result live here so they survive
    // Mark <-> Results <-> Settings navigation; every input change goes through the shared
    // invalidation in MarkingFlow.
    var flow     by remember { mutableStateOf(MarkingFlow()) }
    var settingsReturn by remember { mutableStateOf(Step.Capture) }

    fun retake() {
        flow = flow.retake()   // drop the old result and export before any new capture
        step = Step.Capture
    }

    // A stored settings change (also one landing after its edit) invalidates the current result.
    var seenSettings by remember { mutableStateOf<Pair<ReferenceCheck, LengthUnit>?>(null) }
    LaunchedEffect(reference, unit) {
        val now = reference?.let { it to unit } ?: return@LaunchedEffect
        if (seenSettings != null && seenSettings != now) flow = flow.settingsChanged()
        seenSettings = now
    }

    // System back steps through the flow instead of leaving the app.
    BackHandler(enabled = step != Step.Capture) {
        when (step) {
            Step.Results  -> step = Step.Mark
            Step.Settings -> step = settingsReturn
            Step.Mark     -> retake()
            else          -> step = Step.Capture
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            when (step) {
                Step.Capture -> CameraScreen(
                    onCaptured = { img ->
                        captured = img
                        // New image revision: the shared invalidation drops any old result
                        // and starts fresh marks for the new photo.
                        flow = flow.captured()
                        step = Step.Mark
                    },
                    onSettings = { settingsReturn = Step.Capture; step = Step.Settings },
                    onHelp     = { step = Step.Help },
                )

                Step.Help -> HelpScreen(onBack = { step = Step.Capture })

                Step.Mark -> {
                    val img = captured
                    if (img == null) {
                        LaunchedEffect(Unit) { step = Step.Capture }
                    } else {
                        MarkScreen(
                            image                = img,
                            orientation          = flow.orientation,
                            onOrientationChanged = { o -> flow = flow.orientationSelected(o) },
                            initialCorners       = flow.corners,
                            initialStick         = flow.stick,
                            onMarkChanged        = { c, s -> flow = flow.marksChanged(c, s) },
                            detector             = detector,
                            onMeasure            = { c, s ->
                                val ref = reference
                                if (ref != null) {
                                    // Scene's aligned gravity + calibration; failures stay here.
                                    flow = flow.marksChanged(c, s).attempt(c, s, img.scene, ref, unit)
                                    if (flow.usableView != null) step = Step.Results
                                }
                            },
                            onBack               = { retake() },
                            failureMessage       = flow.failureMessage,
                            onSettings           = { settingsReturn = Step.Mark; step = Step.Settings },
                        )
                    }
                }

                Step.Results -> {
                    val v   = flow.usableView
                    val img = captured
                    if (v == null || img == null) {
                        // No usable result for the current revision: back to correction.
                        LaunchedEffect(Unit) { step = Step.Mark }
                    } else {
                        ResultsScreen(
                            view          = v,
                            exportEnabled = flow.exportEnabled,
                            onExport      = {
                                if (flow.exportEnabled && flow.usableView == v) scope.launch(Dispatchers.IO) {
                                    AnnotatedExporter.shareAnnotated(context, img.bitmap, v)
                                }
                            },
                            onRemark      = { step = Step.Mark },
                            onDone        = { retake() },
                        )
                    }
                }

                Step.Settings -> SettingsScreen(
                    stickLengthMeters = stickLength,
                    stickWidthMeters  = stickWidth,
                    unit     = unit,
                    onLength = { value -> flow = flow.settingsChanged(); scope.launch { repo.setStickLengthMeters(value) } },
                    onWidth  = { value -> flow = flow.settingsChanged(); scope.launch { repo.setStickWidthMeters(value) } },
                    onUnit   = { value ->
                        if (value != unit) flow = flow.settingsChanged()
                        scope.launch { repo.setUnit(value) }
                    },
                    onBack   = { step = settingsReturn },
                )
            }
        }
    }
}
