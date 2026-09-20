package com.cocode.measureapp.geometry

/**
 * Stable failure vocabulary shared by the engine (producer) and the presenter (consumer).
 * Producers map their expected domain errors onto these reasons, e.g. a collapsed stick box
 * is [INVALID_STICK_CORNERS]; a ray parallel to the plane or an unusable solver is
 * [UNSUPPORTED_GEOMETRY]; non-finite intermediate values are [NUMERICAL_FAILURE].
 */
enum class MeasurementFailureReason {
    INVALID_OBJECT_CORNERS,
    INVALID_STICK_CORNERS,
    INVALID_REFERENCE_DIMENSIONS,
    METADATA_UNAVAILABLE,
    UNSUPPORTED_GEOMETRY,
    NUMERICAL_FAILURE,
}

/**
 * Explicit result of one measurement attempt. A failure never carries a measurement, and a
 * success can never hold zero-valued or non-finite dimensions, so failure cannot be coerced
 * into a fabricated `MeasurementResult(0, ...)`.
 */
sealed class MeasurementOutcome {
    /** True only for [Success]; the sole outcome that may be displayed or exported. */
    abstract val usable: Boolean

    /** The success, or `null` for a failure. */
    fun successOrNull(): Success? = this as? Success

    /**
     * A usable measurement with its evidence: the [solver] used, the [orientation] assumed for
     * the surface, the stick [scale], the overall [confidence] in `(0, 1]`, and optional
     * quality [diagnostics] (tilt, scale agreement).
     */
    data class Success(
        val measurement: MeasurementResult,
        val solver: SolverKind,
        val orientation: SurfaceOrientation,
        val scale: ScaleResult,
        val confidence: Double,
        val diagnostics: MeasurementDiagnostics? = null,
    ) : MeasurementOutcome() {
        override val usable: Boolean get() = true

        init {
            val m = measurement
            requirePositive("width", m.width)
            requirePositive("height", m.height)
            requirePositive("area", m.area)
            requirePositive("diagonal", m.diagonal)
            require(m.cornerAngles.size == 4) { "need 4 corner angles, got ${m.cornerAngles.size}" }
            require(m.cornerAngles.all { it.isFinite() }) { "corner angles must be finite" }
            requirePositive("scale", scale.scale)
            require(scale.agreement.isFinite() && scale.agreement >= 0.0) {
                "scale agreement must be finite and >= 0, got ${scale.agreement}"
            }
            require(confidence.isFinite() && confidence > 0.0 && confidence <= 1.0) {
                "success confidence must be in (0, 1], got $confidence"
            }
        }
    }

    /** A failed attempt: a [reason] from the shared vocabulary and an optional [detail]. */
    data class Failure(
        val reason: MeasurementFailureReason,
        val detail: String? = null,
    ) : MeasurementOutcome() {
        override val usable: Boolean get() = false
    }
}

private fun requirePositive(name: String, value: Double) {
    require(value.isFinite() && value > 0.0) { "$name must be finite and > 0, got $value" }
}
