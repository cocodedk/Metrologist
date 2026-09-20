package com.cocode.measureapp.geometry

/** Pinhole camera intrinsics, in pixels. */
data class CameraIntrinsics(
    val fx: Double,
    val fy: Double,
    val cx: Double,
    val cy: Double,
) {
    fun matrix() = Mat3(
        fx, 0.0, cx,
        0.0, fy, cy,
        0.0, 0.0, 1.0,
    )

    fun inverseMatrix() = matrix().inverse()

    /** Finite positive focal lengths and a finite principal point: safe to back-project with. */
    fun isUsable(): Boolean =
        fx.isFinite() && fy.isFinite() && fx > 0.0 && fy > 0.0 && cx.isFinite() && cy.isFinite()
}
