package com.cocode.measureapp.core

import com.cocode.measureapp.geometry.CameraIntrinsics
import com.cocode.measureapp.geometry.MetrologyEngine
import com.cocode.measureapp.geometry.SolverKind
import com.cocode.measureapp.geometry.StickProfile
import com.cocode.measureapp.geometry.SurfaceOrientation
import com.cocode.measureapp.geometry.Vec2
import com.cocode.measureapp.geometry.Vec3
import kotlin.math.roundToInt

/**
 * Formatted view model produced by [MeasurementPresenter]. All string fields are already
 * locale-formatted for display; no further formatting is required in the UI layer.
 */
data class MeasurementView(
    val usable: Boolean,
    val width: String,
    val height: String,
    val area: String,
    val diagonal: String,
    val cornerAngles: List<Double>,   // degrees, each rounded to 1 decimal
    val confidenceLabel: String,
    val confidencePercent: Int,
    val solverName: String,
    val caveats: List<String>,
)

/**
 * Pure-Kotlin presenter: drives [MetrologyEngine.measureHybrid] and converts the raw
 * [com.cocode.measureapp.geometry.EngineResult] into a display-ready [MeasurementView].
 * No Android imports; safe to unit-test on the JVM.
 */
object MeasurementPresenter {
    /** [stick] is the 4 image corners of the stick's bounding box (clockwise around the quad). */
    fun present(
        corners: List<Vec2>,
        stick: List<Vec2>,
        intrinsics: CameraIntrinsics,
        gravity: Vec3,
        profile: StickProfile,
        orientation: SurfaceOrientation,
        unit: LengthUnit,
    ): MeasurementView = toView(
        MetrologyEngine.measureHybrid(corners, stick, intrinsics, profile, gravity, orientation),
        unit,
    )

    /** Visible for testing: converts a pre-built [EngineResult] to a [MeasurementView]. */
    internal fun toView(
        r: com.cocode.measureapp.geometry.EngineResult,
        unit: LengthUnit,
    ): MeasurementView {
        val m = r.measurement
        val solver = r.diagnostics?.solver ?: r.solution.solver
        val solverName = when (solver) {
            SolverKind.RECTANGLE -> "Rectangle method"
            SolverKind.GRAVITY -> "Tilt-sensor fallback"
        }
        return MeasurementView(
            usable = r.confidence > 0.0,
            width = Units.formatLength(m.width, unit),
            height = Units.formatLength(m.height, unit),
            area = Units.formatArea(m.area, unit),
            diagonal = Units.formatLength(m.diagonal, unit),
            cornerAngles = m.cornerAngles.map { kotlin.math.round(it * 10) / 10.0 },
            confidenceLabel = DiagnosticsText.confidenceLabel(r.confidence),
            confidencePercent = (r.confidence * 100).roundToInt(),
            solverName = solverName,
            caveats = r.diagnostics?.let { DiagnosticsText.caveats(it) } ?: emptyList(),
        )
    }
}
