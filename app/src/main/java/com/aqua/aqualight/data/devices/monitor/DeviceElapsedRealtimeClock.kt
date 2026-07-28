package com.aqua.aqualight.data.devices.monitor

import android.os.SystemClock

/** Monotonic clock for presence freshness, retry backoff and timeout decisions. */
internal object DeviceElapsedRealtimeClock {

    fun nowMillis(): Long {
        return runCatching(SystemClock::elapsedRealtime)
            .getOrElse { System.nanoTime() / NANOS_PER_MILLISECOND }
    }

    private const val NANOS_PER_MILLISECOND = 1_000_000L
}
