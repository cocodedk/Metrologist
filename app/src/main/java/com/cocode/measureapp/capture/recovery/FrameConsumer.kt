package com.cocode.measureapp.capture.recovery

/** User-facing message for a frame that arrived but could not be converted or described. */
const val CONVERSION_FAILED = "Could not read the captured photo. Try again."

/**
 * Converts an acquired [frame] (an `ImageProxy` on Android) and always closes it, whether
 * [convert] succeeds or throws. [convert] must copy every piece of metadata it needs from the
 * frame before returning. Any throw becomes a [CaptureOutcome.Failure], never a partial success.
 */
fun <T> consumeFrame(frame: AutoCloseable, convert: () -> T): CaptureOutcome<T> =
    try {
        CaptureOutcome.Success(convert())
    } catch (e: Throwable) {
        CaptureOutcome.Failure(CONVERSION_FAILED, e)
    } finally {
        runCatching { frame.close() }
    }
