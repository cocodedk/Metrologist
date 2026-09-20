package com.cocode.measureapp.geometry.rectangle

import com.cocode.measureapp.geometry.MeasurementFailureReason
import com.cocode.measureapp.geometry.PlaneFrame
import com.cocode.measureapp.geometry.Vec2
import com.cocode.measureapp.geometry.Vec3
import com.cocode.measureapp.geometry.frames.CalibrationProvenance
import kotlin.math.abs
import kotlin.math.hypot

/**
 * A vanishing point kept in homogeneous image form. A point at infinity is a valid direction
 * (image-parallel edges), never a failure; only the zero vector is degenerate.
 */
sealed interface VanishingDirection {
    /** Homogeneous image point `(x, y, w)`; `w == 0` means at infinity. */
    val homogeneous: Vec3

    data class Finite(override val homogeneous: Vec3, val point: Vec2) : VanishingDirection
    data class AtInfinity(override val homogeneous: Vec3, val imageDirection: Vec2) : VanishingDirection

    companion object {
        /** Labels a nonzero homogeneous point; used for evidence only, never for computation. */
        fun classify(v: Vec3): VanishingDirection {
            val planar = hypot(v.x, v.y)
            return if (abs(v.z) <= RectangleTolerances.INFINITY_RELATIVE * planar) {
                AtInfinity(v, Vec2(v.x / planar, v.y / planar))
            } else {
                Finite(v, Vec2(v.x / v.z, v.y / v.z))
            }
        }
    }
}

/** Why no plane candidate exists, mapped onto the shared failure vocabulary. */
enum class RectangleRejection(val failure: MeasurementFailureReason) {
    INVALID_CORNER_COUNT(MeasurementFailureReason.INVALID_OBJECT_CORNERS),
    NON_FINITE_CORNERS(MeasurementFailureReason.INVALID_OBJECT_CORNERS),
    INVALID_INTRINSICS(MeasurementFailureReason.METADATA_UNAVAILABLE),

    /** An edge's endpoints coincide, so it defines no line. */
    COINCIDENT_CORNERS(MeasurementFailureReason.INVALID_OBJECT_CORNERS),

    /** Two opposite edges lie on one image line: their intersection is the zero vector. */
    ZERO_VANISHING_VECTOR(MeasurementFailureReason.INVALID_OBJECT_CORNERS),

    /** Both edge pairs back-project to the same direction; no plane is spanned. */
    COINCIDENT_DIRECTIONS(MeasurementFailureReason.UNSUPPORTED_GEOMETRY),

    /** A corner ray is parallel to, or on the far side of, the recovered plane. */
    PROJECTION_UNUSABLE(MeasurementFailureReason.UNSUPPORTED_GEOMETRY),

    /** Corner rays graze the plane (see [RectangleTolerances.MIN_RAY_PLANE_COS]). */
    GRAZING_VIEW(MeasurementFailureReason.UNSUPPORTED_GEOMETRY),

    /** Half-pixel marking noise changes the rectified shape too much. */
    ILL_CONDITIONED(MeasurementFailureReason.UNSUPPORTED_GEOMETRY),

    NUMERICAL_FAILURE(MeasurementFailureReason.NUMERICAL_FAILURE),
}

/**
 * Evidence a selector needs to judge a rectangle candidate. None of it proves the target is a
 * rectangle or that the plane matches the chosen wall/floor surface.
 *
 * @property orthogonalityResidual `|d1 · d2|` of the unit back-projected edge directions;
 *   0 for a true rectangle with exact intrinsics. Scale-independent.
 * @property minRayPlaneCosine smallest `|normal · ray|` over the four unit corner rays.
 * @property halfPixelAspectSensitivity worst-case relative aspect-ratio change under
 *   [RectangleTolerances.MARK_UNCERTAINTY_PX] marking noise (sum over 8 coordinates).
 * @property halfPixelNormalShiftRad largest normal rotation under the same perturbations.
 */
data class RectangleEvidence(
    val widthVanishing: VanishingDirection,
    val heightVanishing: VanishingDirection,
    val orthogonalityResidual: Double,
    val minRayPlaneCosine: Double,
    val halfPixelAspectSensitivity: Double,
    val halfPixelNormalShiftRad: Double,
)

/**
 * Outcome of the rectangle solver for one aligned scene. Both variants carry the calibration
 * [provenance] and the caller's [sceneId] so a candidate cannot be applied to another input.
 */
sealed interface RectangleCandidate {
    val provenance: CalibrationProvenance
    val sceneId: String?

    /** A finite orthonormal plane frame plus its evidence; still subject to eligibility rules. */
    data class Accepted(
        val frame: PlaneFrame,
        val evidence: RectangleEvidence,
        override val provenance: CalibrationProvenance,
        override val sceneId: String?,
    ) : RectangleCandidate

    /** No usable plane; [reason] is explicit and must not be revived with a confidence. */
    data class Rejected(
        val reason: RectangleRejection,
        val detail: String,
        override val provenance: CalibrationProvenance,
        override val sceneId: String?,
    ) : RectangleCandidate
}
