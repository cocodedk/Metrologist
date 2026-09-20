package com.cocode.measureapp.ui

import android.graphics.Bitmap
import com.cocode.measureapp.capture.recovery.CaptureMetadata
import com.cocode.measureapp.model.CapturedScene

/**
 * A captured photo plus its camera metadata, passed between flow steps. [metadata] was copied
 * from the same shot as [bitmap] (matching `requestId`) before the camera proxy was closed.
 */
data class CapturedImage(val bitmap: Bitmap, val scene: CapturedScene, val metadata: CaptureMetadata)
