package com.cocode.measureapp.capture.frames

import com.cocode.measureapp.capture.gravity.GravitySample
import com.cocode.measureapp.capture.gravity.SensorClock
import com.cocode.measureapp.capture.recovery.ExposureTimestamp
import com.cocode.measureapp.capture.recovery.TimestampSource
import com.cocode.measureapp.geometry.frames.GravityAlignmentReason

/** A gravity sample matched to one exposure, still in DEVICE axes, or why none matched. */
sealed interface ShotGravity {
    data class Matched(val sample: GravitySample.Available, val ageNanos: Long) : ShotGravity
    data class Unmatched(val reason: GravityAlignmentReason) : ShotGravity
}

/**
 * Initial association policy: the newest sample at or before exposure, at most
 * [MAX_AGE_NANOS] old. Timestamps are compared only when the camera declares a REALTIME
 * source and the sample is in `elapsedRealtimeNanos`; callback receipt and wall-clock time
 * are never substituted for exposure time.
 */
object ShotGravityMatcher {
    const val MAX_AGE_NANOS = 100_000_000L

    fun match(
        exposure: ExposureTimestamp,
        samples: List<GravitySample.Available>,
        maxAgeNanos: Long = MAX_AGE_NANOS,
    ): ShotGravity {
        val shot = exposure as? ExposureTimestamp.Available
            ?: return unmatched(GravityAlignmentReason.EXPOSURE_TIME_UNAVAILABLE)
        if (shot.source != TimestampSource.REALTIME) return unmatched(GravityAlignmentReason.CAMERA_CLOCK_NOT_REALTIME)
        val comparable = samples.filter { it.clock == SensorClock.ELAPSED_REALTIME_NANOS }
        if (comparable.isEmpty() && samples.isNotEmpty()) return unmatched(GravityAlignmentReason.CLOCK_MISMATCH)
        val newest = comparable.filter { it.timestampNanos <= shot.nanos }.maxByOrNull { it.timestampNanos }
            ?: return unmatched(GravityAlignmentReason.NO_SAMPLE_AT_OR_BEFORE_EXPOSURE)
        val age = shot.nanos - newest.timestampNanos
        if (age > maxAgeNanos) return unmatched(GravityAlignmentReason.STALE_SAMPLE)
        return ShotGravity.Matched(newest, age)
    }

    private fun unmatched(reason: GravityAlignmentReason) = ShotGravity.Unmatched(reason)
}
