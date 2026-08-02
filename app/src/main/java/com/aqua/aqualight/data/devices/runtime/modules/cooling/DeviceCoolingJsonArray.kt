package com.aqua.aqualight.data.devices.runtime.modules.cooling

import org.json.JSONArray
import org.json.JSONObject

internal fun JSONArray.requireCoolingObject(index: Int): JSONObject =
    get(index) as? JSONObject ?: error("[$index] must be a JSON object.")

internal fun JSONArray.requireCoolingInt(index: Int, minimum: Int = Int.MIN_VALUE): Int {
    val value = get(index) as? Number ?: error("[$index] must be an integer.")
    val asDouble = value.toDouble()
    val asLong = value.toLong()
    require(asDouble.isFinite() && asDouble == asLong.toDouble()) {
        "[$index] must be an integer."
    }
    require(asLong >= minimum.toLong() && asLong <= Int.MAX_VALUE.toLong()) {
        "[$index] is outside its supported range."
    }
    return asLong.toInt()
}
