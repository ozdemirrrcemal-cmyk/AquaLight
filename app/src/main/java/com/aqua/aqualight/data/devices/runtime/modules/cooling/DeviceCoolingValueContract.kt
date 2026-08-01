package com.aqua.aqualight.data.devices.runtime.modules.cooling

internal const val COOLING_MIN_COUNT = 0
internal const val COOLING_MIN_INDEX = 0
internal const val COOLING_UNAVAILABLE_INDEX = -1
internal const val COOLING_NON_NEGATIVE_LONG = 0L
internal const val COOLING_DEVICE_UPTIME_MAX_MS = 0xFFFF_FFFFL
internal const val COOLING_NORMALIZED_MIN = 0.0
internal const val COOLING_NORMALIZED_MAX = 1.0
internal const val COOLING_MANUAL_INACTIVE_VALUE = -1.0
internal const val COOLING_PERCENT_MIN = 0.0
internal const val COOLING_PERCENT_MAX = 100.0
internal const val COOLING_MANUAL_INACTIVE_PERCENT = -100.0
internal const val COOLING_PERCENT_SCALE = 100.0
internal const val COOLING_VALUE_EPSILON = 0.0001
internal const val COOLING_MIN_VALID_TEMPERATURE_C = -100.0
internal const val COOLING_MAX_VALID_TEMPERATURE_C = 200.0

private const val COOLING_DEVICE_UPTIME_HALF_RANGE_MS = 0x8000_0000L

internal fun coolingValuesEquivalent(first: Double, second: Double): Boolean =
    kotlin.math.abs(first - second) <= COOLING_VALUE_EPSILON

/**
 * Orders firmware `millis()` samples inside one runtime connection.
 *
 * ESP32 `unsigned long` uptime wraps at 2^32. Runtime state is cleared whenever a new
 * connection generation starts, so the half-range rule deterministically distinguishes a
 * forward sample (including wraparound) from a delayed older event.
 */
internal fun isNewerCoolingSample(candidateMs: Long, currentMs: Long): Boolean {
    require(candidateMs in COOLING_NON_NEGATIVE_LONG..COOLING_DEVICE_UPTIME_MAX_MS)
    require(currentMs in COOLING_NON_NEGATIVE_LONG..COOLING_DEVICE_UPTIME_MAX_MS)
    if (candidateMs == currentMs) return false

    val forwardDistance = if (candidateMs > currentMs) {
        candidateMs - currentMs
    } else {
        COOLING_DEVICE_UPTIME_MAX_MS - currentMs + candidateMs + 1L
    }
    return forwardDistance in 1L until COOLING_DEVICE_UPTIME_HALF_RANGE_MS
}
