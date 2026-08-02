package com.aqua.aqualight.data.devices.runtime.modules.timer

internal const val TIMER_MIN_COUNT = 0
internal const val TIMER_MIN_INDEX = 0
internal const val TIMER_UNAVAILABLE_INDEX = -1
internal const val TIMER_NON_NEGATIVE_LONG = 0L
internal const val TIMER_DEVICE_UPTIME_MAX_MS = 0xFFFF_FFFFL
internal const val TIMER_NORMALIZED_MIN = 0.0
internal const val TIMER_NORMALIZED_MAX = 1.0
internal const val TIMER_INACTIVE_VALUE = -1.0
internal const val TIMER_STANDALONE_AMOUNT_ML = -1.0
internal const val TIMER_VALUE_EPSILON = 0.0001
internal const val TIMER_WEEKDAY_COUNT = 7
internal const val TIMER_MILLISECONDS_PER_DAY = 86_400_000L

private const val TIMER_DEVICE_UPTIME_HALF_RANGE_MS = 0x8000_0000L
private const val TIMER_MILLISECONDS_PER_HOUR = 3_600_000L
private const val TIMER_MILLISECONDS_PER_MINUTE = 60_000L
private const val TIMER_MILLISECONDS_PER_SECOND = 1_000L
private const val TIMER_TIME_COMPONENT_WIDTH = 2
private const val TIMER_MILLISECOND_COMPONENT_WIDTH = 3

internal fun timerValuesEquivalent(first: Double, second: Double): Boolean =
    kotlin.math.abs(first - second) <= TIMER_VALUE_EPSILON

/** Orders ESP32 `millis()` values within one connection generation, including wraparound. */
internal fun isNewerTimerSample(candidateMs: Long, currentMs: Long): Boolean {
    require(candidateMs in TIMER_NON_NEGATIVE_LONG..TIMER_DEVICE_UPTIME_MAX_MS)
    require(currentMs in TIMER_NON_NEGATIVE_LONG..TIMER_DEVICE_UPTIME_MAX_MS)
    if (candidateMs == currentMs) return false

    val forwardDistance = if (candidateMs > currentMs) {
        candidateMs - currentMs
    } else {
        TIMER_DEVICE_UPTIME_MAX_MS - currentMs + candidateMs + 1L
    }
    return forwardDistance in 1L until TIMER_DEVICE_UPTIME_HALF_RANGE_MS
}

/** Mirrors firmware `AqlTimeService::Millis2TimeStr`. */
internal fun timerTimeText(milliseconds: Long): String {
    require(milliseconds in TIMER_NON_NEGATIVE_LONG..TIMER_DEVICE_UPTIME_MAX_MS)
    var remaining = milliseconds
    val hours = remaining / TIMER_MILLISECONDS_PER_HOUR
    remaining -= hours * TIMER_MILLISECONDS_PER_HOUR
    val minutes = remaining / TIMER_MILLISECONDS_PER_MINUTE
    remaining -= minutes * TIMER_MILLISECONDS_PER_MINUTE
    val seconds = remaining / TIMER_MILLISECONDS_PER_SECOND
    remaining -= seconds * TIMER_MILLISECONDS_PER_SECOND

    return buildString {
        append(hours.toString().padStart(TIMER_TIME_COMPONENT_WIDTH, '0'))
        append(':')
        append(minutes.toString().padStart(TIMER_TIME_COMPONENT_WIDTH, '0'))
        if (seconds > 0L || remaining > 0L) {
            append(':')
            append(seconds.toString().padStart(TIMER_TIME_COMPONENT_WIDTH, '0'))
            if (remaining > 0L) {
                append('.')
                append(remaining.toString().padStart(TIMER_MILLISECOND_COMPONENT_WIDTH, '0'))
            }
        }
    }
}
