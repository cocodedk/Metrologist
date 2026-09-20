package com.cocode.measureapp.geometry

import com.cocode.measureapp.geometry.frames.CalibrationProvenance
import com.cocode.measureapp.geometry.frames.CalibrationStatus
import com.cocode.measureapp.geometry.rectangle.RectangleCandidate
import com.cocode.measureapp.geometry.rectangle.RectangleRejection
import com.cocode.measureapp.geometry.rectangle.RectangleRejectionFixtures
import com.cocode.measureapp.geometry.rectangle.VanishingDirection
import com.cocode.measureapp.stick.StickScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sqrt

/**
 * Contract C07 (node 01): homogeneous vanishing points, including points at infinity, yield a
 * finite orthonormal plane candidate with evidence, or an explicit rejection. Ground truth comes
 * from [SyntheticScene], which projects with an independent pinhole model.
 */
class VanishingPointContractTest {
    private val poses = mapOf(
        "yaw 0" to SceneRotations.yawPitch(0.0, 0.0),
        "yaw 30" to SceneRotations.yawPitch(30.0, 0.0),
        "yaw 45" to SceneRotations.yawPitch(45.0, 0.0),
        "yaw 60" to SceneRotations.yawPitch(60.0, 0.0),
        "pitch 30" to SceneRotations.yawPitch(0.0, 30.0),
        "yaw 25 pitch 20" to SceneRotations.yawPitch(25.0, 20.0),
    )

    private fun accepted(c: RectangleCandidate, label: String): RectangleCandidate.Accepted =
        c as? RectangleCandidate.Accepted ?: throw AssertionError("$label rejected: $c")

    private fun rejected(c: RectangleCandidate, reason: RectangleRejection) {
        val r = c as? RectangleCandidate.Rejected ?: throw AssertionError("expected $reason, got $c")
        assertEquals(reason, r.reason)
    }

    /** Candidate plane -> projection -> stick scale -> measurements (no selector involved). */
    private fun measure(frame: PlaneFrame, corners: List<Vec2>, s: SyntheticScene, k: CameraIntrinsics = s.k): MeasurementResult {
        val scale = StickScale.solve(projectToPlane(s.stickPixels, k, frame), s.profile)
        return Measurements.compute(projectToPlane(corners, k, frame).map { it * scale.scale })
    }

    private fun assertRel(label: String, truth: Double, actual: Double, rel: Double) =
        assertEquals(label, truth, actual, truth * rel)

    @Test fun exactPosesIncludingInfinityRecoverTruth() {
        for ((label, r) in poses) {
            val s = ContractScenes.wall(r)
            val c = accepted(RectangleSolver.candidate(s.cornerPixels, s.k, CalibrationProvenance.CALIBRATED, label), label)
            assertEquals(CalibrationProvenance.CALIBRATED, c.provenance)
            assertEquals(label, c.sceneId)
            val f = c.frame
            for (v in listOf(f.e1, f.e2, f.normal)) assertEquals("$label unit axis", 1.0, v.norm(), 1e-12)
            assertEquals(0.0, f.e1.dot(f.e2), 1e-12)
            assertEquals(0.0, f.e1.dot(f.normal), 1e-12)
            assertEquals(0.0, f.e2.dot(f.normal), 1e-12)
            assertTrue("$label residual ${c.evidence.orthogonalityResidual}", c.evidence.orthogonalityResidual < 1e-9)
            val m = measure(f, s.cornerPixels, s)
            assertRel("$label width", 2.0, m.width, 1e-6)
            assertRel("$label height", 1.0, m.height, 1e-6)
            assertRel("$label area", 2.0, m.area, 1e-6)
            assertRel("$label diagonal", sqrt(5.0), m.diagonal, 1e-6)
            m.cornerAngles.forEach { assertTrue("$label angle $it", it.isFinite() && abs(it - 90.0) < 1e-6) }
        }
    }

    @Test fun infiniteVanishingPointsAreDirectionsNotFailures() {
        fun ev(r: Mat3) = accepted(RectangleSolver.candidate(ContractScenes.wall(r).cornerPixels, ContractScenes.K), "$r").evidence
        val frontal = ev(SceneRotations.yawPitch(0.0, 0.0))
        assertTrue(frontal.widthVanishing is VanishingDirection.AtInfinity)
        assertTrue(frontal.heightVanishing is VanishingDirection.AtInfinity)
        val yaw = ev(SceneRotations.yawPitch(45.0, 0.0))
        assertTrue(yaw.widthVanishing is VanishingDirection.Finite)
        assertTrue(yaw.heightVanishing is VanishingDirection.AtInfinity)
        val pitch = ev(SceneRotations.yawPitch(0.0, 30.0))
        assertTrue(pitch.widthVanishing is VanishingDirection.AtInfinity)
        assertTrue(pitch.heightVanishing is VanishingDirection.Finite)
        // Image-parallel lines meet at a nonzero point at infinity; coincident lines give zero.
        val parallel = Projective.homogeneousVanishingPoint(Vec2(0.0, 0.0), Vec2(5.0, 0.0), Vec2(0.0, 1.0), Vec2(5.0, 1.0))
        assertEquals(0.0, parallel.z, 0.0)
        assertTrue(parallel.norm() > 0.0)
        val same = Projective.homogeneousVanishingPoint(Vec2(0.0, 0.0), Vec2(5.0, 0.0), Vec2(9.0, 0.0), Vec2(2.0, 0.0))
        assertEquals(0.0, same.norm(), 0.0)
    }

    @Test fun marksPerturbedAcrossParallelEdgesStayContinuous() {
        for (r in listOf(SceneRotations.yawPitch(0.0, 0.0), SceneRotations.yawPitch(45.0, 0.0))) {
            val s = ContractScenes.wall(r)
            val normals = mutableListOf<Vec3>()
            for (dx in listOf(-0.5, -1e-6, 1e-6, 0.5)) {
                val marks = ContractScenes.offset(s.cornerPixels, listOf(Vec2(dx, 0.0), Vec2(0.0, 0.0), Vec2(0.0, 0.0), Vec2(0.0, 0.0)))
                val c = accepted(RectangleSolver.candidate(marks, s.k), "dx=$dx")
                val tol = if (abs(dx) < 1e-3) 1e-5 else 0.02
                val m = measure(c.frame, marks, s)
                assertRel("dx=$dx width", 2.0, m.width, tol)
                assertRel("dx=$dx height", 1.0, m.height, tol)
                normals += c.frame.normal
            }
            // Crossing the point at infinity (-1e-6 -> +1e-6 px) must not jump the plane.
            assertTrue("normal jump", acos(normals[1].dot(normals[2]).coerceIn(-1.0, 1.0)) < 1e-5)
        }
    }

    /**
     * Node 03's half-pixel fixture: the CANDIDATE is accepted and its plane measures within 2%.
     * This proves candidate accuracy only; eligibility and live selection belong to node 03.
     */
    @Test fun halfPixelFixtureCandidateStaysWithinTwoPercent() {
        val s = ContractScenes.wall(SceneRotations.yawPitch(30.0, 20.0))
        for (sign in listOf(1.0, -1.0)) {
            val marks = ContractScenes.offset(s.cornerPixels, ContractScenes.halfPixelOffsets(sign))
            val c = accepted(RectangleSolver.candidate(marks, s.k, CalibrationProvenance.CALIBRATED), "sign $sign")
            val ev = c.evidence
            assertTrue("sensitivity ${ev.halfPixelAspectSensitivity}", ev.halfPixelAspectSensitivity.isFinite())
            val m = measure(c.frame, marks, s)
            assertRel("width", 2.0, m.width, 0.02)
            assertRel("height", 1.0, m.height, 0.02)
        }
    }

    @Test fun unusableNumericProjectionInputsAreRejectedNotThrown() {
        val good = ContractScenes.wall(SceneRotations.yawPitch(30.0, 0.0)).cornerPixels
        for (k in RectangleRejectionFixtures.unusableIntrinsics) {
            assertTrue("$k passes the metadata check", k.isUsable())
            rejected(RectangleSolver.candidate(good, k, CalibrationProvenance.CALIBRATED, "s"), RectangleRejection.NUMERICAL_FAILURE)
        }
        val huge = RectangleRejectionFixtures.overflowingCorners
        assertTrue(huge.all { it.x.isFinite() && it.y.isFinite() })
        rejected(RectangleSolver.candidate(huge, ContractScenes.K), RectangleRejection.NUMERICAL_FAILURE)
        // Guards must not disturb valid geometry under the same code path.
        accepted(RectangleSolver.candidate(good, ContractScenes.K), "control")
    }

    @Test fun cornerRaysOnIncompatibleDepthSidesAreProjectionUnusable() {
        rejected(RectangleSolver.candidate(RectangleRejectionFixtures.crossedQuad, ContractScenes.K), RectangleRejection.PROJECTION_UNUSABLE)
        val behind = RectangleRejectionFixtures.wallBehindCamera
        assertTrue(behind.cornerCam.any { it.z < 0.0 } && behind.cornerCam.any { it.z > 0.0 })
        rejected(RectangleSolver.candidate(behind.cornerPixels, behind.k), RectangleRejection.PROJECTION_UNUSABLE)
    }

    @Test fun focalErrorIsQualifiedApproximateNotExact() {
        val s = ContractScenes.wall(SceneRotations.yawPitch(30.0, 20.0))
        for (factor in listOf(1.05, 0.95)) {
            val kWrong = CameraIntrinsics(s.k.fx * factor, s.k.fy * factor, s.k.cx, s.k.cy)
            val c = accepted(RectangleSolver.candidate(s.cornerPixels, kWrong, CalibrationProvenance.FOCAL_AND_SENSOR), "f x $factor")
            assertEquals(CalibrationStatus.APPROXIMATE, c.provenance.status)
            // Wrong focal breaks back-projected orthogonality: visible evidence, not exactness.
            assertTrue("residual ${c.evidence.orthogonalityResidual}", c.evidence.orthogonalityResidual > 1e-4)
            val m = measure(c.frame, s.cornerPixels, s, kWrong)
            assertTrue(m.width.isFinite() && m.height.isFinite())
            assertRel("f x $factor width", 2.0, m.width, 0.10)
            assertRel("f x $factor height", 1.0, m.height, 0.10)
        }
    }

    @Test fun unstableOrGrazingViewsAreRejected() {
        // 2 x 1 cm target at 4 m is ~5 x 2.5 px: image angles look perfectly square, yet
        // half-pixel marking noise dominates the rectified shape.
        val tiny = ContractScenes.wall(SceneRotations.yawPitch(0.0, 0.0), w = 0.02, h = 0.01)
        rejected(RectangleSolver.candidate(tiny.cornerPixels, tiny.k), RectangleRejection.ILL_CONDITIONED)
        // 88 degrees of yaw: corner rays run within ~2 degrees of the plane.
        val grazing = ContractScenes.wall(SceneRotations.yawPitch(88.0, 0.0))
        rejected(RectangleSolver.candidate(grazing.cornerPixels, grazing.k), RectangleRejection.GRAZING_VIEW)
    }

    @Test fun degenerateInputsAreExplicitRejections() {
        val k = ContractScenes.K
        val repeated = listOf(Vec2(100.0, 100.0), Vec2(300.0, 200.0), Vec2(500.0, 100.0), Vec2(300.0, 200.0))
        rejected(RectangleSolver.candidate(repeated, k), RectangleRejection.COINCIDENT_DIRECTIONS)
        val collinear = listOf(Vec2(0.0, 0.0), Vec2(10.0, 0.0), Vec2(20.0, 0.0), Vec2(30.0, 0.0))
        rejected(RectangleSolver.candidate(collinear, k), RectangleRejection.ZERO_VANISHING_VECTOR)
        val collapsed = listOf(Vec2(0.0, 0.0), Vec2(0.0, 0.0), Vec2(20.0, 20.0), Vec2(0.0, 20.0))
        rejected(RectangleSolver.candidate(collapsed, k), RectangleRejection.COINCIDENT_CORNERS)
        val good = ContractScenes.wall(SceneRotations.yawPitch(30.0, 0.0)).cornerPixels
        rejected(RectangleSolver.candidate(good.dropLast(1), k), RectangleRejection.INVALID_CORNER_COUNT)
        rejected(RectangleSolver.candidate(good.dropLast(1) + Vec2(Double.NaN, 1.0), k), RectangleRejection.NON_FINITE_CORNERS)
        rejected(RectangleSolver.candidate(good, k.copy(fx = 0.0)), RectangleRejection.INVALID_INTRINSICS)
        assertEquals(MeasurementFailureReason.METADATA_UNAVAILABLE, RectangleRejection.INVALID_INTRINSICS.failure)
    }
}
