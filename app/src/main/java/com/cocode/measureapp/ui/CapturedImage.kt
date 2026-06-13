package com.cocode.measureapp.ui

import android.graphics.Bitmap
import com.cocode.measureapp.model.CapturedScene

/** A captured photo plus its camera metadata, passed between flow steps. */
data class CapturedImage(val bitmap: Bitmap, val scene: CapturedScene)
