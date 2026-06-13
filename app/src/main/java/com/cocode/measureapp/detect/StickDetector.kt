package com.cocode.measureapp.detect

import android.graphics.Bitmap
import com.cocode.measureapp.geometry.Vec2

/**
 * Detected stick points in image pixels, ordered end to end: 2 ends plus optionally the
 * 3 internal band joints (red-white-red-white).
 */
data class StickDetection(val points: List<Vec2>, val confidence: Double)

/** Auto-detection of the red-white-red-white reference stick. */
interface StickDetector {
    fun detect(image: Bitmap): StickDetection?
}

/**
 * Placeholder until the OpenCV pipeline lands (deferred: native libs are Android-only and
 * cannot be unit-tested headlessly). Returns null so the UI falls back to manual stick
 * marking, which is the accuracy backbone anyway.
 */
object DeferredStickDetector : StickDetector {
    override fun detect(image: Bitmap): StickDetection? = null
}
