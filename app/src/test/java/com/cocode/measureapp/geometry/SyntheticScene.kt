package com.cocode.measureapp.geometry

import kotlin.math.cos
import kotlin.math.sin

/**
 * Test-only synthetic ground-truth oracle: projects a KNOWN planar wall + stick to image pixels
 * with an INDEPENDENT pinhole model. This deliberately does NOT call any engine code
 * (`projectToPlane`, `RectangleSolver`, ...), so feeding its pixels back through the engine is a
 * genuine round-trip correctness check rather than a tautology.
 *
 * World frame: the wall lies in the z = 0 plane, centered at the origin. The wall is a [w] x [h]
 * rectangle; its corners are emitted clockwise from top-left as `[TL, TR, BR, BL]`. The stick is
 * a [l] x [sw] rectangle lying flat on the wall, centered on its horizontal midline (y = 0); its
 * 4 corners are emitted clockwise so the engine's box-scale (long edges = length, short edges =
 * width) matches `StickProfile(l, 4, sw)`.
 *
 * Camera model: a world point X maps to camera coords `Xc = R*X + t` (positive `Xc.z` = in front
 * of the camera), then to a pixel via `K.matrix() * (Xc * (1/Xc.z))`, taking `(x, y)`.
 *
 * @param w wall width in real units.
 * @param h wall height in real units.
 * @param r camera rotation applied to world points.
 * @param t camera translation placing the wall in front (positive depth).
 * @param k camera intrinsics.
 * @param l stick total length in real units (same unit as [w], [h]).
 * @param sw stick width in real units; defaults to a thin stick. Used by the box-scale path.
 */
class SyntheticScene(
    val w: Double,
    val h: Double,
    val r: Mat3,
    val t: Vec3,
    val k: CameraIntrinsics,
    val l: Double,
    val sw: Double = 0.08,
) {
    /** Real wall corners in `[TL, TR, BR, BL]` clockwise order, projected to image pixels. */
    val cornerPixels: List<Vec2> = listOf(
        Vec3(-w / 2.0, -h / 2.0, 0.0), // TL
        Vec3(w / 2.0, -h / 2.0, 0.0),  // TR
        Vec3(w / 2.0, h / 2.0, 0.0),   // BR
        Vec3(-w / 2.0, h / 2.0, 0.0),  // BL
    ).map { project(it) }

    /**
     * Stick box: the 4 corners of an [l] x [sw] rectangle on the wall (z = 0), centered on
     * y = 0, emitted clockwise as `[TL, TR, BR, BL]` so the long edges run along the length.
     */
    val stickPixels: List<Vec2> = listOf(
        Vec3(-l / 2.0, -sw / 2.0, 0.0), // TL
        Vec3(l / 2.0, -sw / 2.0, 0.0),  // TR
        Vec3(l / 2.0, sw / 2.0, 0.0),   // BR
        Vec3(-l / 2.0, sw / 2.0, 0.0),  // BL
    ).map { project(it) }

    /** Stick profile matching the box the scene emits: known length [l] AND width [sw]. */
    val profile: StickProfile = StickProfile(totalLength = l, bandCount = 4, width = sw)

    /**
     * Camera-frame gravity for this pose: world **down** `(0,1,0)` rotated into the camera by
     * the SAME world->camera rotation [r] used for projection, then normalized. This is the
     * ground-truth gravity an IMU would report; it is derived purely from the pose and NEVER
     * from any solver, so feeding it through the gravity pipeline is a genuine round-trip check.
     */
    val gravityCam: Vec3 = (r * Vec3(0.0, 1.0, 0.0)).normalized()

    /**
     * Camera-frame gravity if the same rectangle is read as a FLOOR/TABLE: world down is the
     * plane normal `(0,0,1)`, pointing away from a camera on the `z < 0` side (any `t.z > 0`
     * pose close to frontal). Derived from the pose only, never from a solver.
     */
    val floorGravityCam: Vec3 = (r * Vec3(0.0, 0.0, 1.0)).normalized()

    /** Wall corners `[TL, TR, BR, BL]` in camera coordinates (`R*X + t`), for ray checks. */
    val cornerCam: List<Vec3> get() = listOf(
        Vec3(-w / 2.0, -h / 2.0, 0.0), Vec3(w / 2.0, -h / 2.0, 0.0),
        Vec3(w / 2.0, h / 2.0, 0.0), Vec3(-w / 2.0, h / 2.0, 0.0),
    ).map { r * it + t }

    /**
     * The same physical view recorded in a sensor BUFFER that must be turned clockwise by
     * [bufferRotationDeg] to look like this scene: the camera is rolled by `-bufferRotationDeg`
     * about its optical axis and projects through the fixed sensor intrinsics [kb].
     */
    fun asBuffer(kb: CameraIntrinsics, bufferRotationDeg: Double): SyntheticScene {
        val inv = SceneRotations.roll(-bufferRotationDeg)
        return SyntheticScene(w, h, inv * r, inv * t, kb, l, sw)
    }

    /** Independent pinhole projection of a world point: `pixel = K * ((R*X + t) / z)`. */
    private fun project(x: Vec3): Vec2 {
        val xc = r * x + t
        val u = k.matrix() * (xc * (1.0 / xc.z))
        return Vec2(u.x, u.y)
    }
}

/** Fixtures shared by the rectangle vanishing-point contract (node 01) and its consumers. */
object ContractScenes {
    /** Contract intrinsics `(fx, fy, cx, cy) = (1000, 1000, 1000, 750)`. */
    val K = CameraIntrinsics(fx = 1000.0, fy = 1000.0, cx = 1000.0, cy = 750.0)

    /** 2 x 1 m wall, 1 x 0.04 m stick, camera translation `(0, 0, 4)`, posed by [r]. */
    fun wall(r: Mat3, k: CameraIntrinsics = K, w: Double = 2.0, h: Double = 1.0): SyntheticScene =
        SyntheticScene(w = w, h = h, r = r, t = Vec3(0.0, 0.0, 4.0), k = k, l = 1.0, sw = 0.04)

    /** Node 03's half-pixel marking offsets for `[TL, TR, BR, BL]`, optionally negated. */
    fun halfPixelOffsets(sign: Double): List<Vec2> =
        listOf(Vec2(0.5, 0.0), Vec2(0.0, -0.5), Vec2(-0.5, 0.0), Vec2(0.0, 0.5)).map { it * sign }

    /** Adds per-corner pixel [offsets] to [corners]. */
    fun offset(corners: List<Vec2>, offsets: List<Vec2>): List<Vec2> = corners.zip(offsets) { c, o -> c + o }
}

/** Small sin/cos rotation-matrix builders for posing the synthetic camera. */
object SceneRotations {
    /** Right-handed yaw about the world Y axis (degrees). */
    fun yaw(deg: Double): Mat3 {
        val a = Math.toRadians(deg)
        return Mat3(
            cos(a), 0.0, sin(a),
            0.0, 1.0, 0.0,
            -sin(a), 0.0, cos(a),
        )
    }

    /** Right-handed pitch about the world X axis (degrees). */
    fun pitch(deg: Double): Mat3 {
        val a = Math.toRadians(deg)
        return Mat3(
            1.0, 0.0, 0.0,
            0.0, cos(a), -sin(a),
            0.0, sin(a), cos(a),
        )
    }

    /** Rotation about the optical (z) axis; in y-down image axes it turns content clockwise. */
    fun roll(deg: Double): Mat3 {
        val a = Math.toRadians(deg)
        return Mat3(
            cos(a), -sin(a), 0.0,
            sin(a), cos(a), 0.0,
            0.0, 0.0, 1.0,
        )
    }

    /** Combined yaw then pitch: `pitch(pitchDeg) * yaw(yawDeg)`. */
    fun yawPitch(yawDeg: Double, pitchDeg: Double): Mat3 = pitch(pitchDeg) * yaw(yawDeg)
}
