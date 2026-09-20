package com.cocode.measureapp.geometry.eligibility

import com.cocode.measureapp.geometry.CameraIntrinsics
import com.cocode.measureapp.geometry.ContractScenes
import com.cocode.measureapp.geometry.MeasurementOutcome
import com.cocode.measureapp.geometry.MeasurementResult
import com.cocode.measureapp.geometry.RectangleSolver
import com.cocode.measureapp.geometry.StickProfile
import com.cocode.measureapp.geometry.SurfaceOrientation
import com.cocode.measureapp.geometry.SyntheticScene
import com.cocode.measureapp.geometry.Vec2
import com.cocode.measureapp.geometry.Vec3
import com.cocode.measureapp.geometry.frames.AlignedGravity
import com.cocode.measureapp.geometry.frames.CalibrationProvenance
import com.cocode.measureapp.geometry.frames.GravityAlignmentReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Node 03 fixtures. Pixels come from an independent pinhole model (`K * X / z`), never from
 * engine projection code, and truth is computed here with plain trigonometry.
 */
object EligibilityFixtures {
    val K: CameraIntrinsics = ContractScenes.K
    const val REVISION = 7

    /** The 111.8-degree nonrectangle of the acceptance check, in plane metres. */
    val nonRectWorld = listOf(Vec2(-1.0, -0.5), Vec2(1.0, -0.5), Vec2(1.4, 0.5), Vec2(-1.0, 0.6))

    /** Centred 1 x 0.04 m reference box. */
    val stickWorld = listOf(Vec2(-0.5, -0.02), Vec2(0.5, -0.02), Vec2(0.5, 0.02), Vec2(-0.5, 0.02))
    val stickProfile = StickProfile(totalLength = 1.0, bandCount = 4, width = 0.04)

    /** Plane point `(x, y)` seen head-on at [depth] m: pixel `K * (x, y, depth) / depth`. */
    fun headOn(p: Vec2, depth: Double = 4.0) = Vec2(K.fx * p.x / depth + K.cx, K.fy * p.y / depth + K.cy)

    val nonRectCorners = nonRectWorld.map { headOn(it) }
    val nonRectStick = stickWorld.map { headOn(it) }

    /** Level camera facing a wall: physical down is camera +y. */
    val LEVEL = Vec3(0.0, 1.0, 0.0)

    /** Camera looking straight down at a floor/table: physical down is camera +z. */
    val STRAIGHT_DOWN = Vec3(0.0, 0.0, 1.0)

    val MISSING: AlignedGravity = AlignedGravity.Unavailable(GravityAlignmentReason.STALE_SAMPLE)

    fun available(g: Vec3): AlignedGravity = AlignedGravity.Available(g, 0L)

    fun input(
        corners: List<Vec2>,
        stick: List<Vec2>,
        gravity: AlignedGravity,
        orientation: SurfaceOrientation,
        k: CameraIntrinsics = K,
        calibration: CalibrationProvenance = CalibrationProvenance.CALIBRATED,
        profile: StickProfile = stickProfile,
    ) = EligibilityInput(corners, stick, k, calibration, gravity, profile, orientation)

    fun scene(
        s: SyntheticScene,
        gravity: AlignedGravity,
        orientation: SurfaceOrientation = SurfaceOrientation.VERTICAL,
        corners: List<Vec2> = s.cornerPixels,
        k: CameraIntrinsics = s.k,
        calibration: CalibrationProvenance = CalibrationProvenance.CALIBRATED,
        profile: StickProfile = s.profile,
    ) = input(corners, s.stickPixels, gravity, orientation, k, calibration, profile)

    fun evaluate(i: EligibilityInput): MeasurementOutcome = OutcomeProducer.evaluate(i, REVISION).outcome

    fun rectangle(i: EligibilityInput): Assessment =
        RectangleEligibility.assess(RectangleSolver.candidate(i.corners, i.intrinsics, i.calibration), i)

    /** [g] turned by [deg] toward the unit vector [toward], which must be orthogonal to [g]. */
    fun turn(g: Vec3, toward: Vec3, deg: Double): Vec3 {
        val a = Math.toRadians(deg)
        return g * cos(a) + toward * sin(a)
    }

    /** Independent truth: mean opposite edges, shoelace area, interior angles via atan2. */
    fun truth(p: List<Vec2>): MeasurementResult {
        fun d(a: Vec2, b: Vec2) = hypot(a.x - b.x, a.y - b.y)
        var twiceArea = 0.0
        for (i in 0..3) twiceArea += p[i].x * p[(i + 1) % 4].y - p[(i + 1) % 4].x * p[i].y
        val angles = (0..3).map { i ->
            val u = p[(i + 3) % 4] - p[i]
            val v = p[(i + 1) % 4] - p[i]
            Math.toDegrees(atan2(abs(u.x * v.y - u.y * v.x), u.x * v.x + u.y * v.y))
        }
        return MeasurementResult(
            (d(p[0], p[1]) + d(p[3], p[2])) / 2.0, (d(p[0], p[3]) + d(p[1], p[2])) / 2.0,
            abs(twiceArea) / 2.0, (d(p[0], p[2]) + d(p[1], p[3])) / 2.0, angles,
        )
    }

    fun assertMatches(label: String, truth: MeasurementResult, m: MeasurementResult, rel: Double, deg: Double) {
        assertEquals("$label width", truth.width, m.width, truth.width * rel)
        assertEquals("$label height", truth.height, m.height, truth.height * rel)
        assertEquals("$label area", truth.area, m.area, truth.area * rel)
        assertEquals("$label diagonal", truth.diagonal, m.diagonal, truth.diagonal * rel)
        for (i in 0..3) assertEquals("$label angle $i", truth.cornerAngles[i], m.cornerAngles[i], deg)
    }

    /** A success with bounded confidence and visible provenance for the fixture revision. */
    fun success(o: MeasurementOutcome, label: String): MeasurementOutcome.Success {
        val s = o as? MeasurementOutcome.Success ?: throw AssertionError("$label: expected success, got $o")
        assertTrue("$label confidence ${s.confidence}", s.confidence.isFinite() && s.confidence > 0.0 && s.confidence <= 1.0)
        val d = s.diagnostics ?: throw AssertionError("$label: diagnostics missing")
        assertNotNull("$label calibration", d.calibration)
        assertNotNull("$label surface", d.surfaceConsistency)
        assertNotNull("$label assumption", d.assumption)
        assertEquals("$label revision", REVISION, d.revision)
        assertEquals("$label confidence echo", s.confidence, d.confidence, 0.0)
        return s
    }

    fun failure(o: MeasurementOutcome, label: String): MeasurementOutcome.Failure {
        val f = o as? MeasurementOutcome.Failure ?: throw AssertionError("$label: expected failure, got $o")
        assertTrue("$label actionable detail", !f.detail.isNullOrBlank())
        return f
    }

    fun eligible(a: Assessment, label: String): Assessment.Eligible =
        a as? Assessment.Eligible ?: throw AssertionError("$label: expected eligible, got $a")

    fun ineligible(a: Assessment, label: String): Assessment.Ineligible =
        a as? Assessment.Ineligible ?: throw AssertionError("$label: expected ineligible, got $a")
}
