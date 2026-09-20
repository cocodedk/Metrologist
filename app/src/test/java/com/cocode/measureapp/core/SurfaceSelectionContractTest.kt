package com.cocode.measureapp.core

import com.cocode.measureapp.geometry.CameraIntrinsics
import com.cocode.measureapp.geometry.EngineResult
import com.cocode.measureapp.geometry.SceneRotations
import com.cocode.measureapp.geometry.SurfaceOrientation
import com.cocode.measureapp.geometry.SurfaceOrientation.HORIZONTAL
import com.cocode.measureapp.geometry.SurfaceOrientation.VERTICAL
import com.cocode.measureapp.geometry.SyntheticScene
import com.cocode.measureapp.geometry.Vec3
import com.cocode.measureapp.ui.surface.MarkingFlow
import com.cocode.measureapp.ui.surface.surfaceLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * C05 explicit surface / C15 result invalidation, driven through [MarkingFlow] — the state
 * holder whose transitions `MeasureApp` wires to the MarkScreen selector, Measure button and
 * Results -> Re-mark callbacks. The engine is the real hybrid engine behind a recorder.
 *
 * Not covered here: the on-device Compose tap on the selector (no Compose UI test harness in
 * the JVM test set) and physical-camera gravity.
 */
class SurfaceSelectionContractTest {
    private val k = CameraIntrinsics(fx = 1500.0, fy = 1500.0, cx = 960.0, cy = 540.0)

    /** 2 x 1 m floor seen by a camera looking straight down; world down = camera +z. */
    private val floor = SyntheticScene(
        w = 2.0, h = 1.0, r = SceneRotations.yaw(0.0), t = Vec3(0.0, 0.0, 2.5), k = k, l = 1.0,
    )
    private val floorGravity = Vec3(0.0, 0.0, 1.0)

    private val requests = mutableListOf<MeasurementRequest>()
    private val results = mutableListOf<EngineResult>()
    private val engine: MeasurementEngine = { r ->
        requests += r
        MeasurementPresenter.hybridEngine(r).also { results += it }
    }

    /** Measure button: marks are saved for re-mark, then the ordered marks are measured. */
    private fun MarkingFlow.pressMeasure(): MarkingFlow =
        marksChanged(floor.cornerPixels, floor.stickPixels).measure(
            floor.cornerPixels, floor.stickPixels, k, floorGravity, floor.profile, LengthUnit.METERS, engine,
        )

    @Test
    fun C05_newCaptureShowsWallAsTheVisibleSelection() {
        val flow = MarkingFlow().captured()
        assertEquals(VERTICAL, flow.orientation)
        assertEquals("Wall", surfaceLabel(flow.orientation))
        assertEquals("Floor", surfaceLabel(HORIZONTAL))
        flow.pressMeasure()
        assertEquals(VERTICAL, requests.single().orientation)
    }

    @Test
    fun C05_floorSelectionReachesEngineAsHorizontalAndRecoversFloor() {
        val flow = MarkingFlow().captured().orientationSelected(HORIZONTAL).pressMeasure()
        assertEquals(HORIZONTAL, requests.single().orientation)
        assertEquals(flow.session.revision, requests.single().revision)
        val view = flow.currentView
        assertNotNull(view)
        assertTrue(view!!.usable)
        assertEquals("2.00 m", view.width)
        assertEquals("1.00 m", view.height)
        assertEquals(2.0, results.single().measurement.width, 1e-4)
    }

    @Test
    fun C05_wallSelectionReachesEngineAsVertical() {
        MarkingFlow().captured().orientationSelected(HORIZONTAL).orientationSelected(VERTICAL).pressMeasure()
        assertEquals(VERTICAL, requests.single().orientation)
    }

    @Test
    fun C05_remarkKeepsSelectionMarksAndResult() {
        val measured = MarkingFlow().captured().orientationSelected(HORIZONTAL).pressMeasure()
        // Results -> Re-mark only navigates; MarkScreen is re-seeded from this same state.
        val remark = measured
        assertEquals(HORIZONTAL, remark.orientation)
        assertEquals(floor.cornerPixels, remark.corners)
        assertEquals(floor.stickPixels, remark.stick)
        assertSame(measured.currentView, remark.currentView)
        remark.pressMeasure()
        assertEquals(listOf(HORIZONTAL, HORIZONTAL), requests.map { it.orientation })
    }

    @Test
    fun C15_changingSelectionInvalidatesAndRecomputesForNewValue() {
        val floorResult = MarkingFlow().captured().orientationSelected(HORIZONTAL).pressMeasure()
        val floorRequest = requests.single()
        val floorView = floorResult.currentView!!
        assertTrue(floorView.usable)

        val wall = floorResult.orientationSelected(VERTICAL)
        assertEquals(floorResult.session.revision + 1, wall.session.revision)
        assertNull("old floor result must not be shown or exported", wall.currentView)
        assertEquals("marks preserved", floorResult.corners, wall.corners)
        assertEquals("marks preserved", floorResult.stick, wall.stick)
        // A late completion for the floor request cannot restore the stale result.
        assertSame(wall, wall.completed(floorRequest, floorView))

        val recomputed = wall.pressMeasure()
        val wallRequest = requests.last()
        assertEquals(VERTICAL, wallRequest.orientation)
        assertEquals(wall.session.revision, wallRequest.revision)
        assertEquals(2, requests.size)
        // A straight-down floor has no usable wall plane: the floor numbers are not carried over.
        assertFalse(recomputed.currentView!!.usable)
    }

    @Test
    fun C15_reselectingCurrentSurfaceDoesNotInvalidate() {
        val measured = MarkingFlow().captured().orientationSelected(HORIZONTAL).pressMeasure()
        assertSame(measured, measured.orientationSelected(HORIZONTAL))
    }

    @Test
    fun C15_switchingBackRequiresFreshMeasurement() {
        val measured = MarkingFlow().captured().orientationSelected(HORIZONTAL).pressMeasure()
        val back = measured.orientationSelected(VERTICAL).orientationSelected(HORIZONTAL)
        assertEquals(measured.session.revision + 2, back.session.revision)
        assertNull(back.currentView)
        back.pressMeasure()
        assertEquals(back.session.revision, requests.last().revision)
    }

    @Test
    fun C15_newCaptureClearsMarksResultAndResetsVisibleSelection() {
        val measured = MarkingFlow().captured().orientationSelected(HORIZONTAL).pressMeasure()
        val next = measured.captured()
        assertEquals(measured.session.revision + 1, next.session.revision)
        assertNull(next.currentView)
        assertNull(next.corners)
        assertNull(next.stick)
        assertEquals(VERTICAL, next.orientation)
    }

    @Test
    fun C05_presenterPassesRequestOrientationToEngine() {
        fun request(o: SurfaceOrientation) = MeasurementRequest(
            floor.cornerPixels, floor.stickPixels, k, floorGravity, floor.profile, o, revision = 7,
        )
        assertTrue(MeasurementPresenter.present(request(HORIZONTAL), LengthUnit.METERS, engine).usable)
        assertFalse(MeasurementPresenter.present(request(VERTICAL), LengthUnit.METERS, engine).usable)
        assertEquals(listOf(request(HORIZONTAL), request(VERTICAL)), requests)
    }
}
