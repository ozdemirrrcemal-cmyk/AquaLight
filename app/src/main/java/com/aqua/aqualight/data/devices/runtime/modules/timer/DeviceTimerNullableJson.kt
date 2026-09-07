package com.aqua.aqualight.data.devices.runtime.modules.timer

import org.json.JSONObject

internal fun JSONObject.optionalTimerText(key: String): String? =
    if (has(key)) requireTimerText(key) else null

internal fun JSONObject.requireNullableTimerText(key: String): String? {
    require(has(key)) { "$key is required by the firmware contract." }
    return if (isNull(key)) null else requireTimerText(key)
}

internal fun JSONObject.requireNullableTimerLong(
    key: String,
    minimum: Long = Long.MIN_VALUE,
    maximum: Long = Long.MAX_VALUE
): Long? {
    require(has(key)) { "$key is required by the firmware contract." }
    return if (isNull(key)) null else requireTimerLong(key, minimum, maximum)
}

internal fun JSONObject.requireNullableTimerInt(
    key: String,
    minimum: Int = Int.MIN_VALUE,
    maximum: Int = Int.MAX_VALUE
): Int? {
    require(has(key)) { "$key is required by the firmware contract." }
    return if (isNull(key)) null else requireTimerInt(key, minimum, maximum)
}
