package com.cocode.measureapp.capture.recovery

/** Terminal outcome of one accepted capture request. */
sealed interface CaptureOutcome<out T> {
    data class Success<T>(val payload: T) : CaptureOutcome<T>
    data class Failure(val message: String, val cause: Throwable? = null) : CaptureOutcome<Nothing>
    data object Cancelled : CaptureOutcome<Nothing>
}

/** Observable capture state; [canCapture] is what enables the Capture button. */
data class CaptureState(
    val busy: Boolean = false,
    val error: String? = null,
    val disposed: Boolean = false,
) {
    fun canCapture(permissionGranted: Boolean, cameraReady: Boolean): Boolean =
        permissionGranted && cameraReady && !busy && !disposed
}

/**
 * Capture request lifecycle for one screen instance. Every accepted request ends in exactly
 * one [complete]; busy clears for success, failure and cancellation. Results for a superseded
 * request or a disposed screen are rejected and handed to [discard] so they don't leak.
 * A new screen instance (e.g. Retake) creates a new controller, so no busy flag is inherited.
 *
 * [requestIdOf] reads the shot identity carried by a payload; a success whose identity does
 * not match its request is a failure, so image and metadata never mix across requests.
 */
class CaptureController<T>(
    private val discard: (T) -> Unit = {},
    private val requestIdOf: (T) -> Int? = { null },
    private val onStateChanged: (CaptureState) -> Unit = {},
) {
    private var nextId = 0
    private var active: Int? = null
    private val inFlight = mutableSetOf<Int>()

    var state = CaptureState()
        private set(value) { field = value; onStateChanged(value) }

    /** True once disposed with no request still owed a callback: safe to release executors. */
    val released: Boolean
        @Synchronized get() = state.disposed && inFlight.isEmpty()

    /** Accepts a request, or returns null while one is active or after disposal. */
    @Synchronized
    fun begin(): Int? {
        if (state.busy || state.disposed) return null
        val id = ++nextId
        active = id
        inFlight += id
        state = state.copy(busy = true, error = null)
        return id
    }

    /**
     * Begins a request and runs [start] with its id. A synchronous throw from [start] completes
     * the request as a failure (no success is fabricated). Returns the id, or null if refused.
     */
    fun launch(start: (Int) -> Unit): Int? {
        val id = begin() ?: return null
        try {
            start(id)
        } catch (e: Throwable) {
            complete(id, CaptureOutcome.Failure(START_FAILED, e))
        }
        return id
    }

    /**
     * Records the terminal outcome for [id]. Returns the payload to deliver only for a
     * success of the active request on a live screen; everything else returns null.
     */
    @Synchronized
    fun complete(id: Int, outcome: CaptureOutcome<T>): T? {
        val owed = inFlight.remove(id)
        if (!owed || state.disposed || id != active) {
            if (outcome is CaptureOutcome.Success) discard(outcome.payload)
            return null
        }
        active = null
        return when (outcome) {
            is CaptureOutcome.Success -> {
                val shot = requestIdOf(outcome.payload)
                if (shot != null && shot != id) {
                    discard(outcome.payload)
                    state = state.copy(busy = false, error = MISMATCHED)
                    null
                } else {
                    state = state.copy(busy = false, error = null)
                    outcome.payload
                }
            }
            is CaptureOutcome.Failure -> { state = state.copy(busy = false, error = outcome.message); null }
            CaptureOutcome.Cancelled -> { state = state.copy(busy = false); null }
        }
    }

    /** The owning screen left: later results are ignored and discarded. */
    @Synchronized
    fun dispose() {
        active = null
        state = state.copy(busy = false, disposed = true)
    }

    companion object {
        const val START_FAILED = "Could not start the capture. Try again."
        const val MISMATCHED = "Captured photo did not match its request. Try again."
    }
}
