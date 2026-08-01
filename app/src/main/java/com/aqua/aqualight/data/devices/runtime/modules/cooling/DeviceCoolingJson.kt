package com.aqua.aqualight.data.devices.runtime.modules.cooling

import org.json.JSONArray
import org.json.JSONObject

internal fun JSONObject.requireCoolingKeys(expected: Set<String>, label: String) {
    val actual = keys().asSequence().toSet()
    require(actual == expected) {
        "$label keys differ from the firmware contract; expected=$expected actual=$actual"
    }
}

internal fun JSONObject.requireCoolingObject(key: String): JSONObject =
    get(key) as? JSONObject ?: error("$key must be a JSON object.")

internal fun JSONObject.requireCoolingArray(key: String): JSONArray =
    get(key) as? JSONArray ?: error("$key must be a JSON array.")

internal fun JSONArray.requireCoolingObject(index: Int): JSONObject =
    get(index) as? JSONObject ?: error("[$index] must be a JSON object.")

internal fun JSONObject.requireCoolingText(key: String): String {
    val value = get(key) as? String ?: error("$key must be a string.")
    require(value.isNotEmpty()) { "$key must not be empty." }
    require(!value.first().isWhitespace() && !value.last().isWhitespace()) {
        "$key must not contain surrounding whitespace."
    }
    require(value.none(Char::isISOControl)) { "$key must not contain control characters." }
    return value
}

internal fun JSONObject.requireCoolingBoolean(key: String): Boolean =
    get(key) as? Boolean ?: error("$key must be a boolean.")

internal fun JSONObject.requireCoolingInt(
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

internal fun JSONObject.requireCoolingLong(
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

internal fun JSONObject.requireCoolingDouble(
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

internal fun JSONObject.requireCoolingNullableDouble(
    key: String,
    minimum: Double = -Double.MAX_VALUE,
    maximum: Double = Double.MAX_VALUE
): Double? {
    val value = get(key)
    if (value === JSONObject.NULL) return null
    val number = value as? Number ?: error("$key must be numeric or null.")
    return number.toDouble().also { parsed ->
        require(parsed.isFinite()) { "$key must be finite." }
        require(parsed in minimum..maximum) { "$key is outside its supported range." }
    }
}

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
