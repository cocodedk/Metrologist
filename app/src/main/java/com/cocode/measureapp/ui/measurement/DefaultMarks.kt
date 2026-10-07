package com.cocode.measureapp.ui.measurement

import com.cocode.measureapp.geometry.Vec2

/** Where the Object box starts on a photo of [w] x [h] pixels: the middle 60%. */
internal fun defaultObjectCorners(w: Double, h: Double) = listOf(
    Vec2(w * 0.20, h * 0.20), Vec2(w * 0.80, h * 0.20),
    Vec2(w * 0.80, h * 0.80), Vec2(w * 0.20, h * 0.80),
)

/** Where the Stick box starts, low in the middle of the photo, until the stick is found. */
internal fun defaultStickBox(w: Double, h: Double) = listOf(
    Vec2(w * 0.35, h * 0.64), Vec2(w * 0.65, h * 0.64),
    Vec2(w * 0.65, h * 0.72), Vec2(w * 0.35, h * 0.72),
)
