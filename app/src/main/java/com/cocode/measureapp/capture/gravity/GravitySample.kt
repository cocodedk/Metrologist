package com.cocode.measureapp.capture.gravity

import com.cocode.measureapp.geometry.Vec3

/** Clock domain of a sensor timestamp. Android sensor events use `elapsedRealtimeNanos`. */
enum class SensorClock { ELAPSED_REALTIME_NANOS }

/** Why no physical-down vector is available. None of these may be shown as "level". */
enum class GravityUnavailableReason { NO_SENSOR, NO_SAMPLE_YET, INVALID_VECTOR }

/**
 * One gravity reading, decoded into physical direction but NOT into a camera frame.
 *
 * Device axes: x toward the natural right edge, y toward the phone's natural top,
 * z out of the screen. Converting to camera/image axes (node 02) or display/view axes
 * (level overlay) is the consumer's job; neither may invert the direction again.
 */
sealed interface GravitySample {
    /**
     * [down] is a finite unit vector along physical down in device axes. [timestampNanos]
     * is the original sensor-event time (not callback receipt) in [clock].
     */
    data class Available(
        val down: Vec3,
        val timestampNanos: Long,
        val clock: SensorClock = SensorClock.ELAPSED_REALTIME_NANOS,
    ) : GravitySample

    data class Unavailable(val reason: GravityUnavailableReason) : GravitySample
}

/** Decodes raw `TYPE_GRAVITY` values; pure Kotlin so the adapter's exact math is unit-tested. */
object GravitySampleDecoder {
    /** Below this magnitude (m/s^2) a reading carries no usable direction. */
    const val MIN_MAGNITUDE = 1e-6

    /**
     * Android reports the stationary acceleration reaction, which points physically UP:
     * an upright phone reads `(0, +g, 0)`. Physical down is therefore the negated,
     * normalized sample: `(0, -1, 0)` for that upright phone.
     */
    fun decode(values: FloatArray, timestampNanos: Long): GravitySample {
        if (values.size < 3) return invalid()
        val x = values[0].toDouble(); val y = values[1].toDouble(); val z = values[2].toDouble()
        if (!x.isFinite() || !y.isFinite() || !z.isFinite()) return invalid()
        val up = Vec3(x, y, z)
        val n = up.norm()
        if (!n.isFinite() || n < MIN_MAGNITUDE) return invalid()
        return GravitySample.Available(up * (-1.0 / n), timestampNanos)
    }

    /** Initial state before any sensor callback: absent sensor vs. not-yet-delivered. */
    fun initial(sensorPresent: Boolean): GravitySample = GravitySample.Unavailable(
        if (sensorPresent) GravityUnavailableReason.NO_SAMPLE_YET else GravityUnavailableReason.NO_SENSOR,
    )

    private fun invalid() = GravitySample.Unavailable(GravityUnavailableReason.INVALID_VECTOR)
}

/**
 * Legacy bridge for the capture path until node 02 applies the real device-to-image
 * transform: back camera held in upright natural portrait, camera axes x-right, y-down,
 * z-forward = `(dx, -dy, -dz)`. Returns null when no valid sample exists.
 */
fun GravitySample.portraitCameraDownOrNull(): Vec3? = when (this) {
    is GravitySample.Available -> Vec3(down.x, -down.y, -down.z)
    is GravitySample.Unavailable -> null
}
