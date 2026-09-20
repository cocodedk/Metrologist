package com.cocode.measureapp.contracts

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.cocode.measureapp.core.LengthUnit
import com.cocode.measureapp.core.dimensions.ReferenceCheck
import com.cocode.measureapp.core.measurement.MeasurementAttempt
import com.cocode.measureapp.core.measurement.OutcomeEngine
import com.cocode.measureapp.core.measurement.OutcomeRequest
import com.cocode.measureapp.geometry.MeasurementOutcome
import com.cocode.measureapp.geometry.Vec2
import com.cocode.measureapp.ui.CapturedImage
import com.cocode.measureapp.ui.MarkScreen
import com.cocode.measureapp.ui.ResultsScreen
import com.cocode.measureapp.ui.surface.MarkingFlow

enum class HostStep { Capture, Mark, Results, Settings }

/**
 * State held at the host's boundary, wired exactly as `MeasureApp` wires it (whose CameraX and
 * DataStore dependencies cannot be injected): one [MarkingFlow], the captured image, validated
 * reference settings and the unit. The real production engine runs behind a recorder.
 */
class FlowState(
    reference: ReferenceCheck = ReferenceCheck.Valid(0.1, 0.02),
    unit: LengthUnit = LengthUnit.CENTIMETERS,
    start: HostStep = HostStep.Mark,
) {
    var step by mutableStateOf(start)
    var captured by mutableStateOf<CapturedImage?>(null)
    var flow by mutableStateOf(MarkingFlow())
    var reference by mutableStateOf(reference)
    var unit by mutableStateOf(unit)
    var markGeneration by mutableIntStateOf(0)
    var cameraScreen by mutableIntStateOf(0)
    var settingsReturn = HostStep.Mark
    var exports = 0
    val requests = mutableListOf<OutcomeRequest>()
    val outcomes = mutableListOf<MeasurementOutcome>()
    val delivered = mutableListOf<CapturedImage>()
    val discarded = mutableListOf<CapturedImage>()
    val camera = FakeCamera()

    /** The production producer ([MeasurementAttempt.engine]) with every request/outcome recorded. */
    val engine: OutcomeEngine = { r -> requests += r; MeasurementAttempt.engine(r).also { outcomes += it } }

    /** `MeasureApp.onCaptured`: a new image revision with fresh marks, then marking. */
    fun onCaptured(img: CapturedImage) {
        delivered += img
        captured = img
        flow = flow.captured()
        step = HostStep.Mark
        markGeneration++
    }

    /** Marks placed on the current photo, reported through the marking callback. */
    fun place(corners: List<Vec2>, stick: List<Vec2>) {
        flow = flow.marksChanged(corners, stick)
        markGeneration++
    }

    fun load(img: CapturedImage, corners: List<Vec2>, stick: List<Vec2>) {
        onCaptured(img)
        place(corners, stick)
    }

    /** `MeasureApp`'s Measure callback. */
    fun measure(corners: List<Vec2>, stick: List<Vec2>) {
        val img = captured ?: return
        flow = flow.marksChanged(corners, stick).attempt(corners, stick, img.scene, reference, unit, engine)
        if (flow.usableView != null) step = HostStep.Results
    }

    fun retake() {
        flow = flow.retake()
        step = HostStep.Capture
        cameraScreen++
    }

    /** `MeasureApp`'s settings effect: a changed stored value invalidates the result. */
    fun storeReference(value: ReferenceCheck) {
        if (value != reference) flow = flow.settingsChanged()
        reference = value
    }

    fun storeUnit(value: LengthUnit) {
        if (value != unit) flow = flow.settingsChanged()
        unit = value
    }
}

@Composable
fun FlowHost(s: FlowState) {
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            when (s.step) {
                HostStep.Capture -> key(s.cameraScreen) { CameraStandIn(s) }
                HostStep.Mark -> {
                    val img = s.captured
                    if (img == null) LaunchedEffect(Unit) { s.step = HostStep.Capture } else key(img, s.markGeneration) {
                        MarkScreen(
                            image = img,
                            orientation = s.flow.orientation,
                            onOrientationChanged = { o -> s.flow = s.flow.orientationSelected(o) },
                            initialCorners = s.flow.corners,
                            initialStick = s.flow.stick,
                            onMarkChanged = { c, st -> s.flow = s.flow.marksChanged(c, st) },
                            onMeasure = { c, st -> s.measure(c, st) },
                            onBack = { s.retake() },
                            failureMessage = s.flow.failureMessage,
                            onSettings = { s.settingsReturn = HostStep.Mark; s.step = HostStep.Settings },
                        )
                    }
                }
                HostStep.Results -> {
                    val v = s.flow.usableView
                    if (v == null || s.captured == null) LaunchedEffect(Unit) { s.step = HostStep.Mark } else ResultsScreen(
                        view = v,
                        exportEnabled = s.flow.exportEnabled,
                        onExport = { if (s.flow.exportEnabled && s.flow.usableView == v) s.exports++ },
                        onRemark = { s.step = HostStep.Mark },
                        onDone = { s.retake() },
                    )
                }
                HostStep.Settings -> SettingsStandIn(s)
            }
        }
    }
}
