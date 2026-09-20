package com.cocode.measureapp.geometry

import com.cocode.measureapp.capture.frames.ActiveArray
import com.cocode.measureapp.capture.frames.BufferIntrinsics
import com.cocode.measureapp.capture.frames.LensCharacteristics
import com.cocode.measureapp.capture.frames.SceneAligner
import com.cocode.measureapp.capture.frames.SceneAlignment
import com.cocode.measureapp.capture.gravity.GravitySample
import com.cocode.measureapp.capture.gravity.GravityUnavailableReason
import com.cocode.measureapp.capture.gravity.portraitCameraDownOrNull
import com.cocode.measureapp.capture.recovery.CalibrationQuality
import com.cocode.measureapp.capture.recovery.CaptureMetadata
import com.cocode.measureapp.capture.recovery.CropRect
import com.cocode.measureapp.capture.recovery.ExposureTimestamp
import com.cocode.measureapp.geometry.frames.AlignedGravity
import com.cocode.measureapp.geometry.frames.CalibrationProvenance
import com.cocode.measureapp.geometry.frames.CalibrationStatus
import com.cocode.measureapp.geometry.frames.CameraMounting
import com.cocode.measureapp.geometry.frames.FrameUnavailableReason
import com.cocode.measureapp.geometry.frames.GravityAlignmentReason
import com.cocode.measureapp.geometry.frames.IntrinsicsOrigin
import com.cocode.measureapp.geometry.frames.ProvenanceReason
import com.cocode.measureapp.geometry.frames.QuarterTurn
import com.cocode.measureapp.model.CapturedScene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraIntrinsicsTest {
    @Test fun inverseMatrixMapsPrincipalPointToOpticalAxis() {
        val k = CameraIntrinsics(fx = 1000.0, fy = 1000.0, cx = 640.0, cy = 360.0)
        val ray = k.inverseMatrix() * Vec3(640.0, 360.0, 1.0)
        assertEquals(0.0, ray.x, 1e-9)
        assertEquals(0.0, ray.y, 1e-9)
        assertEquals(1.0, ray.z, 1e-9)
    }

    @Test fun inverseMatrixNormalizesPixelOffsetByFocalLength() {
        val k = CameraIntrinsics(fx = 800.0, fy = 400.0, cx = 320.0, cy = 240.0)
        val ray = k.inverseMatrix() * Vec3(320.0 + 800.0, 240.0 - 400.0, 1.0)
        assertEquals(1.0, ray.x, 1e-9)
        assertEquals(-1.0, ray.y, 1e-9)
        assertEquals(1.0, ray.z, 1e-9)
    }

    @Test fun matrixMapsOpticalAxisRayToPrincipalPoint() {
        val k = CameraIntrinsics(fx = 1000.0, fy = 1000.0, cx = 640.0, cy = 360.0)
        val pixel = k.matrix() * Vec3(0.0, 0.0, 1.0)
        assertEquals(640.0, pixel.x, 1e-9)
        assertEquals(360.0, pixel.y, 1e-9)
        assertEquals(1.0, pixel.z, 1e-9)
    }

    @Test fun usableRequiresFinitePositiveFocalLengths() {
        assertTrue(CameraIntrinsics(1.0, 2.0, 0.0, 0.0).isUsable())
        assertFalse(CameraIntrinsics(0.0, 2.0, 0.0, 0.0).isUsable())
        assertFalse(CameraIntrinsics(1.0, -2.0, 0.0, 0.0).isUsable())
        assertFalse(CameraIntrinsics(Double.POSITIVE_INFINITY, 2.0, 0.0, 0.0).isUsable())
        assertFalse(CameraIntrinsics(1.0, 2.0, Double.NaN, 0.0).isUsable())
    }

    private fun assertNear(expected: Vec3, actual: Vec3) =
        assertTrue("expected $expected, was $actual", (expected - actual).norm() < 1e-9)

    private val array = ActiveArray(4000, 3000)
    private val calib = listOf(3100.0, 3050.0, 2010.0, 1480.0, 0.0)

    @Test fun deviceCalibrationWithAssumedStreamMappingIsApproximateNotCalibrated() {
        val same = BufferIntrinsics.estimate(LensCharacteristics(calib, array), 2000, 1500)
        assertEquals(CalibrationProvenance.DEVICE_CALIBRATION_ASSUMED_MAPPING, same.provenance)
        assertEquals(CalibrationStatus.APPROXIMATE, same.provenance.status)
        assertEquals(IntrinsicsOrigin.LENS_INTRINSIC_CALIBRATION, same.provenance.origin)
        assertEquals(ProvenanceReason.PROCESSED_IMAGE_MAPPING_ASSUMED, same.provenance.reason)
        // A matching pre-correction array still does not verify the processed-image mapping.
        val matched = BufferIntrinsics.estimate(LensCharacteristics(calib, array, preCorrectionArray = array), 2000, 1500)
        assertEquals(CalibrationProvenance.DEVICE_CALIBRATION_ASSUMED_MAPPING, matched.provenance)
        assertNear(Vec3(1550.0, 1525.0, 1005.0), Vec3(same.intrinsics.fx, same.intrinsics.fy, same.intrinsics.cx))
        assertEquals(740.0, same.intrinsics.cy, 1e-9)
        // 16:9 stream from a 4:3 array: rows 375..2625 of the array, uniform scale 0.48.
        val wide = BufferIntrinsics.estimate(LensCharacteristics(calib, array), 1920, 1080).intrinsics
        assertEquals(3100.0 * 0.48, wide.fx, 1e-9)
        assertEquals(3050.0 * 0.48, wide.fy, 1e-9)
        assertEquals(2010.0 * 0.48, wide.cx, 1e-9)
        assertEquals((1480.0 - 375.0) * 0.48, wide.cy, 1e-9)
    }

    @Test fun fallbacksAreApproximateAndNeverUpgraded() {
        val focal = LensCharacteristics(activeArray = array, focalLengthMm = 4.0, sensorWidthMm = 6.4, sensorHeightMm = 4.8)
        assertEquals(CalibrationProvenance.FOCAL_AND_SENSOR, BufferIntrinsics.estimate(focal, 2000, 1500).provenance)
        val broken = focal.copy(intrinsicCalibration = listOf(0.0, 3050.0, 2010.0, 1480.0, 0.0))
        assertEquals(
            CalibrationProvenance.FOCAL_AND_SENSOR.because(ProvenanceReason.CALIBRATION_VALUES_INVALID),
            BufferIntrinsics.estimate(broken, 2000, 1500).provenance,
        )
        val noArray = LensCharacteristics(intrinsicCalibration = calib)
        assertEquals(
            CalibrationProvenance.FOV_GUESS.because(ProvenanceReason.CALIBRATION_ARRAY_UNMAPPABLE),
            BufferIntrinsics.estimate(noArray, 2000, 1500).provenance,
        )
        val none = BufferIntrinsics.estimate(null, 2000, 1500)
        assertEquals(CalibrationProvenance.FOV_GUESS, none.provenance)
        assertEquals(CameraIntrinsics(2000.0, 2000.0, 1000.0, 750.0), none.intrinsics)
        val legacy = CapturedScene(10, 10, none.intrinsics, Vec3(0.0, 1.0, 0.0))
        assertEquals(CalibrationProvenance.UNAVAILABLE, legacy.calibration)
        assertTrue(legacy.alignedGravity is AlignedGravity.Unavailable)
    }

    @Test fun unsupportedDeviceCalibrationIsRejectedWithItsReason() {
        val focal = LensCharacteristics(activeArray = array, focalLengthMm = 4.0, sensorWidthMm = 6.4, sensorHeightMm = 4.8)
        fun reasonFor(c: List<Double>, pre: ActiveArray? = null): ProvenanceReason? {
            val p = BufferIntrinsics.estimate(focal.copy(intrinsicCalibration = c, preCorrectionArray = pre), 2000, 1500)
            assertEquals("fallback source for $c", IntrinsicsOrigin.FOCAL_LENGTH_AND_SENSOR_SIZE, p.provenance.origin)
            assertEquals(CalibrationStatus.APPROXIMATE, p.provenance.status)
            return p.provenance.reason
        }
        assertEquals(ProvenanceReason.CALIBRATION_SKEW_UNSUPPORTED, reasonFor(calib.dropLast(1) + 2.5))
        assertEquals(ProvenanceReason.CALIBRATION_SKEW_UNSUPPORTED, reasonFor(calib.dropLast(1) + Double.NaN))
        assertEquals(ProvenanceReason.CALIBRATION_VALUES_INVALID, reasonFor(calib.take(4)))
        assertEquals(ProvenanceReason.CALIBRATION_VALUES_INVALID, reasonFor(listOf(Double.POSITIVE_INFINITY) + calib.drop(1)))
        assertEquals(ProvenanceReason.CALIBRATION_VALUES_INVALID, reasonFor(listOf(3100.0, -1.0) + calib.drop(2)))
        assertEquals(ProvenanceReason.CALIBRATION_ARRAY_UNMAPPABLE, reasonFor(calib, ActiveArray(4032, 3024)))
        // No device path is ever labelled calibrated; only trusted fixtures are.
        for (lens in listOf(null, focal, focal.copy(intrinsicCalibration = calib), LensCharacteristics(calib, array))) {
            val status = BufferIntrinsics.estimate(lens, 1920, 1080).provenance.status
            assertTrue("device source $lens must not be calibrated", status != CalibrationStatus.CALIBRATED)
        }
    }

    @Test fun mountingMatchesPhysicalPortraitAndLandscapeHolds() {
        val back = CameraMounting.of(CameraMounting.CAMERA2_FACING_BACK, 90)!!
        // Upright portrait: buffer rotation 90; down is image-down.
        assertEquals(QuarterTurn.R90, back.expectedBufferRotation(0))
        assertNear(Vec3(0.0, 1.0, 0.0), QuarterTurn.R90.vector(back.deviceToBuffer(Vec3(0.0, -1.0, 0.0))))
        // Landscape, top of phone to the left (display ROTATION_90): buffer rotation 0.
        assertEquals(QuarterTurn.R0, back.expectedBufferRotation(90))
        assertNear(Vec3(0.0, 1.0, 0.0), QuarterTurn.R0.vector(back.deviceToBuffer(Vec3(-1.0, 0.0, 0.0))))
        // Reverse landscape (ROTATION_270): buffer rotation 180, phone top to the right.
        assertEquals(QuarterTurn.R180, back.expectedBufferRotation(270))
        assertNear(Vec3(0.0, 1.0, 0.0), QuarterTurn.R180.vector(back.deviceToBuffer(Vec3(1.0, 0.0, 0.0))))
        // Portrait matches the old upright-portrait bridge for an arbitrary tilted sample.
        val d = Vec3(0.2, -0.9, 0.38)
        val s = GravitySample.Available(d, 0L).portraitCameraDownOrNull()!!
        assertNear(s, QuarterTurn.R90.vector(back.deviceToBuffer(d)))
        assertNull(CameraMounting.of(2, 90))
        assertNull(CameraMounting.of(CameraMounting.CAMERA2_FACING_BACK, 45))
        assertNull(CameraMounting.of(CameraMounting.CAMERA2_FACING_BACK, null))
    }

    private fun meta(rotation: Int, crop: CropRect) = CaptureMetadata(
        3, 2000, 1500, rotation, crop, ExposureTimestamp.Unavailable("none"),
        GravitySample.Unavailable(GravityUnavailableReason.NO_SAMPLE_YET), emptyList(), CalibrationQuality.APPROXIMATE,
    )

    @Test fun metadataDescribesTheDeliveredBitmapOrFailsExplicitly() {
        val crop = CropRect(100, 50, 1900, 1450)
        val lens = LensCharacteristics(calib, array, facing = CameraMounting.CAMERA2_FACING_BACK, sensorOrientation = 90)
        val full = SceneAligner.fromMetadata(meta(90, crop), 2000, 1500, lens, 0)
        full as SceneAlignment.Aligned
        assertTrue(full.cropPending)
        assertEquals(3, full.scene.shotId)
        assertEquals(1400 to 1800, full.scene.imageWidth to full.scene.imageHeight)
        // The assumed-mapping device calibration keeps its quality/source through scene alignment.
        assertEquals(CalibrationProvenance.DEVICE_CALIBRATION_ASSUMED_MAPPING, full.scene.calibration)
        val skewed = SceneAligner.fromMetadata(meta(90, crop), 2000, 1500, lens.copy(intrinsicCalibration = calib.dropLast(1) + 1.0), 0)
        assertEquals(
            CalibrationProvenance.FOV_GUESS.because(ProvenanceReason.CALIBRATION_SKEW_UNSUPPORTED),
            (skewed as SceneAlignment.Aligned).scene.calibration,
        )
        assertEquals(
            AlignedGravity.Unavailable(GravityAlignmentReason.EXPOSURE_TIME_UNAVAILABLE), full.scene.alignedGravity,
        )
        val pre = SceneAligner.fromMetadata(meta(90, crop), 1800, 1400, null, null) as SceneAlignment.Aligned
        assertFalse(pre.cropPending)
        assertEquals(CalibrationProvenance.FOV_GUESS, pre.scene.calibration)
        assertEquals(
            SceneAlignment.Unavailable(FrameUnavailableReason.BITMAP_SIZE_UNEXPLAINED),
            SceneAligner.fromMetadata(meta(90, crop), 1500, 2000, null, null),
        )
        assertEquals(
            SceneAlignment.Unavailable(FrameUnavailableReason.INVALID_ROTATION),
            SceneAligner.fromMetadata(meta(45, crop), 2000, 1500, null, null),
        )
        assertEquals(
            SceneAlignment.Unavailable(FrameUnavailableReason.INVALID_CROP),
            SceneAligner.fromMetadata(meta(0, CropRect(0, 0, 2100, 1500)), 2000, 1500, null, null),
        )
    }
}
