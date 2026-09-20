package com.cocode.measureapp.capture.gravity

/**
 * Bounded, thread-safe record of the most recent valid samples, oldest first, so the
 * capture path can pick the newest sample at or before exposure (node 02's policy).
 * Invalid readings are not stored; they only change [latest].
 */
class GravitySampleHistory(private val capacity: Int = 32, sensorPresent: Boolean) {
    private val samples = ArrayDeque<GravitySample.Available>()

    @Volatile
    var latest: GravitySample = GravitySampleDecoder.initial(sensorPresent)
        private set

    @Synchronized
    fun record(sample: GravitySample) {
        latest = sample
        if (sample !is GravitySample.Available) return
        samples.addLast(sample)
        while (samples.size > capacity) samples.removeFirst()
    }

    @Synchronized
    fun snapshot(): List<GravitySample.Available> = samples.toList()
}
