package com.aqua.aqualight.data.devices.runtime.modules.light

import org.json.JSONArray
import org.json.JSONObject

internal fun JSONArray.requireLightObject(index: Int): JSONObject =
    get(index) as? JSONObject ?: error("[$index] must be a JSON object.")

internal fun JSONArray.requireLightArray(index: Int): JSONArray =
    get(index) as? JSONArray ?: error("[$index] must be a JSON array.")

internal fun JSONArray.requireLightLong(
    index: Int,
    minimum: Long = Long.MIN_VALUE,
    maximum: Long = Long.MAX_VALUE
): Long = requireLightInteger(get(index), "[$index]", minimum, maximum)

internal fun JSONArray.requireLightInt(
    index: Int,
    minimum: Int = Int.MIN_VALUE,
    maximum: Int = Int.MAX_VALUE
): Int = requireLightInteger(get(index), "[$index]", minimum.toLong(), maximum.toLong()).toInt()

internal fun JSONArray.requireLightText(index: Int): String {
    val value = get(index) as? String ?: error("[$index] must be a string.")
    return requireExactLightText("[$index]", value)
}

internal fun JSONArray.toLightLongList(
    minimum: Long = Long.MIN_VALUE,
    maximum: Long = Long.MAX_VALUE
): List<Long> = List(length()) { index -> requireLightLong(index, minimum, maximum) }
