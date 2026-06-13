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
 * 5 collinear, equally-spaced points (2 ends + 3 joints) along the wall's horizontal midline
 * (y = 0), of total length [l], so it forms 4 equal bands matching `StickProfile(l, 4)`.
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
 */
class SyntheticScene(
    val w: Double,
    val h: Double,
    val r: Mat3,
    val t: Vec3,
    val k: CameraIntrinsics,
    val l: Double,
) {
    /** Real wall corners in `[TL, TR, BR, BL]` clockwise order, projected to image pixels. */
    val cornerPixels: List<Vec2> = listOf(
        Vec3(-w / 2.0, -h / 2.0, 0.0), // TL
        Vec3(w / 2.0, -h / 2.0, 0.0),  // TR
        Vec3(w / 2.0, h / 2.0, 0.0),   // BR
        Vec3(-w / 2.0, h / 2.0, 0.0),  // BL
    ).map { project(it) }

    /** Stick: 5 equally-spaced collinear markers along y = 0 (2 ends + 3 joints), as pixels. */
    val stickPixels: List<Vec2> = (0..4).map { i ->
        val x = -l / 2.0 + l * i / 4.0
        project(Vec3(x, 0.0, 0.0))
    }

    /** Stick profile matching the 5 markers / 4 equal bands the scene emits. */
    val profile: StickProfile = StickProfile(totalLength = l, bandCount = 4)

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
