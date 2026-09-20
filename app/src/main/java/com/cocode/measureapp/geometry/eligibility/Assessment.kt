package com.cocode.measureapp.geometry.eligibility

import com.cocode.measureapp.geometry.CameraIntrinsics
import com.cocode.measureapp.geometry.MeasurementFailureReason
import com.cocode.measureapp.geometry.MeasurementResult
import com.cocode.measureapp.geometry.PlaneFrame
import com.cocode.measureapp.geometry.ScaleResult
import com.cocode.measureapp.geometry.SolverKind
import com.cocode.measureapp.geometry.StickProfile
import com.cocode.measureapp.geometry.SurfaceOrientation
import com.cocode.measureapp.geometry.Vec2
import com.cocode.measureapp.geometry.frames.AlignedGravity
import com.cocode.measureapp.geometry.frames.CalibrationProvenance

/** Whether the plane was checked against the selected wall/floor surface with valid gravity. */
enum class SurfaceConsistency {
    /** Valid gravity agrees with the selected surface within the 5-degree tolerance. */
    CONSISTENT,

    /** No usable gravity: the selection could not be checked. Never reported as a match. */
    UNVERIFIED_NO_GRAVITY,
}

/** The plane assumption a successful measurement rests on; shown in diagnostics. */
enum class PlaneAssumption {
    /** The marked object is a rectangle; checked for back-projected orthogonality only. */
    RECTANGLE_TARGET,

    /** Floor/table normal equals gravity; the in-plane shape is unconstrained. */
    FLOOR_NORMAL_FROM_GRAVITY,

    /** Wall contains gravity and is ASSUMED to face the camera: its azimuth is unresolved. */
    WALL_FACES_CAMERA,
}

/** Why a candidate was excluded before ranking. Rejected candidates are never revived. */
enum class IneligibleReason {
    RECTANGLE_REJECTED,
    NOT_ORTHOGONAL,
    SURFACE_CONTRADICTION,
    GRAVITY_UNAVAILABLE,
    AZIMUTH_UNRESOLVED,
    PROJECTION_UNUSABLE,
    REFERENCE_INCONSISTENT,
    NUMERICAL,
}

/** Everything one measurement attempt knows, in the SAME aligned image frame. */
data class EligibilityInput(
    val corners: List<Vec2>,
    val stick: List<Vec2>,
    val intrinsics: CameraIntrinsics,
    val calibration: CalibrationProvenance,
    val gravity: AlignedGravity,
    val profile: StickProfile,
    val orientation: SurfaceOrientation,
)

/** Eligibility verdict for one solver's candidate plane. */
sealed interface Assessment {
    val solver: SolverKind

    /**
     * A candidate that passed every applicable check, already measured on its plane.
     * [confidence] is in `(0, 1]`; [caps] names each bound applied to it.
     */
    data class Eligible(
        override val solver: SolverKind,
        val frame: PlaneFrame,
        val measurement: MeasurementResult,
        val scale: ScaleResult,
        val confidence: Double,
        val assumption: PlaneAssumption,
        val surface: SurfaceConsistency,
        val referenceChecked: Boolean,
        val caps: List<String>,
    ) : Assessment

    /** An excluded candidate with its shared-vocabulary [failure] and actionable [detail]. */
    data class Ineligible(
        override val solver: SolverKind,
        val reason: IneligibleReason,
        val failure: MeasurementFailureReason,
        val detail: String,
    ) : Assessment
}
