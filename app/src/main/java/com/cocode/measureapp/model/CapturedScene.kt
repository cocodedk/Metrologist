package com.cocode.measureapp.model

import com.cocode.measureapp.geometry.CameraIntrinsics
import com.cocode.measureapp.geometry.Vec3

/**
 * Camera data captured at the moment of the shot. Coordinates are in image pixels.
 * The captured bitmap itself is held by the UI layer; this is the pure metadata the
 * metrology engine needs.
 */
data class CapturedScene(
    val imageWidth: Int,
    val imageHeight: Int,
    val intrinsics: CameraIntrinsics,
    val gravity: Vec3,
)
