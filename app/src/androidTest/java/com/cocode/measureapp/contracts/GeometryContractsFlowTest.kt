package com.cocode.measureapp.contracts

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cocode.measureapp.core.dimensions.ReferenceCheck
import com.cocode.measureapp.core.validation.MarkerValidation
import com.cocode.measureapp.core.validation.MarkerValidator
import com.cocode.measureapp.geometry.MeasurementFailureReason as Reason
import com.cocode.measureapp.geometry.MeasurementResult
import com.cocode.measureapp.geometry.MetrologyEngine
import com.cocode.measureapp.geometry.ProfileValidation
import com.cocode.measureapp.geometry.RectangleSolver
import com.cocode.measureapp.geometry.SolverKind
import com.cocode.measureapp.geometry.StickProfile
import com.cocode.measureapp.geometry.SurfaceOrientation
import com.cocode.measureapp.geometry.Vec2
import com.cocode.measureapp.geometry.eligibility.PlaneAssumption
import com.cocode.measureapp.geometry.frames.AlignedGravity
import com.cocode.measureapp.geometry.frames.CalibrationProvenance
import com.cocode.measureapp.geometry.rectangle.RectangleCandidate
import com.cocode.measureapp.geometry.rectangle.VanishingDirection
import com.cocode.measureapp.stick.StickBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Object quads (C06), plane candidates (C07), metric profiles (C12) and stick boxes (C13). */
@RunWith(AndroidJUnit4::class)
class GeometryContractsFlowTest {
    @get:Rule val rule = createComposeRule()

    private val f = TargetFixture.frontal
    private val wallProfile = ReferenceCheck.Valid(0.5, 0.05)

    /** A trusted synthetic scene of [placement] on a 1280 x 960 image, loaded with its marks. */
    private fun FlowDriver.loadSynthetic(placement: Placement, target: Planar, stick: List<Vec2>? = null): List<Vec2> {
        val k = Targets.K_WALL
        val img = Scenes.image(Scenes.blank(Targets.W, Targets.H), k, AlignedGravity.Available(placement.down, 0L),
            CalibrationProvenance.CALIBRATED)
        val obj = placement.pixels(target.obj, k)
        load(img, obj, stick ?: placement.pixels(target.stick, k))
        return obj
    }

    private fun FlowDriver.measured(): MeasurementResult {
        val ok = Expect.success(measure())
        assertResults()
        clickResults("Re-mark")
        return ok.measurement
    }

    @Test fun C06_onlyValidObjectQuadsReachTheSolverInCanonicalOrder() {
        val d = FlowDriver(rule, FlowState(f.reference)).start()
        for (target in listOf(TargetFixture.frontal, TargetFixture.nonrectangle)) {
            val c = target.objectPixels
            val shuffled = listOf(c[2], c[0], c[3], c[1])
            assertEquals(c, (MarkerValidator.validateObject(shuffled) as MarkerValidation.Accepted).corners)
            d.load(target.image(), shuffled, target.stickPixels)
            Expect.sameResult(target.expected, d.measured(), what = target.name)
            assertEquals("the user's placement is kept as marked", shuffled, d.s.requests.last().corners)
        }
        val c = f.objectPixels
        val invalid = mapOf(
            "duplicate" to listOf(c[0], c[0], c[2], c[3]),
            "collinear" to listOf(Vec2(100.0, 100.0), Vec2(300.0, 100.0), Vec2(500.0, 100.0), Vec2(700.0, 100.0)),
            "concave" to listOf(Vec2(100.0, 100.0), Vec2(600.0, 300.0), Vec2(100.0, 500.0), Vec2(250.0, 300.0)),
        )
        d.load(f.image(), c, f.stickPixels)
        invalid.forEach { (name, quad) ->
            d.ui { place(quad, f.stickPixels) }
            val fail = Expect.failure(d.measure(), Reason.INVALID_OBJECT_CORNERS)
            d.assertMarking()
            d.assertShown(fail.detail!!)
            assertTrue("$name must not reach scale recovery", d.s.flow.usableView == null)
        }
    }

    @Test fun C07_frontalYawAndPitchRectanglesSelectAnEligibleRectangle() {
        val d = FlowDriver(rule, FlowState(wallProfile)).start()
        val views = mapOf(
            "frontal" to Targets.WALL,
            "yaw" to Targets.WALL.copy(e1 = Rot.yaw(30.0)(Targets.WALL.e1)),
            "pitch" to Targets.WALL.moved(Rot.pitch(12.0)),
        )
        views.forEach { (name, placement) ->
            val obj = d.loadSynthetic(placement, Targets.WALL_RECT)
            val ev = (RectangleSolver.candidate(obj, Targets.K_WALL) as RectangleCandidate.Accepted).evidence
            val atInfinity = listOf(ev.widthVanishing, ev.heightVanishing).count { it is VanishingDirection.AtInfinity }
            assertEquals("$name vanishing points at infinity", if (name == "frontal") 2 else 1, atInfinity)
            val ok = Expect.success(d.measure())
            assertEquals(SolverKind.RECTANGLE, ok.solver)
            assertEquals(PlaneAssumption.RECTANGLE_TARGET, ok.diagnostics!!.assumption)
            Expect.sameShape(Targets.WALL_RECT.oracle, ok.measurement, 1e-6, name)
            d.assertResults()
            d.clickResults("Re-mark")
        }
        // Nearly edge-on (88 degrees of yaw) the edge directions collapse: rejected, never revived.
        val grazing = Targets.WALL.copy(e1 = Rot.yaw(88.0)(Targets.WALL.e1))
        val obj = d.loadSynthetic(grazing, Targets.WALL_RECT)
        assertTrue(RectangleSolver.candidate(obj, Targets.K_WALL) is RectangleCandidate.Rejected)
        Expect.failure(d.measure(), Reason.UNSUPPORTED_GEOMETRY)
        d.assertMarking()
        d.assertNoUsableResult()
    }

    @Test fun C12_equivalentProfilesInAnyUnitMeasureAlikeAndInvalidEngineProfilesAreRejected() {
        val d = FlowDriver(rule, FlowState(ReferenceCheck.Valid(1.0, 0.04))).start()
        d.load(f.image(), f.objectPixels, f.stickPixels)
        val entries = listOf(
            Triple(0, "0.1", "0.02"), // metres
            Triple(1, "10", "2"), // centimetres
            Triple(2, "0.328083989501", "0.0656167979003"), // decimal feet
        )
        val results = entries.map { (unit, length, width) ->
            d.click("Settings")
            d.enterReference(unit, length, width)
            d.back()
            val ok = Expect.success(d.measure())
            Expect.near(0.1, d.s.requests.last().profile.totalLength, 1e-9, "profile length")
            Expect.near(0.02, d.s.requests.last().profile.width, 1e-9, "profile width")
            d.clickResults("Re-mark")
            ok.measurement
        }
        results.forEach { Expect.sameResult(f.expected, it, 1e-6, "unit-independent") }

        for (bad in listOf(Double.NaN, -0.02, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            val v = StickProfile.validated(0.1, width = bad)
            assertTrue("width $bad", v is ProfileValidation.Rejected)
            assertEquals(Reason.INVALID_REFERENCE_DIMENSIONS, (v as ProfileValidation.Rejected).failure.reason)
        }
        for (bad in listOf(0.0, -0.1, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertTrue("length $bad", StickProfile.validated(bad, width = 0.02) is ProfileValidation.Rejected)
        }
        val lengthOnly = MetrologyEngine.evaluate(
            f.objectPixels, f.stickPixels, f.intrinsics, CalibrationProvenance.CALIBRATED,
            AlignedGravity.Available(f.down, 0L), StickProfile(0.1, width = 0.0), SurfaceOrientation.VERTICAL, 1,
        )
        val ok = Expect.success(lengthOnly)
        Expect.sameResult(f.expected, ok.measurement)
        assertFalse(ok.diagnostics!!.referenceChecked!!)
    }

    @Test fun C13_stickBoxesInBothWindingsGiveEqualScaleAndInvalidBoxesStopBeforeScale() {
        val d = FlowDriver(rule, FlowState(f.reference)).start()
        fun reversed(b: List<Vec2>) = listOf(b[0], b[3], b[2], b[1])
        val horizontal = f.stickPixels
        val vertical = listOf(Vec2(600.0, 100.0), Vec2(660.0, 100.0), Vec2(660.0, 400.0), Vec2(600.0, 400.0))
        val detected = StickBox.fromDetectedEnds(Vec2(231.0, 358.5), Vec2(531.0, 358.5), halfWidth = 30.0)
        for (box in listOf(horizontal, vertical, detected)) {
            val pair = listOf(box, reversed(box)).map { b ->
                d.load(f.image(), f.objectPixels, b)
                d.measured()
            }
            Expect.sameResult(pair[0], pair[1], 1e-9, "winding")
            Expect.sameResult(f.expected, pair[0], 1e-6, "stick box $box")
        }

        d.ui { storeReference(wallProfile) } // perspective: a stick on the tilted floor
        val floorStick = Targets.TILTED_FLOOR.pixels(Targets.FLOOR_RECT.stick, Targets.K_WALL)
        val perspective = listOf(floorStick, reversed(floorStick)).map { b ->
            d.loadSynthetic(Targets.TILTED_FLOOR, Targets.FLOOR_RECT, b)
            d.select(SurfaceOrientation.HORIZONTAL)
            d.measured()
        }
        Expect.sameResult(perspective[0], perspective[1], 1e-9, "perspective winding")
        Expect.sameShape(Targets.FLOOR_RECT.oracle, perspective[0], 1e-6, "perspective")

        d.ui { storeReference(f.reference) }
        d.load(f.image(), f.objectPixels, horizontal)
        val s = horizontal
        for (bad in listOf(listOf(s[0], s[1], s[1], s[0]), listOf(s[0], s[1], s[3], s[2]))) {
            d.ui { place(f.objectPixels, bad) }
            val fail = Expect.failure(d.measure(), Reason.INVALID_STICK_CORNERS)
            d.assertShown(fail.detail!!)
            d.assertMarking()
            d.assertNoUsableResult()
        }
    }
}
