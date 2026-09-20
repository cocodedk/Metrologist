package com.cocode.measureapp.ui.surface

import com.cocode.measureapp.core.LengthUnit
import com.cocode.measureapp.core.MeasurementEngine
import com.cocode.measureapp.core.MeasurementPresenter
import com.cocode.measureapp.core.MeasurementRequest
import com.cocode.measureapp.core.MeasurementSession
import com.cocode.measureapp.core.MeasurementView
import com.cocode.measureapp.core.dimensions.ReferenceCheck
import com.cocode.measureapp.core.measurement.MeasurementAttempt
import com.cocode.measureapp.core.measurement.OutcomeEngine
import com.cocode.measureapp.core.measurement.OutcomeRequest
import com.cocode.measureapp.geometry.CameraIntrinsics
import com.cocode.measureapp.geometry.MeasurementOutcome
import com.cocode.measureapp.geometry.StickProfile
import com.cocode.measureapp.geometry.SurfaceOrientation
import com.cocode.measureapp.geometry.Vec2
import com.cocode.measureapp.geometry.Vec3
import com.cocode.measureapp.model.CapturedScene

/** A formatted result together with the revision and surface selection it was computed for. */
data class BoundView(
    val view: MeasurementView,
    val revision: Int,
    val orientation: SurfaceOrientation,
)

/**
 * Measurement state for the current photo, held at the `MeasureApp` state boundary: the
 * user's marks, the visible wall vs floor/table [orientation], the shared revision [session]
 * and the result bound to it. Pure Kotlin (no Android imports) so the marking callbacks can be
 * driven on the JVM exactly as the screens drive them.
 *
 * Every input change goes through [invalidated] (the node-05 shared operation): mark edits,
 * settings changes, surface changes, Retake and a new capture. Camera pose never feeds
 * [orientation]; only [orientationSelected] changes it.
 */
data class MarkingFlow(
    val session: MeasurementSession = MeasurementSession(),
    val orientation: SurfaceOrientation = SurfaceOrientation.VERTICAL,
    val corners: List<Vec2>? = null,
    val stick: List<Vec2>? = null,
    val result: BoundView? = null,
) {
    /** The view for the current revision and selection: a success or an explained failure. */
    val currentView: MeasurementView?
        get() = result?.takeIf { it.revision == session.revision && it.orientation == orientation }?.view

    /** A usable view for the current revision; the only one Results may show. */
    val usableView: MeasurementView?
        get() = currentView?.takeIf { it.usable }

    /** Correction text for a failed attempt at the current revision, shown on the marking screen. */
    val failureMessage: String?
        get() = currentView?.takeIf { !it.usable }?.message

    /** Export only for a success recorded in the session at the current revision. */
    val exportEnabled: Boolean
        get() = session.exportEnabled && usableView != null

    /** The shared invalidation: new revision, no result; photo, marks and selection are kept. */
    fun invalidated(): MarkingFlow = copy(session = session.invalidate(), result = null)

    /**
     * A new photo: invalidate through the shared operation and start fresh marks. The surface
     * choice resets to the visibly selected wall default together with the marks.
     */
    fun captured(): MarkingFlow = MarkingFlow(session = session.invalidate())

    /** Retake: the old result and its export eligibility are dropped before any new capture. */
    fun retake(): MarkingFlow = invalidated()

    /** Reference dimensions or display unit changed: marks and selection survive. */
    fun settingsChanged(): MarkingFlow = invalidated()

    /**
     * The user picked [selected]. A real change routes through the shared invalidation, so
     * the previous result can no longer be shown or exported; marks are kept.
     */
    fun orientationSelected(selected: SurfaceOrientation): MarkingFlow =
        if (selected == orientation) this else invalidated().copy(orientation = selected)

    /** Stores the user's raw placements; an actual edit invalidates the current result. */
    fun marksChanged(corners: List<Vec2>, stick: List<Vec2>): MarkingFlow =
        if (corners == this.corners && stick == this.stick) this
        else invalidated().copy(corners = corners.toList(), stick = stick.toList())

    /**
     * Measure button (production path): raw marks, the scene's aligned gravity and calibration
     * and the validated [reference] go through [MeasurementAttempt] for the current revision
     * and selection. Expected failures come back as outcomes and stay in the marking flow.
     */
    fun attempt(
        corners: List<Vec2>,
        stick: List<Vec2>,
        scene: CapturedScene,
        reference: ReferenceCheck,
        unit: LengthUnit,
        engine: OutcomeEngine = MeasurementAttempt.engine,
    ): MarkingFlow {
        val prepared = MeasurementAttempt.prepare(corners, stick, scene, reference, orientation, session.revision)
        return completed(session.revision, orientation, MeasurementAttempt.run(prepared, engine), unit)
    }

    /** Records a typed completion for [request]; stale revisions or selections are ignored. */
    fun completed(request: OutcomeRequest, outcome: MeasurementOutcome, unit: LengthUnit): MarkingFlow =
        completed(request.revision, request.orientation, outcome, unit)

    private fun completed(
        revision: Int,
        selection: SurfaceOrientation,
        outcome: MeasurementOutcome,
        unit: LengthUnit,
    ): MarkingFlow =
        if (revision != session.revision || selection != orientation) this
        else copy(
            session = session.complete(revision, outcome),
            result = BoundView(MeasurementPresenter.present(outcome, unit), revision, selection),
        )

    /** Legacy engine request carrying the currently visible selection and the current revision. */
    fun request(
        orderedCorners: List<Vec2>,
        stick: List<Vec2>,
        intrinsics: CameraIntrinsics,
        gravity: Vec3,
        profile: StickProfile,
    ): MeasurementRequest =
        MeasurementRequest(orderedCorners, stick, intrinsics, gravity, profile, orientation, session.revision)

    /**
     * Records a legacy [view] for [request]. A completion built for another revision or
     * selection is stale and ignored, so it can never replace or restore the current result.
     */
    fun completed(request: MeasurementRequest, view: MeasurementView): MarkingFlow =
        if (request.revision != session.revision || request.orientation != orientation) this
        else copy(result = BoundView(view, request.revision, request.orientation))

    /** Legacy measure over [MeasurementEngine]; production uses [attempt]. */
    fun measure(
        orderedCorners: List<Vec2>,
        stick: List<Vec2>,
        intrinsics: CameraIntrinsics,
        gravity: Vec3,
        profile: StickProfile,
        unit: LengthUnit,
        engine: MeasurementEngine = MeasurementPresenter.hybridEngine,
    ): MarkingFlow {
        val request = request(orderedCorners, stick, intrinsics, gravity, profile)
        return completed(request, MeasurementPresenter.present(request, unit, engine))
    }
}
