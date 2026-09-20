package com.cocode.measureapp.geometry.frames

import com.cocode.measureapp.geometry.CameraIntrinsics
import com.cocode.measureapp.geometry.Vec2
import com.cocode.measureapp.geometry.Vec3

/**
 * A clockwise image rotation by a multiple of 90 degrees, as seen on screen (pixel and camera
 * axes x-right, y-down). Pixels use continuous coordinates over a `w x h` source image, so an
 * image corner maps exactly onto a corner of the rotated image.
 *
 * Camera vectors rotate about the optical axis with the pixels: a point at camera `(X, Y, Z)`
 * that projects to source pixel `p` projects to rotated pixel [pixel]`(p)` at [vector]`(X, Y, Z)`
 * through the rotated [intrinsics].
 */
enum class QuarterTurn(val degrees: Int) {
    R0(0), R90(90), R180(180), R270(270);

    /** True when source width becomes rotated height. */
    val swapsAxes: Boolean get() = this == R90 || this == R270

    fun inverse(): QuarterTurn = of(360 - degrees)!!

    /** This rotation followed by [next]. */
    fun then(next: QuarterTurn): QuarterTurn = of(degrees + next.degrees)!!

    fun pixel(p: Vec2, w: Double, h: Double): Vec2 = when (this) {
        R0 -> p
        R90 -> Vec2(h - p.y, p.x)
        R180 -> Vec2(w - p.x, h - p.y)
        R270 -> Vec2(p.y, w - p.x)
    }

    fun vector(v: Vec3): Vec3 = when (this) {
        R0 -> v
        R90 -> Vec3(-v.y, v.x, v.z)
        R180 -> Vec3(-v.x, -v.y, v.z)
        R270 -> Vec3(v.y, -v.x, v.z)
    }

    /** Intrinsics of the rotated image; focal lengths swap with the axes, the principal point moves. */
    fun intrinsics(k: CameraIntrinsics, w: Double, h: Double): CameraIntrinsics = when (this) {
        R0 -> k
        R90 -> CameraIntrinsics(fx = k.fy, fy = k.fx, cx = h - k.cy, cy = k.cx)
        R180 -> CameraIntrinsics(fx = k.fx, fy = k.fy, cx = w - k.cx, cy = h - k.cy)
        R270 -> CameraIntrinsics(fx = k.fy, fy = k.fx, cx = k.cy, cy = w - k.cx)
    }

    companion object {
        /** Normalizes any multiple of 90 (including negatives); null for anything else. */
        fun of(degrees: Int): QuarterTurn? {
            if (degrees % 90 != 0) return null
            val d = ((degrees % 360) + 360) % 360
            return entries.first { it.degrees == d }
        }
    }
}
