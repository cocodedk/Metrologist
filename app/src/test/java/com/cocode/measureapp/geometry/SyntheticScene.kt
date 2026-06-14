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

    /** Independent pinhole projection of a world point: `pixel = K * ((R*X + t) / z)`. */
    private fun project(x: Vec3): Vec2 {
        val xc = r * x + t
        val u = k.matrix() * (xc * (1.0 / xc.z))
        return Vec2(u.x, u.y)
    }
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

    /** Combined yaw then pitch: `pitch(pitchDeg) * yaw(yawDeg)`. */
    fun yawPitch(yawDeg: Double, pitchDeg: Double): Mat3 = pitch(pitchDeg) * yaw(yawDeg)
}
