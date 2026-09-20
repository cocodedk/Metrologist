package com.cocode.measureapp.geometry

/**
 * Stick of known real-world dimensions (engine is unit-agnostic). [totalLength] is the long
 * axis, divided into [bandCount] equal bands. [width] is the across-stick dimension in the
 * SAME unit as [totalLength]; `0.0` means width unknown/ignored (length-only calibration).
 * [totalLength] must be finite and > 0; [width] must be finite and >= 0 (NaN, infinite and
 * negative values are always rejected). Use [validated] to get an explicit failure instead.
 */
data class StickProfile(
    val totalLength: Double,
    val bandCount: Int = 4,
    val width: Double = 0.0,
) {
    init {
        require(bandCount >= 1) { "bandCount must be >= 1, got $bandCount" }
        require(totalLength.isFinite() && totalLength > 0.0) {
            "totalLength must be finite and > 0, got $totalLength"
        }
        require(width.isFinite() && width >= 0.0) { "width must be finite and >= 0, got $width" }
    }

    companion object {
        /**
         * Builds a profile, or returns a [MeasurementFailureReason.INVALID_REFERENCE_DIMENSIONS]
         * failure for dimensions the constructor would reject (never a fabricated measurement).
         */
        fun validated(totalLength: Double, bandCount: Int = 4, width: Double = 0.0): ProfileValidation =
            try {
                ProfileValidation.Valid(StickProfile(totalLength, bandCount, width))
            } catch (e: IllegalArgumentException) {
                ProfileValidation.Rejected(
                    MeasurementOutcome.Failure(MeasurementFailureReason.INVALID_REFERENCE_DIMENSIONS, e.message),
                )
            }
    }
}

/** Result of [StickProfile.validated]: a usable profile or an explicit rejected outcome. */
sealed class ProfileValidation {
    data class Valid(val profile: StickProfile) : ProfileValidation()
    data class Rejected(val failure: MeasurementOutcome.Failure) : ProfileValidation()
}

/** Orthonormal camera-frame triad: `e1`,`e2` span the plane, `normal` is perpendicular. */
data class PlaneFrame(val e1: Vec3, val e2: Vec3, val normal: Vec3)

/** Which solver produced a [PlaneSolution]. */
enum class SolverKind { RECTANGLE, GRAVITY }

/** A recovered plane orientation with the solver used and a confidence in `[0,1]`. */
data class PlaneSolution(val frame: PlaneFrame, val solver: SolverKind, val confidence: Double)
