package com.aqua.aqualight.data.devices.runtime.modules.dosing

internal const val DOSING_MIN_COUNT = 0
internal const val DOSING_MIN_INDEX = 0
internal const val DOSING_UNAVAILABLE_INDEX = -1
internal const val DOSING_NON_NEGATIVE_LONG = 0L
internal const val DOSING_DEVICE_UPTIME_MAX_MS = 0xFFFF_FFFFL
internal const val DOSING_NORMALIZED_MIN = 0.0
internal const val DOSING_NORMALIZED_MAX = 1.0
internal const val DOSING_INACTIVE_VALUE = -1.0
internal const val DOSING_UNSET_CALIBRATION = -1L
internal const val DOSING_UNSET_RESERVOIR = -1.0
internal const val DOSING_PERCENT_MAX = 100.0
internal const val DOSING_VALUE_EPSILON = 0.0001
internal const val DOSING_WEEKDAY_COUNT = 7
internal const val DOSING_MILLISECONDS_PER_DAY = 86_400_000L

private const val DOSING_DEVICE_UPTIME_HALF_RANGE_MS = 0x8000_0000L
private const val DOSING_MILLISECONDS_PER_HOUR = 3_600_000L
private const val DOSING_MILLISECONDS_PER_MINUTE = 60_000L
private const val DOSING_MILLISECONDS_PER_SECOND = 1_000L
private const val DOSING_TIME_COMPONENT_WIDTH = 2
private const val DOSING_MILLISECOND_COMPONENT_WIDTH = 3

internal fun dosingValuesEquivalent(first: Double, second: Double): Boolean =
    kotlin.math.abs(first - second) <= DOSING_VALUE_EPSILON

/** Orders ESP32 `millis()` values within one connection generation, including wraparound. */
internal fun isNewerDosingSample(candidateMs: Long, currentMs: Long): Boolean {
    require(candidateMs in DOSING_NON_NEGATIVE_LONG..DOSING_DEVICE_UPTIME_MAX_MS)
    require(currentMs in DOSING_NON_NEGATIVE_LONG..DOSING_DEVICE_UPTIME_MAX_MS)
    if (candidateMs == currentMs) return false

    val forwardDistance = if (candidateMs > currentMs) {
        candidateMs - currentMs
    } else {
        DOSING_DEVICE_UPTIME_MAX_MS - currentMs + candidateMs + 1L
    }
    return forwardDistance in 1L until DOSING_DEVICE_UPTIME_HALF_RANGE_MS
}

/** Mirrors firmware `AqlTimeService::Millis2TimeStr`. */
internal fun dosingTimeText(milliseconds: Long): String {
    require(milliseconds in DOSING_NON_NEGATIVE_LONG..DOSING_DEVICE_UPTIME_MAX_MS)
    var remaining = milliseconds
    val hours = remaining / DOSING_MILLISECONDS_PER_HOUR
    remaining -= hours * DOSING_MILLISECONDS_PER_HOUR
    val minutes = remaining / DOSING_MILLISECONDS_PER_MINUTE
    remaining -= minutes * DOSING_MILLISECONDS_PER_MINUTE
    val seconds = remaining / DOSING_MILLISECONDS_PER_SECOND
    remaining -= seconds * DOSING_MILLISECONDS_PER_SECOND

    return buildString {
        append(hours.toString().padStart(DOSING_TIME_COMPONENT_WIDTH, '0'))
        append(':')
        append(minutes.toString().padStart(DOSING_TIME_COMPONENT_WIDTH, '0'))
        if (seconds > 0L || remaining > 0L) {
            append(':')
            append(seconds.toString().padStart(DOSING_TIME_COMPONENT_WIDTH, '0'))
            if (remaining > 0L) {
                append('.')
                append(remaining.toString().padStart(DOSING_MILLISECOND_COMPONENT_WIDTH, '0'))
            }
        }
    }
}
