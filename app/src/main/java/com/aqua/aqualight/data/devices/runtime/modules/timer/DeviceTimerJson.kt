package com.aqua.aqualight.data.devices.runtime.modules.timer

import org.json.JSONArray
import org.json.JSONObject

internal fun JSONObject.requireTimerKeys(expected: Set<String>, label: String) {
    val actual = keys().asSequence().toSet()
    require(actual == expected) {
        "$label keys differ from the firmware contract; expected=$expected actual=$actual"
    }
}

internal fun JSONObject.requireTimerKeys(
    required: Set<String>,
    optional: Set<String>,
    label: String
) {
    val actual = keys().asSequence().toSet()
    require(actual.containsAll(required) && actual.all { key -> key in required || key in optional }) {
        "$label keys differ from the firmware contract; " +
            "required=$required optional=$optional actual=$actual"
    }
}

internal fun JSONObject.requireTimerObject(key: String): JSONObject =
    get(key) as? JSONObject ?: error("$key must be a JSON object.")

internal fun JSONObject.requireTimerArray(key: String): JSONArray =
    get(key) as? JSONArray ?: error("$key must be a JSON array.")

internal fun JSONObject.requireTimerText(key: String, allowEmpty: Boolean = false): String {
    val value = get(key) as? String ?: error("$key must be a string.")
    require(allowEmpty || value.isNotEmpty()) { "$key must not be empty." }
    require(value.isEmpty() || (!value.first().isWhitespace() && !value.last().isWhitespace())) {
        "$key must not contain surrounding whitespace."
    }
    require(value.none(Char::isISOControl)) { "$key must not contain control characters." }
    return value
}

internal fun JSONObject.optionalTimerText(key: String): String? =
    if (has(key)) requireTimerText(key) else null

internal fun JSONObject.requireTimerBoolean(key: String): Boolean =
    get(key) as? Boolean ?: error("$key must be a boolean.")

internal fun JSONObject.requireTimerInt(
    key: String,
    minimum: Int = Int.MIN_VALUE,
    maximum: Int = Int.MAX_VALUE
): Int {
    val value = get(key) as? Number ?: error("$key must be an integer.")
    val asDouble = value.toDouble()
    val asLong = value.toLong()
    require(asDouble.isFinite() && asDouble == asLong.toDouble()) {
        "$key must be an integer."
    }
    require(asLong in minimum.toLong()..maximum.toLong()) {
        "$key is outside its supported range."
    }
    return asLong.toInt()
}

internal fun JSONObject.requireTimerLong(
    key: String,
    minimum: Long = Long.MIN_VALUE,
    maximum: Long = Long.MAX_VALUE
): Long {
    val value = get(key) as? Number ?: error("$key must be an integer.")
    val asDouble = value.toDouble()
    val asLong = value.toLong()
    require(asDouble.isFinite() && asDouble == asLong.toDouble()) {
        "$key must be an integer."
    }
    require(asLong in minimum..maximum) { "$key is outside its supported range." }
    return asLong
}

internal fun JSONObject.requireTimerDouble(
    key: String,
    minimum: Double = -Double.MAX_VALUE,
    maximum: Double = Double.MAX_VALUE
): Double {
    val value = get(key) as? Number ?: error("$key must be numeric.")
    return value.toDouble().also { number ->
        require(number.isFinite()) { "$key must be finite." }
        require(number in minimum..maximum) { "$key is outside its supported range." }
    }
}
