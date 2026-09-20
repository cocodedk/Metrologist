package com.cocode.measureapp.geometry.frames

import com.cocode.measureapp.capture.frames.SceneAligner
import com.cocode.measureapp.capture.frames.SceneAlignment
import com.cocode.measureapp.capture.gravity.GravitySample
import com.cocode.measureapp.capture.recovery.ExposureTimestamp
import com.cocode.measureapp.capture.recovery.TimestampSource
import com.cocode.measureapp.geometry.CameraIntrinsics
import com.cocode.measureapp.geometry.GravitySolver
import com.cocode.measureapp.geometry.MeasurementResult
import com.cocode.measureapp.geometry.Measurements
import com.cocode.measureapp.geometry.SceneRotations
import com.cocode.measureapp.geometry.StickProfile
import com.cocode.measureapp.geometry.SurfaceOrientation
import com.cocode.measureapp.geometry.Vec2
import com.cocode.measureapp.geometry.Vec3
import com.cocode.measureapp.geometry.projectToPlane
import com.cocode.measureapp.stick.StickScale
import kotlin.math.abs

/**
 * Shared synthetic capture for the node-02 frame contracts: a fixed sensor with unequal fx/fy
 * and an off-centre principal point, a back camera mounted at SENSOR_ORIENTATION 90, and a
 * REALTIME exposure clock. Pure test data; no physical handset behaviour is implied.
 */
object FrameFixtures {
    val kb = CameraIntrinsics(fx = 1520.0, fy = 1410.0, cx = 1043.0, cy = 711.0)
    val t = Vec3(0.2, -0.1, 6.0)
    const val EXPOSURE = 5_000_000_000L
    val rotations = listOf(0, 90, 180, 270)
    val back90 = CameraMounting(LensFacing.BACK, QuarterTurn.R90)

    /** Buffer -> marking transform; cropped cases also halve the crop (resize). */
    fun alignment(rot: Int, cropped: Boolean) = if (cropped) {
        FrameAlignment(2000, 1500, 120, 90, 1760, 1320, 880, 660, QuarterTurn.of(rot)!!)
    } else {
        FrameAlignment(2000, 1500, 0, 0, 2000, 1500, 2000, 1500, QuarterTurn.of(rot)!!)
    }

    fun rel(a: Double, b: Double) = abs(a - b) / abs(b)

    /** Device-axes down for [back90] whose marking-frame down is [gm] at buffer rotation [rot]. */
    fun deviceDown(gm: Vec3, rot: Int): Vec3 {
        val u = SceneRotations.roll(90.0) * (SceneRotations.roll(-rot.toDouble()) * gm)
        return Vec3(u.x, -u.y, -u.z)
    }

    /** The gravity-branch measurement: plane from [g] alone, metric from the stick. */
    fun gravityMeasure(
        corners: List<Vec2>, stick: List<Vec2>, k: CameraIntrinsics, g: Vec3,
        profile: StickProfile, orientation: SurfaceOrientation,
    ): Pair<Vec3, MeasurementResult> {
        val frame = GravitySolver.solve(g, orientation).frame
        val scale = StickScale.solve(projectToPlane(stick, k, frame), profile)
        return frame.normal to Measurements.compute(projectToPlane(corners, k, frame).map { it * scale.scale })
    }

    /** Aligns one shot whose single gravity sample is [ageMs] older than the exposure. */
    fun alignShot(rot: Int, d: Vec3, ageMs: Long, prov: CalibrationProvenance, target: Int? = (450 - rot) % 360) =
        SceneAligner.align(
            7, alignment(rot, cropped = rot % 180 == 0), ProvenancedIntrinsics(kb, prov), back90, target,
            ExposureTimestamp.Available(EXPOSURE, TimestampSource.REALTIME),
            listOf(GravitySample.Available(d, EXPOSURE - ageMs * 1_000_000L)),
        ) as SceneAlignment.Aligned
}
