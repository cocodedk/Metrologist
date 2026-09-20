package com.cocode.measureapp.geometry

import com.cocode.measureapp.capture.gravity.GravitySample
import com.cocode.measureapp.capture.gravity.GravitySampleDecoder
import com.cocode.measureapp.capture.gravity.GravitySampleHistory
import com.cocode.measureapp.capture.gravity.GravityUnavailableReason
import com.cocode.measureapp.capture.gravity.LevelReading
import com.cocode.measureapp.capture.gravity.SensorClock
import com.cocode.measureapp.capture.gravity.levelReadingOf
import com.cocode.measureapp.capture.gravity.portraitCameraDownOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/**
 * C02 downward-vector contract, driven through the same decoder the Android adapter calls.
 *
 * Raw samples are derived from physical poses, not from the implementation: Android's
 * `TYPE_GRAVITY` reads the support reaction (physically UP) in device axes x right,
 * y natural top, z out of the screen. `Display.getRotation()` returns ROTATION_90 when the
 * device is turned 90 degrees counter-clockwise (natural top pointing left, +x up).
 */
class GravityDirectionContractTest {
    private val g = 9.81f
    private val eps = 1e-6
    private val c30 = cos(Math.toRadians(30.0)); private val s30 = sin(Math.toRadians(30.0))

    private fun raw(x: Double, y: Double, z: Double, t: Long = 1_000L) =
        GravitySampleDecoder.decode(floatArrayOf((x * g).toFloat(), (y * g).toFloat(), (z * g).toFloat()), t)

    private fun down(s: GravitySample) = (s as GravitySample.Available).down

    private fun assertVec(expected: Vec3, actual: Vec3, tol: Double = eps) {
        assertEquals("x", expected.x, actual.x, tol)
        assertEquals("y", expected.y, actual.y, tol)
        assertEquals("z", expected.z, actual.z, tol)
    }

    private fun overlay(s: GravitySample, rot: Int) = (levelReadingOf(s, rot) as LevelReading.Tilt).angles

    @Test fun uprightPortraitDecodesToPhysicalDownInDeviceAxes() {
        assertVec(Vec3(0.0, -1.0, 0.0), down(raw(0.0, 1.0, 0.0)))
    }

    @Test fun flatScreenUpAndScreenDownDecode() {
        assertVec(Vec3(0.0, 0.0, -1.0), down(raw(0.0, 0.0, 1.0)))   // lying on a table, screen up
        assertVec(Vec3(0.0, 0.0, 1.0), down(raw(0.0, 0.0, -1.0)))   // screen facing the floor
    }

    @Test fun bothLandscapePosesDecode() {
        assertVec(Vec3(-1.0, 0.0, 0.0), down(raw(1.0, 0.0, 0.0)))   // top to the left: +x is up
        assertVec(Vec3(1.0, 0.0, 0.0), down(raw(-1.0, 0.0, 0.0)))   // top to the right: +x is down
    }

    @Test fun thirtyDegreeForwardTipDecodes() {
        // Top tipped 30 deg away from the user: device y = cos30 up + sin30 away,
        // device z = sin30 up - cos30 away, so up reads (0, cos30, sin30).
        assertVec(Vec3(0.0, -c30, -s30), down(raw(0.0, c30, s30)))
    }

    @Test fun decodedVectorIsUnitAndKeepsSensorTimestampAndClock() {
        val s = GravitySampleDecoder.decode(floatArrayOf(3f, 4f, 0f), 123_456_789_012L)
        s as GravitySample.Available
        assertEquals(1.0, s.down.norm(), 1e-12)
        assertVec(Vec3(-0.6, -0.8, 0.0), s.down)
        assertEquals(123_456_789_012L, s.timestampNanos)
        assertEquals(SensorClock.ELAPSED_REALTIME_NANOS, s.clock)
    }

    @Test fun invalidVectorsAreUnavailable() {
        val bad = listOf(
            floatArrayOf(0f, 0f, 0f), floatArrayOf(Float.NaN, g, 0f),
            floatArrayOf(0f, Float.POSITIVE_INFINITY, 0f), floatArrayOf(0f, 0f, Float.NEGATIVE_INFINITY),
            floatArrayOf(0f, g), floatArrayOf(1e-9f, 0f, 0f),
        )
        for (v in bad) {
            val s = GravitySampleDecoder.decode(v, 1L)
            assertEquals(v.contentToString(), GravitySample.Unavailable(GravityUnavailableReason.INVALID_VECTOR), s)
            assertNull(s.portraitCameraDownOrNull())
            assertFalse(levelReadingOf(s, 0).isLevel())
        }
    }

    @Test fun sensorAbsenceAndFirstReadingAreUnavailableNotLevel() {
        val absent = GravitySampleHistory(sensorPresent = false)
        val waiting = GravitySampleHistory(sensorPresent = true)
        assertEquals(GravitySample.Unavailable(GravityUnavailableReason.NO_SENSOR), absent.latest)
        assertEquals(GravitySample.Unavailable(GravityUnavailableReason.NO_SAMPLE_YET), waiting.latest)
        for (s in listOf(absent.latest, waiting.latest)) {
            val reading = levelReadingOf(s, 0)
            assertTrue(reading is LevelReading.Unavailable)
            assertFalse(reading.isLevel())
            assertNull(s.portraitCameraDownOrNull())
        }
        assertTrue(waiting.snapshot().isEmpty())
    }

    @Test fun historyKeepsValidSamplesOnlyAndExposesInvalidLatest() {
        val h = GravitySampleHistory(capacity = 2, sensorPresent = true)
        h.record(raw(0.0, 1.0, 0.0, t = 10))
        h.record(raw(0.0, 1.0, 0.0, t = 20))
        h.record(raw(0.0, 1.0, 0.0, t = 30))
        h.record(GravitySampleDecoder.decode(floatArrayOf(0f, 0f, 0f), 40))
        assertEquals(listOf(20L, 30L), h.snapshot().map { it.timestampNanos })
        assertTrue(h.latest is GravitySample.Unavailable)
    }

    @Test fun uprightInEveryDisplayRotationReadsLevelZeroZero() {
        // Level pose for each Display rotation: 0 upright, 90 top-left, 180 reversed, 270 top-right.
        val poses = mapOf(0 to raw(0.0, 1.0, 0.0), 90 to raw(1.0, 0.0, 0.0),
            180 to raw(0.0, -1.0, 0.0), 270 to raw(-1.0, 0.0, 0.0))
        for ((rot, s) in poses) {
            val t = overlay(s, rot)
            assertEquals("pitch@$rot", 0.0, t.pitchDeg, eps)
            assertEquals("roll@$rot", 0.0, t.rollDeg, eps)
            assertTrue(levelReadingOf(s, rot).isLevel())
        }
    }

    @Test fun landscapePosesWithWrongRotationAreNotLevel() {
        assertFalse(levelReadingOf(raw(1.0, 0.0, 0.0), 270).isLevel())
        assertFalse(levelReadingOf(raw(-1.0, 0.0, 0.0), 90).isLevel())
        assertFalse(levelReadingOf(raw(0.0, 1.0, 0.0), 180).isLevel())
    }

    @Test fun flatPosesReadPlusMinusNinetyOverlayPitch() {
        assertEquals(90.0, overlay(raw(0.0, 0.0, 1.0), 0).pitchDeg, eps)    // looking at the floor
        assertEquals(-90.0, overlay(raw(0.0, 0.0, -1.0), 0).pitchDeg, eps)  // looking at the ceiling
    }

    @Test fun uprightCameraFrameDownIsPlusYAndLookingDownHasPositiveZ() {
        assertVec(Vec3(0.0, 1.0, 0.0), raw(0.0, 1.0, 0.0).portraitCameraDownOrNull()!!)
        assertTrue(raw(0.0, 0.0, 1.0).portraitCameraDownOrNull()!!.z > 0.99)
    }

    @Test fun thirtyDegreeTipReadsPositiveOverlayPitchButNegativeDiagnosticPitch() {
        val degTol = 1e-4   // raw samples are Float, so sub-arcsecond is all that survives
        val s = raw(0.0, c30, s30)
        val t = overlay(s, 0)
        assertEquals(30.0, t.pitchDeg, degTol)
        assertEquals(0.0, t.rollDeg, degTol)
        assertEquals(-30.0, diagnosticTilt(s.portraitCameraDownOrNull()!!), degTol)
        assertEquals(30.0, diagnosticTilt(raw(0.0, c30, -s30).portraitCameraDownOrNull()!!), degTol)
    }

    /** The engine's own diagnostic tilt: looking up positive, looking down negative. */
    private fun diagnosticTilt(cameraDown: Vec3): Double {
        val k = CameraIntrinsics(fx = 1500.0, fy = 1500.0, cx = 960.0, cy = 540.0)
        val scene = SyntheticScene(3.0, 2.0, SceneRotations.yawPitch(1.5, 0.0), Vec3(0.0, 0.0, 6.0), k, 1.0)
        return MetrologyEngine.measureHybrid(
            scene.cornerPixels, scene.stickPixels, scene.k, scene.profile, cameraDown, SurfaceOrientation.VERTICAL,
        ).diagnostics!!.cameraTiltDeg
    }
}
