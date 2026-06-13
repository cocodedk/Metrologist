package com.cocode.measureapp.detect

import android.graphics.Bitmap
import com.cocode.measureapp.stick.StickPoints

/** Auto-detection of the red-white-red-white reference stick. */
interface StickDetector {
    /** Returns the ordered stick points + confidence, or null if no stick was found. */
    fun detect(image: Bitmap): StickPoints?
}

/**
 * Placeholder that always returns null, so the UI falls back to manual stick marking.
 * Used when OpenCV is unavailable.
 */
object DeferredStickDetector : StickDetector {
    override fun detect(image: Bitmap): StickPoints? = null
}
