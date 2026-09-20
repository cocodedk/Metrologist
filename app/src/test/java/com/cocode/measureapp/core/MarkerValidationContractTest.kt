package com.cocode.measureapp.core

import com.cocode.measureapp.core.validation.MarkValidation
import com.cocode.measureapp.core.validation.MarkerRule
import com.cocode.measureapp.core.validation.MarkerTarget
import com.cocode.measureapp.core.validation.MarkerValidation
import com.cocode.measureapp.core.validation.MarkerValidator
import com.cocode.measureapp.core.validation.Winding
import com.cocode.measureapp.geometry.CameraIntrinsics
import com.cocode.measureapp.geometry.MeasurementFailureReason
import com.cocode.measureapp.geometry.Measurements
import com.cocode.measureapp.geometry.MetrologyEngine
import com.cocode.measureapp.geometry.SceneRotations
import com.cocode.measureapp.geometry.StickProfile
import com.cocode.measureapp.geometry.SurfaceOrientation
import com.cocode.measureapp.geometry.SyntheticScene
import com.cocode.measureapp.geometry.Vec2
import com.cocode.measureapp.geometry.Vec3
import com.cocode.measureapp.stick.StickBox
import com.cocode.measureapp.stick.StickScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Contracts C06 (valid object quad), C08 (validation outcome) and C13 (valid stick quad). */
class MarkerValidationContractTest {
    private fun q(vararg c: Double) = c.toList().chunked(2).map { Vec2(it[0], it[1]) }
    private fun obj(p: List<Vec2>) = MarkerValidator.validateObject(p)
    private fun stick(p: List<Vec2>) = MarkerValidator.validateStick(p)
    private fun rule(v: MarkerValidation) = (v as MarkerValidation.Rejected).rule
    private fun accepted(v: MarkerValidation) = (v as MarkerValidation.Accepted).corners
    private fun assertRel(e: Double, a: Double) = assertEquals(e, a, abs(e) * 1e-9)
    private fun shifts(p: List<Vec2>) = (0 until 4).map { s -> List(4) { p[(it + s) % 4] } }
    private fun <T> perms(items: List<T>): List<List<T>> = if (items.size <= 1) listOf(items) else
        items.indices.flatMap { i -> perms(items.filterIndexed { j, _ -> j != i }).map { listOf(items[i]) + it } }

    private val collinear = q(800.0, 600.0, 900.0, 600.0, 1000.0, 600.0, 1100.0, 600.0)
    private val detectorBox = q(100.0, 110.0, 300.0, 110.0, 300.0, 90.0, 100.0, 90.0)
    private val k = CameraIntrinsics(fx = 1500.0, fy = 1500.0, cx = 960.0, cy = 540.0)

    @Test fun collinearObjectMarksAreRejectedEverywhere() {
        assertEquals(MarkerRule.COLLINEAR_VERTICES, rule(obj(collinear)))
        assertThrows(IllegalArgumentException::class.java) { CornerOrdering.order(collinear) }
        // Even a non-UI caller cannot turn them into a zero-area measurement.
        assertThrows(IllegalArgumentException::class.java) { Measurements.compute(collinear) }
    }

    @Test fun degenerateObjectInputsFailTheirNamedRule() {
        val cases = mapOf(
            q(0.0, 0.0, 0.0, 0.0, 10.0, 4.0, 0.0, 4.0) to MarkerRule.COINCIDENT_VERTICES,
            q(0.0, 0.0, 500.0, 0.0, 1000.0, 0.0, 500.0, 600.0) to MarkerRule.COLLINEAR_VERTICES,
            q(0.0, 0.0, 1000.0, 0.0, 500.0, 600.0, 500.0, 50.0) to MarkerRule.CONCAVE,
            q(Double.NaN, 0.0, 10.0, 0.0, 10.0, 4.0, 0.0, 4.0) to MarkerRule.NON_FINITE,
            q(0.0, 0.0, Double.POSITIVE_INFINITY, 0.0, 10.0, 4.0, 0.0, 4.0) to MarkerRule.NON_FINITE,
            q(0.0, 0.0, 10.0, 0.0, 10.0, 4.0) to MarkerRule.POINT_COUNT,
        )
        for ((points, expected) in cases) {
            for (p in if (points.size == 4) perms(points) else listOf(points)) {
                val v = obj(p)
                assertEquals("$p", expected, rule(v))
                assertEquals(MarkerTarget.OBJECT, v.target)
            }
        }
    }

    @Test fun C06_validQuadsReachTheSolverInCanonicalClockwiseOrder() {
        val valid = listOf(
            q(0.0, 0.0, 10.0, 0.0, 10.0, 10.0, 0.0, 10.0),              // square
            q(5.0, 0.0, 10.0, 5.0, 5.0, 10.0, 0.0, 5.0),                // diamond
            q(0.0, 0.0, 2000.0, 0.0, 2000.0, 4.0, 0.0, 4.0),            // thin but resolved
            q(3.0, 1.0, 7.0, 1.0, 12.0, 8.0, 1.0, 6.0),                 // convex non-rectangle
        )
        for (canonical in valid) for (p in perms(canonical)) {
            val v = obj(p) as MarkerValidation.Accepted
            assertEquals(canonical, v.corners)
            assertEquals(Winding.CLOCKWISE, v.winding)
        }
        // A shuffled perspective scene is ordered back and measures exactly like the original.
        val scene = SyntheticScene(3.0, 2.0, SceneRotations.yawPitch(25.0, 20.0), Vec3(0.0, 0.0, 6.0), k, 1.0)
        val shuffled = listOf(2, 0, 3, 1).map { scene.cornerPixels[it] }
        assertEquals(scene.cornerPixels, CornerOrdering.order(shuffled))
        val a = measure(scene, CornerOrdering.order(shuffled), scene.stickPixels, scene.gravityCam)
        val b = measure(scene, scene.cornerPixels, scene.stickPixels, scene.gravityCam)
        assertEquals(b.measurement, a.measurement)
    }

    @Test fun toleranceBoundariesAreDocumentedAndBothSidesHold() {
        // Separation: 1e-3 of the diameter (~1000 px) -> ~1 px wide box.
        assertTrue(obj(q(0.0, 0.0, 1000.0, 0.0, 1000.0, 1.01, 0.0, 1.01)) is MarkerValidation.Accepted)
        assertEquals(MarkerRule.COINCIDENT_VERTICES, rule(obj(q(0.0, 0.0, 1000.0, 0.0, 1000.0, 0.99, 0.0, 0.99))))
        // Turn: sin at the apex is e/250, the threshold 1e-3 sits at e = 0.25 px.
        assertTrue(obj(apex(0.5)) is MarkerValidation.Accepted)
        assertEquals(MarkerRule.COLLINEAR_VERTICES, rule(obj(apex(0.125))))
        // Engine area guard: diameter 1000 -> threshold 1e-4 * 1000^2 = 100 px^2.
        assertEquals(200.0, Measurements.compute(q(0.0, 0.0, 1000.0, 0.0, 1000.0, 0.2, 0.0, 0.2)).area, 1e-6)
        assertThrows(IllegalArgumentException::class.java) {
            Measurements.compute(q(0.0, 0.0, 1000.0, 0.0, 1000.0, 0.05, 0.0, 0.05))
        }
    }

    @Test fun displayZoomAndResolutionDoNotChangeOutcomes() {
        val marks = listOf(apex(0.5), apex(0.125), collinear, detectorBox, q(0.0, 0.0, 1000.0, 0.0, 1000.0, 0.99, 0.0, 0.99))
        for (image in marks) {
            val base = obj(image)
            for (zoom in listOf(0.25, 1.0, 3.7, 6.0)) {
                // Screen taps -> image coordinates, exactly as the marking view maps drags.
                val screen = image.map { Vec2(it.x * zoom + 40.0, it.y * zoom - 15.0) }
                val back = screen.map { Vec2((it.x - 40.0) / zoom, (it.y + 15.0) / zoom) }
                assertEquals(base::class, obj(back)::class)
                if (base is MarkerValidation.Rejected) assertEquals(base.rule, rule(obj(back)))
                // A uniformly scaled (higher resolution) image keeps the outcome too.
                assertEquals(base::class, obj(image.map { it * zoom })::class)
            }
        }
    }

    /** Convex quad whose top vertex sits [e] px above the TL-TR line (collinear at e = 0). */
    private fun apex(e: Double) = q(0.0, 0.0, 500.0, -e, 1000.0, 0.0, 500.0, 600.0)

    @Test fun C13_detectorStickBoxIsAcceptedInBothWindingsWithIdenticalScale() {
        // Actual auto-detection handoff: detected ends -> framed box.
        val handoff = StickBox.fromDetectedEnds(Vec2(100.0, 100.0), Vec2(300.0, 100.0), halfWidth = 10.0)
        assertEquals(detectorBox, handoff)
        val vertical = detectorBox.map { Vec2(it.y, it.x) }
        val profile = StickProfile(totalLength = 1.0, width = 0.1)
        for (box in listOf(handoff, vertical)) {
            val expected = StickScale.solve(box, profile)
            for (variant in shifts(box) + shifts(box.reversed())) {
                val v = stick(variant) as MarkerValidation.Accepted
                assertEquals(variant, v.corners) // order and opposite edges preserved
                val s = StickScale.solve(StickBox.requireValid(variant), profile)
                assertRel(expected.scale, s.scale)
                assertRel(expected.agreement, s.agreement)
            }
        }
        assertEquals(Winding.COUNTER_CLOCKWISE, (stick(handoff) as MarkerValidation.Accepted).winding)
        assertEquals(Winding.CLOCKWISE, (stick(handoff.reversed()) as MarkerValidation.Accepted).winding)
    }

    @Test fun C13_perspectiveStickMeasuresIdenticallyInEveryWinding() {
        val poses = listOf(SceneRotations.yawPitch(25.0, 20.0) to null, SceneRotations.yaw(1.5) to Vec3(0.0, 1.0, 0.0))
        for ((r, level) in poses) {
            val scene = SyntheticScene(3.0, 2.0, r, Vec3(0.0, 0.0, 6.0), k, 1.0)
            val g = level ?: scene.gravityCam
            val base = measure(scene, scene.cornerPixels, scene.stickPixels, g)
            assertTrue(base.measurement.width > 0.0)
            for (variant in shifts(scene.stickPixels) + shifts(scene.stickPixels.reversed())) {
                val m = measure(scene, scene.cornerPixels, StickBox.requireValid(variant), g).measurement
                assertRel(base.measurement.width, m.width)
                assertRel(base.measurement.height, m.height)
                assertRel(base.measurement.area, m.area)
            }
        }
    }

    @Test fun C13_crossedOrCollapsedStickBoxesAreRejectedBeforeDivision() {
        val crossed = listOf(detectorBox[0], detectorBox[2], detectorBox[1], detectorBox[3])
        assertEquals(MarkerRule.SELF_CROSSING, rule(stick(crossed)))
        val collapsed = q(100.0, 100.0, 300.0, 100.0, 300.0, 100.0, 100.0, 100.0)
        assertEquals(MarkerRule.COINCIDENT_VERTICES, rule(stick(collapsed)))
        assertEquals(MarkerRule.COLLINEAR_VERTICES, rule(stick(q(100.0, 100.0, 200.0, 100.0, 300.0, 100.0, 400.0, 100.0))))
        for (bad in listOf(crossed, collapsed)) {
            assertThrows(IllegalArgumentException::class.java) { StickBox.requireValid(bad) }
        }
        // Length-only scale stays a separate low-level capability; zero width does not repair marks.
        assertEquals(0.005, StickScale.solve(collapsed, StickProfile(totalLength = 1.0)).scale, 1e-12)
    }

    @Test fun C08_objectAndStickRejectionsAreDistinctActionableFailures() {
        val objFail = MarkerValidator.validateMarks(collinear, detectorBox) as MarkValidation.Rejected
        val collapsed = q(100.0, 100.0, 300.0, 100.0, 300.0, 100.0, 100.0, 100.0)
        val good = q(0.0, 0.0, 10.0, 0.0, 10.0, 4.0, 0.0, 4.0)
        val stickFail = MarkerValidator.validateMarks(good.reversed(), collapsed) as MarkValidation.Rejected
        assertEquals(MarkerTarget.OBJECT, objFail.rejection.target)
        assertEquals(MarkerTarget.STICK, stickFail.rejection.target)
        assertNotEquals(objFail.rejection.message, stickFail.rejection.message)
        assertEquals(MeasurementFailureReason.INVALID_OBJECT_CORNERS, objFail.rejection.toFailure().reason)
        assertEquals(MeasurementFailureReason.INVALID_STICK_CORNERS, stickFail.rejection.toFailure().reason)
        assertTrue(!objFail.rejection.toFailure().usable)
        // Marks are never rewritten by a rejection; fixing them permits a new attempt.
        assertEquals(q(800.0, 600.0, 900.0, 600.0, 1000.0, 600.0, 1100.0, 600.0), collinear)
        val fixed = MarkerValidator.validateMarks(good.reversed(), detectorBox) as MarkValidation.Accepted
        assertEquals(good, fixed.objectCorners)
        assertEquals(detectorBox, fixed.stickBox)
    }

    private fun measure(s: SyntheticScene, corners: List<Vec2>, stickBox: List<Vec2>, g: Vec3) =
        MetrologyEngine.measureHybrid(corners, stickBox, s.k, s.profile, g, SurfaceOrientation.VERTICAL)
}
