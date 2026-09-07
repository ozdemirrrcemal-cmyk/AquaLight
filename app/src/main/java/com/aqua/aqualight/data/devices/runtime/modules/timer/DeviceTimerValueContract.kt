package com.aqua.aqualight.data.devices.runtime.modules.timer

internal const val TIMER_MIN_COUNT = 0
internal const val TIMER_MIN_INDEX = 0
internal const val TIMER_UNAVAILABLE_INDEX = -1
internal const val TIMER_NON_NEGATIVE_LONG = 0L
internal const val TIMER_NORMALIZED_MIN = 0.0
internal const val TIMER_NORMALIZED_MAX = 1.0
internal const val TIMER_INACTIVE_VALUE = -1.0
internal const val TIMER_WEEKDAY_COUNT = 7

private const val TIMER_UINT32_HALF_RANGE = 0x8000_0000L
private const val TIMER_MILLISECONDS_PER_HOUR = 3_600_000L
private const val TIMER_MILLISECONDS_PER_MINUTE = 60_000L
private const val TIMER_MILLISECONDS_PER_SECOND = 1_000L
private const val TIMER_TIME_COMPONENT_WIDTH = 2
private const val TIMER_MILLISECOND_COMPONENT_WIDTH = 3
private const val TIMER_ASCII_CONTROL_END = 0x20
private const val TIMER_ASCII_DELETE = 0x7F
private const val TIMER_UNSIGNED_BYTE_MASK = 0xFF

/** Orders ESP32 unsigned 32-bit counters, including wraparound. */
internal fun isNewerTimerCounter(candidate: Long, current: Long): Boolean {
    require(candidate in TIMER_NON_NEGATIVE_LONG..DeviceTimerRuntimeContract.Limit.UINT32_MAX)
    require(current in TIMER_NON_NEGATIVE_LONG..DeviceTimerRuntimeContract.Limit.UINT32_MAX)
    if (candidate == current) return false

    val forwardDistance = if (candidate > current) {
        candidate - current
    } else {
        DeviceTimerRuntimeContract.Limit.UINT32_MAX - current + candidate + 1L
    }
    return forwardDistance in 1L until TIMER_UINT32_HALF_RANGE
}

internal fun nextTimerSequence(current: Long): Long =
    if (current == DeviceTimerRuntimeContract.Limit.UINT32_MAX) 1L else current + 1L

internal fun timerScheduleBoundaryMillis(hour: Int, minute: Int): Long {
    require(hour in 0..23) { "Timer hour must be inside 00..23." }
    require(minute in 0..59) { "Timer minute must be inside 00..59." }
    return (hour * 60L + minute) * TIMER_MILLISECONDS_PER_MINUTE
}

internal fun isTimerScheduleBoundary(milliseconds: Long): Boolean =
    milliseconds in TIMER_NON_NEGATIVE_LONG until
        DeviceTimerRuntimeContract.Limit.DAY_MILLISECONDS &&
        milliseconds % DeviceTimerRuntimeContract.Limit.SCHEDULE_BOUNDARY_GRANULARITY_MS == 0L

/** Mirrors firmware `AqlTimeService::Millis2TimeStr`. */
internal fun timerTimeText(milliseconds: Long): String {
    require(milliseconds in TIMER_NON_NEGATIVE_LONG..DeviceTimerRuntimeContract.Limit.UINT32_MAX)
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

/** Mirrors Arduino `String::trim()` without stripping valid non-ASCII name bytes. */
internal fun String.trimTimerAsciiWhitespace(): String = trim { character ->
    character == ' ' || character in '\t'..'\r'
}

/** Mirrors firmware validation of C0 control bytes and ASCII DEL. */
internal fun String.hasTimerForbiddenControlBytes(): Boolean =
    toByteArray(Charsets.UTF_8).any { byte ->
        val unsigned = byte.toInt() and TIMER_UNSIGNED_BYTE_MASK
        unsigned < TIMER_ASCII_CONTROL_END || unsigned == TIMER_ASCII_DELETE
    }

internal fun normalizeTimerChannelKey(channelKey: String): String =
    channelKey.trimTimerAsciiWhitespace().lowercase().also { normalized ->
        require(normalized.isNotEmpty()) { "channelKey must not be blank." }
        require(normalized != "-" && normalized != "none") {
            "channelKey must identify a configured Timer channel."
        }
        require(!normalized.hasTimerForbiddenControlBytes()) {
            "channelKey must not contain control characters."
        }
        require(
            normalized.toByteArray(Charsets.UTF_8).size <=
                DeviceTimerRuntimeContract.Limit.MAX_CHANNEL_KEY_BYTES
        ) { "channelKey exceeds the firmware byte limit." }
    }
