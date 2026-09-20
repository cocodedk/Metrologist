package com.cocode.measureapp.geometry

import com.cocode.measureapp.capture.frames.SceneAligner
import com.cocode.measureapp.capture.frames.ShotGravity
import com.cocode.measureapp.capture.frames.ShotGravityMatcher
import com.cocode.measureapp.capture.gravity.GravitySample
import com.cocode.measureapp.capture.recovery.ExposureTimestamp
import com.cocode.measureapp.capture.recovery.TimestampSource
import com.cocode.measureapp.geometry.frames.AlignedGravity
import com.cocode.measureapp.geometry.frames.CalibrationProvenance
import com.cocode.measureapp.geometry.frames.FrameFixtures.EXPOSURE
import com.cocode.measureapp.geometry.frames.FrameFixtures.alignShot
import com.cocode.measureapp.geometry.frames.FrameFixtures.alignment
import com.cocode.measureapp.geometry.frames.FrameFixtures.back90
import com.cocode.measureapp.geometry.frames.FrameFixtures.deviceDown
import com.cocode.measureapp.geometry.frames.FrameFixtures.gravityMeasure
import com.cocode.measureapp.geometry.frames.FrameFixtures.kb
import com.cocode.measureapp.geometry.frames.FrameFixtures.rel
import com.cocode.measureapp.geometry.frames.FrameFixtures.rotations
import com.cocode.measureapp.geometry.frames.FrameFixtures.t
import com.cocode.measureapp.geometry.frames.GravityAlignmentReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Node 02 edges C03 (aligned projection) and C04 (aligned gravity). One physical upright view
 * is recorded by a fixed sensor (unequal fx/fy, off-centre principal point) in all four buffer
 * rotations, cropped/resized or not; after alignment every case must be the same scene.
 * Mathematical/synthetic only: no physical handset behaviour is proven here.
 */
class CoordinateFrameContractTest {
    private val provenances = listOf(
        CalibrationProvenance.CALIBRATED,
        CalibrationProvenance.DEVICE_CALIBRATION_ASSUMED_MAPPING,
        CalibrationProvenance.FOV_GUESS,
    )

    @Test fun C03_raysAndRectangleDimensionsAgreeAcrossRotationsAndCrops() {
        val up = SyntheticScene(3.0, 2.0, SceneRotations.roll(7.0) * SceneRotations.yawPitch(20.0, 15.0), t, kb, 1.0)
        var reference: EngineResult? = null
        for (cropped in listOf(false, true)) for (rot in rotations) {
            val buf = up.asBuffer(kb, rot.toDouble())
            val a = alignment(rot, cropped)
            val km = a.intrinsics(kb)!!
            val corners = buf.cornerPixels.map(a::toMarking)
            val stick = buf.stickPixels.map(a::toMarking)
            for ((i, p) in corners.withIndex()) {
                val ray = (km.inverseMatrix() * Vec3(p.x, p.y, 1.0)).normalized()
                val truth = up.cornerCam[i].normalized()
                assertEquals("rot $rot crop $cropped ray $i x", truth.x, ray.x, 1e-9)
                assertEquals("rot $rot crop $cropped ray $i y", truth.y, ray.y, 1e-9)
                assertEquals("rot $rot crop $cropped ray $i z", truth.z, ray.z, 1e-9)
                val back = a.toBuffer(p)
                assertEquals(buf.cornerPixels[i].x, back.x, 1e-9)
                assertEquals(buf.cornerPixels[i].y, back.y, 1e-9)
            }
            val result = MetrologyEngine.measure(corners, stick, km, up.profile)
            val ref = reference ?: result.also { reference = it }
            assertTrue("rot $rot width", rel(result.measurement.width, ref.measurement.width) < 1e-6)
            assertTrue("rot $rot height", rel(result.measurement.height, ref.measurement.height) < 1e-6)
            assertEquals("truth width", 3.0, result.measurement.width, 3.0 * 0.005)
            assertEquals("truth height", 2.0, result.measurement.height, 2.0 * 0.005)
        }
    }

    @Test fun C03_rotatingPixelsWithoutIntrinsicsIsDetectablyWrong() {
        val up = SyntheticScene(3.0, 2.0, SceneRotations.yawPitch(20.0, 15.0), t, kb, 1.0)
        val a = alignment(90, cropped = false)
        val p = a.toMarking(up.asBuffer(kb, 90.0).cornerPixels[0])
        val naive = (kb.inverseMatrix() * Vec3(p.x, p.y, 1.0)).normalized()
        val truth = up.cornerCam[0].normalized()
        assertTrue("buffer intrinsics on rotated pixels must not match", (naive - truth).norm() > 1e-2)
    }

    @Test fun C03_markingIntrinsicsSwapFocalsAndMovePrincipalPoint() {
        val k90 = alignment(90, cropped = false).intrinsics(kb)!!
        assertEquals(CameraIntrinsics(1410.0, 1520.0, 1500.0 - 711.0, 1043.0), k90)
        val kCrop = alignment(0, cropped = true).intrinsics(kb)!!
        assertEquals(CameraIntrinsics(760.0, 705.0, (1043.0 - 120.0) * 0.5, (711.0 - 90.0) * 0.5), kCrop)
        assertEquals(null, alignment(0, false).intrinsics(CameraIntrinsics(0.0, 1.0, 1.0, 1.0)))
        assertEquals(null, alignment(0, false).intrinsics(CameraIntrinsics(Double.NaN, 1.0, 1.0, 1.0)))
    }

    /**
     * Gravity branch on a tilted, rolled camera in all four orientations for one surface type:
     * the aligned gravity, plane normal and metric result must agree, and provenance survives.
     */
    private fun assertGravityBranchEquivalent(up: SyntheticScene, gravityCam: Vec3, surface: SurfaceOrientation) {
        var refNormal: Vec3? = null
        var ref: MeasurementResult? = null
        for (prov in provenances) for (rot in rotations) {
            val scene = alignShot(rot, deviceDown(gravityCam, rot), 40, prov).scene
            assertEquals("provenance survives alignment", prov, scene.calibration)
            assertEquals(7, scene.shotId)
            val aligned = scene.alignedGravity as AlignedGravity.Available
            assertTrue("$surface rot $rot aligned gravity", (aligned.down - gravityCam).norm() < 1e-9)
            assertEquals(40_000_000L, aligned.ageNanos)
            val buf = up.asBuffer(kb, rot.toDouble())
            val a = scene.alignment!!
            val (normal, m) = gravityMeasure(
                buf.cornerPixels.map(a::toMarking), buf.stickPixels.map(a::toMarking),
                scene.intrinsics, aligned.down, up.profile, surface,
            )
            val n0 = refNormal ?: normal.also { refNormal = it }
            assertTrue("$surface rot $rot normal", (normal - n0).norm() < 1e-9)
            val r0 = ref ?: m.also { ref = it }
            assertTrue("$surface rot $rot width", rel(m.width, r0.width) < 1e-6)
            assertTrue("$surface rot $rot height", rel(m.height, r0.height) < 1e-6)
            assertEquals(3.0, m.width, 3.0 * 0.02)
            assertEquals(2.0, m.height, 2.0 * 0.02)
        }
    }

    @Test fun C04_tiltedRolledCameraGravityBranchIsEquivalentInAllOrientations() {
        val up = SyntheticScene(3.0, 2.0, SceneRotations.roll(6.0) * SceneRotations.pitch(-12.0), t, kb, 1.0)
        assertGravityBranchEquivalent(up, up.gravityCam, SurfaceOrientation.VERTICAL)
    }

    @Test fun C04_tiltedRolledCameraOverFloorOrTableIsEquivalentInAllOrientations() {
        val up = SyntheticScene(3.0, 2.0, SceneRotations.roll(9.0) * SceneRotations.pitch(-25.0), t, kb, 1.0)
        // Gravity is not parallel to any image axis, so a wrong quarter turn cannot hide.
        assertTrue(listOf(up.floorGravityCam.x, up.floorGravityCam.y).all { kotlin.math.abs(it) > 0.05 })
        assertGravityBranchEquivalent(up, up.floorGravityCam, SurfaceOrientation.HORIZONTAL)
    }

    @Test fun C04_staleOrMissingGravityIsUnavailableNotUpright() {
        val d = deviceDown(Vec3(0.3, 0.9, 0.2).normalized(), 90)
        val stale = alignShot(90, d, 150, CalibrationProvenance.CALIBRATED).scene
        assertEquals(AlignedGravity.Unavailable(GravityAlignmentReason.STALE_SAMPLE), stale.alignedGravity)
        val noClock = SceneAligner.alignGravity(alignment(0, false), back90, null,
            ExposureTimestamp.Available(EXPOSURE, TimestampSource.UNKNOWN), listOf(GravitySample.Available(d, EXPOSURE)))
        assertEquals(AlignedGravity.Unavailable(GravityAlignmentReason.CAMERA_CLOCK_NOT_REALTIME), noClock)
        val noSample = SceneAligner.alignGravity(alignment(0, false), back90, null,
            ExposureTimestamp.Available(EXPOSURE, TimestampSource.REALTIME), emptyList())
        assertEquals(AlignedGravity.Unavailable(GravityAlignmentReason.NO_SAMPLE_AT_OR_BEFORE_EXPOSURE), noSample)
        val noMount = SceneAligner.alignGravity(alignment(0, false), null, null,
            ExposureTimestamp.Available(EXPOSURE, TimestampSource.REALTIME), listOf(GravitySample.Available(d, EXPOSURE)))
        assertEquals(AlignedGravity.Unavailable(GravityAlignmentReason.MOUNTING_UNAVAILABLE), noMount)
        val contradicted = alignShot(90, d, 10, CalibrationProvenance.CALIBRATED, target = 90).scene
        assertEquals(AlignedGravity.Unavailable(GravityAlignmentReason.MOUNTING_UNAVAILABLE), contradicted.alignedGravity)
    }

    private fun matchAt(ageNanos: Long, source: TimestampSource = TimestampSource.REALTIME) =
        ShotGravityMatcher.match(
            ExposureTimestamp.Available(EXPOSURE, source),
            listOf(GravitySample.Available(Vec3(0.0, -1.0, 0.0), EXPOSURE - ageNanos)),
        )

    @Test fun C04_exposureMatchingAcceptsOnlyComparableNonFutureSamplesWithin100ms() {
        assertTrue(matchAt(99_000_000L) is ShotGravity.Matched)
        assertTrue(matchAt(100_000_000L) is ShotGravity.Matched)
        assertEquals(ShotGravity.Unmatched(GravityAlignmentReason.STALE_SAMPLE), matchAt(101_000_000L))
        assertEquals(ShotGravity.Unmatched(GravityAlignmentReason.NO_SAMPLE_AT_OR_BEFORE_EXPOSURE), matchAt(-1L))
        assertTrue(matchAt(0L) is ShotGravity.Matched)
        // Identical numbers, different clock domains: UNKNOWN never passes by coincidence.
        assertEquals(ShotGravity.Unmatched(GravityAlignmentReason.CAMERA_CLOCK_NOT_REALTIME), matchAt(0L, TimestampSource.UNKNOWN))
        assertEquals(
            ShotGravity.Unmatched(GravityAlignmentReason.EXPOSURE_TIME_UNAVAILABLE),
            ShotGravityMatcher.match(ExposureTimestamp.Unavailable("none"), listOf(GravitySample.Available(Vec3(0.0, 1.0, 0.0), 1L))),
        )
        val newest = GravitySample.Available(Vec3(1.0, 0.0, 0.0), EXPOSURE - 20_000_000L)
        val older = GravitySample.Available(Vec3(0.0, 1.0, 0.0), EXPOSURE - 60_000_000L)
        val future = GravitySample.Available(Vec3(0.0, 0.0, 1.0), EXPOSURE + 5_000_000L)
        val picked = ShotGravityMatcher.match(ExposureTimestamp.Available(EXPOSURE, TimestampSource.REALTIME), listOf(older, newest, future))
        assertEquals(ShotGravity.Matched(newest, 20_000_000L), picked)
    }
}
