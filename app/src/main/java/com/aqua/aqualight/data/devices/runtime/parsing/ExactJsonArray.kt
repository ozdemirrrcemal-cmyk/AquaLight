package com.aqua.aqualight.data.devices.runtime.parsing

import org.json.JSONArray
import org.json.JSONObject

internal fun JSONArray.requiredObject(index: Int): JSONObject =
    get(index) as? JSONObject ?: error("Array item $index must be an object.")

internal fun JSONArray.requiredNonNegativeInts(): List<Int> = List(length()) { index ->
    val number = get(index) as? Number ?: error("Array item $index must be numeric.")
    val doubleValue = number.toDouble()
    val longValue = number.toLong()
    require(doubleValue.isFinite() && doubleValue == longValue.toDouble()) {
        "Array item $index must be an exact integer."
    }
    require(longValue in 0L..Int.MAX_VALUE.toLong()) {
        "Array item $index must be a non-negative Int."
    }
    longValue.toInt()
}

internal fun JSONArray.requiredBooleans(
    expectedSize: Int,
    label: String
): List<Boolean> {
    require(length() == expectedSize) {
        "$label must contain exactly $expectedSize booleans."
    }
    return List(length()) { index ->
        get(index) as? Boolean ?: error("$label[$index] must be a boolean.")
    }
}
