package com.cocode.measureapp.core

import com.cocode.measureapp.core.measurement.CorrectionText
import com.cocode.measureapp.geometry.CameraIntrinsics
import com.cocode.measureapp.geometry.EngineResult
import com.cocode.measureapp.geometry.MeasurementDiagnostics
import com.cocode.measureapp.geometry.MeasurementFailureReason
import com.cocode.measureapp.geometry.MeasurementOutcome
import com.cocode.measureapp.geometry.MeasurementResult
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
    /** Why the attempt failed; null for a success (and for the legacy zero-confidence path). */
    val failure: MeasurementFailureReason? = null,
    /** Specific correction text for an unusable view; null for a success. */
    val message: String? = null,
)

/**
 * One engine request built from the marking flow. [orientation] is the user's currently
 * visible wall vs floor/table selection — never inferred from camera pose or screen rotation —
 * and [revision] is the [MeasurementSession] revision the request was built for.
 */
data class MeasurementRequest(
    val corners: List<Vec2>,
    val stick: List<Vec2>,
    val intrinsics: CameraIntrinsics,
    val gravity: Vec3,
    val profile: StickProfile,
    val orientation: SurfaceOrientation,
    val revision: Int,
)

/** Engine seam used by [MeasurementPresenter.present]; tests substitute a recording engine. */
typealias MeasurementEngine = (MeasurementRequest) -> EngineResult

/**
 * Pure-Kotlin presenter: formats a typed [MeasurementOutcome] (the production path, fed by
 * [com.cocode.measureapp.core.measurement.MeasurementAttempt]) or a legacy [EngineResult] from
 * [MetrologyEngine.measureHybrid] into a display-ready [MeasurementView].
 * No Android imports; safe to unit-test on the JVM.
 */
object MeasurementPresenter {
    /** The production engine: the hybrid solver with the request's explicit orientation. */
    val hybridEngine: MeasurementEngine = { r ->
        MetrologyEngine.measureHybrid(r.corners, r.stick, r.intrinsics, r.profile, r.gravity, r.orientation)
    }

    /** Runs [request] through [engine] and formats the result in [unit]. */
    fun present(
        request: MeasurementRequest,
        unit: LengthUnit,
        engine: MeasurementEngine = hybridEngine,
    ): MeasurementView = toView(engine(request), unit)

    /** [stick] is the 4 image corners of the stick's bounding box (clockwise around the quad). */
    fun present(
        corners: List<Vec2>,
        stick: List<Vec2>,
        intrinsics: CameraIntrinsics,
        gravity: Vec3,
        profile: StickProfile,
        orientation: SurfaceOrientation,
        unit: LengthUnit,
    ): MeasurementView = present(
        MeasurementRequest(corners, stick, intrinsics, gravity, profile, orientation, revision = 0),
        unit,
    )

    /**
     * Formats a typed outcome (contract C14 consumer). A success is formatted in [unit]; a
     * failure keeps its reason, shows specific correction text and never shows dimensions.
     */
    fun present(outcome: MeasurementOutcome, unit: LengthUnit): MeasurementView = when (outcome) {
        is MeasurementOutcome.Success -> formatted(
            outcome.measurement, outcome.solver, outcome.confidence, outcome.diagnostics, unit,
        )
        is MeasurementOutcome.Failure -> unmeasured(outcome.reason, CorrectionText.message(outcome))
    }

    /**
     * Visible for testing: converts a pre-built legacy [EngineResult] to a [MeasurementView].
     * A zero-confidence result is the adapter's failure marker, so its zeroed placeholder
     * measurement is never formatted as dimensions.
     */
    internal fun toView(r: EngineResult, unit: LengthUnit): MeasurementView =
        if (r.confidence > 0.0) {
            formatted(r.measurement, r.diagnostics?.solver ?: r.solution.solver, r.confidence, r.diagnostics, unit)
        } else {
            unmeasured(null, LEGACY_FAILURE)
        }

    private fun formatted(
        m: MeasurementResult,
        solver: SolverKind,
        confidence: Double,
        diagnostics: MeasurementDiagnostics?,
        unit: LengthUnit,
    ) = MeasurementView(
        usable = true,
        width = Units.formatLength(m.width, unit),
        height = Units.formatLength(m.height, unit),
        area = Units.formatArea(m.area, unit),
        diagonal = Units.formatLength(m.diagonal, unit),
        cornerAngles = m.cornerAngles.map { kotlin.math.round(it * 10) / 10.0 },
        confidenceLabel = DiagnosticsText.confidenceLabel(confidence),
        confidencePercent = (confidence * 100).roundToInt(),
        solverName = when (solver) {
            SolverKind.RECTANGLE -> "Rectangle method"
            SolverKind.GRAVITY -> "Tilt-sensor fallback"
        },
        caveats = diagnostics?.let { DiagnosticsText.caveats(it) } ?: emptyList(),
    )

    private fun unmeasured(reason: MeasurementFailureReason?, message: String) = MeasurementView(
        usable = false,
        width = NOT_MEASURED, height = NOT_MEASURED, area = NOT_MEASURED, diagonal = NOT_MEASURED,
        cornerAngles = emptyList(),
        confidenceLabel = "Not measured",
        confidencePercent = 0,
        solverName = "None",
        caveats = emptyList(),
        failure = reason,
        message = message,
    )

    private const val NOT_MEASURED = "—"
    private const val LEGACY_FAILURE =
        "Could not measure confidently — check the markers and try a moderate angle."
}
