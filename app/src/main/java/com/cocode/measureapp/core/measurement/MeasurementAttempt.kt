package com.cocode.measureapp.core.measurement

import com.cocode.measureapp.core.dimensions.ReferenceCheck
import com.cocode.measureapp.geometry.CameraIntrinsics
import com.cocode.measureapp.geometry.MeasurementFailureReason
import com.cocode.measureapp.geometry.MeasurementOutcome
import com.cocode.measureapp.geometry.MetrologyEngine
import com.cocode.measureapp.geometry.ProfileValidation
import com.cocode.measureapp.geometry.StickProfile
import com.cocode.measureapp.geometry.SurfaceOrientation
import com.cocode.measureapp.geometry.Vec2
import com.cocode.measureapp.geometry.frames.AlignedGravity
import com.cocode.measureapp.geometry.frames.CalibrationProvenance
import com.cocode.measureapp.model.CapturedScene

/**
 * One typed engine request (contract C10 input). [corners] and [stick] are the user's raw marks
 * in the aligned marking frame; the producer validates and orders them. [gravity] and
 * [calibration] come from the scene's aligned metadata, never from its legacy gravity vector.
 */
data class OutcomeRequest(
    val corners: List<Vec2>,
    val stick: List<Vec2>,
    val intrinsics: CameraIntrinsics,
    val calibration: CalibrationProvenance,
    val gravity: AlignedGravity,
    val profile: StickProfile,
    val orientation: SurfaceOrientation,
    val revision: Int,
)

/** Producer seam: the real engine in production, a recorder around it in tests. */
typealias OutcomeEngine = (OutcomeRequest) -> MeasurementOutcome

/** A request ready for the engine, or a failure found before the engine could run. */
sealed class Prepared {
    data class Ready(val request: OutcomeRequest) : Prepared()
    data class Rejected(val failure: MeasurementOutcome.Failure) : Prepared()
}

/** Builds and runs one measurement attempt from the marking flow's current inputs. */
object MeasurementAttempt {
    /** Production producer: node 03's eligibility-checked [MetrologyEngine.evaluate]. */
    val engine: OutcomeEngine = { r ->
        MetrologyEngine.evaluate(
            r.corners, r.stick, r.intrinsics, r.calibration, r.gravity, r.profile, r.orientation, r.revision,
        )
    }

    /**
     * Invalid reference settings stop the attempt with an explicit
     * `INVALID_REFERENCE_DIMENSIONS` failure (the settings' own correction text as detail);
     * nothing is measured with them and no profile is built from raw values.
     */
    fun prepare(
        corners: List<Vec2>,
        stick: List<Vec2>,
        scene: CapturedScene,
        reference: ReferenceCheck,
        orientation: SurfaceOrientation,
        revision: Int,
    ): Prepared {
        val valid = when (reference) {
            is ReferenceCheck.NeedsCorrection -> return Prepared.Rejected(
                MeasurementOutcome.Failure(MeasurementFailureReason.INVALID_REFERENCE_DIMENSIONS, reference.message),
            )
            is ReferenceCheck.Valid -> reference
        }
        val profile = when (val p = StickProfile.validated(valid.lengthMeters, width = valid.widthMeters)) {
            is ProfileValidation.Rejected -> return Prepared.Rejected(p.failure)
            is ProfileValidation.Valid -> p.profile
        }
        return Prepared.Ready(
            OutcomeRequest(
                corners.toList(), stick.toList(), scene.intrinsics, scene.calibration,
                scene.alignedGravity, profile, orientation, revision,
            ),
        )
    }

    /** Runs a prepared attempt; a pre-engine rejection is returned without calling [engine]. */
    fun run(prepared: Prepared, engine: OutcomeEngine = this.engine): MeasurementOutcome = when (prepared) {
        is Prepared.Rejected -> prepared.failure
        is Prepared.Ready -> engine(prepared.request)
    }
}
