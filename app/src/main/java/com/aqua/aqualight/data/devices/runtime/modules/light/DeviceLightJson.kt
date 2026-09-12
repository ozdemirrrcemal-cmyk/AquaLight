package com.aqua.aqualight.data.devices.runtime.modules.light

import org.json.JSONArray
import org.json.JSONObject

internal fun JSONObject.requireLightKeys(expected: Set<String>, label: String) {
    val actual = keys().asSequence().toSet()
    require(actual == expected) {
        "$label keys differ from the firmware contract; expected=$expected actual=$actual"
    }
}

internal fun JSONObject.requireLightObject(key: String): JSONObject =
    get(key) as? JSONObject ?: error("$key must be a JSON object.")

internal fun JSONObject.requireNullableLightObject(key: String): JSONObject? = when (val value = get(key)) {
    JSONObject.NULL -> null
    is JSONObject -> value
    else -> error("$key must be a JSON object or null.")
}

internal fun JSONObject.requireLightArray(key: String): JSONArray =
    get(key) as? JSONArray ?: error("$key must be a JSON array.")

internal fun JSONArray.requireLightObject(index: Int): JSONObject =
    get(index) as? JSONObject ?: error("[$index] must be a JSON object.")

internal fun JSONArray.requireLightArray(index: Int): JSONArray =
    get(index) as? JSONArray ?: error("[$index] must be a JSON array.")

internal fun JSONObject.requireLightText(key: String): String {
    val value = get(key) as? String ?: error("$key must be a string.")
    return requireExactLightText(key, value)
}

internal fun JSONObject.requireNullableLightText(key: String): String? = when (val value = get(key)) {
    JSONObject.NULL -> null
    is String -> requireExactLightText(key, value)
    else -> error("$key must be a string or null.")
}

private fun requireExactLightText(key: String, value: String): String {
    require(value.isNotEmpty()) { "$key must not be empty." }
    require(!value.first().isWhitespace() && !value.last().isWhitespace()) {
        "$key must not contain surrounding whitespace."
    }
    require(value.none(Char::isISOControl)) { "$key must not contain control characters." }
    return value
}

internal fun JSONObject.requireLightBoolean(key: String): Boolean =
    get(key) as? Boolean ?: error("$key must be a boolean.")

internal fun JSONObject.requireNullableLightBoolean(key: String): Boolean? = when (val value = get(key)) {
    JSONObject.NULL -> null
    is Boolean -> value
    else -> error("$key must be a boolean or null.")
}

internal fun JSONObject.requireLightInt(
    key: String,
    minimum: Int = Int.MIN_VALUE,
    maximum: Int = Int.MAX_VALUE
): Int = requireLightInteger(get(key), key, minimum.toLong(), maximum.toLong()).toInt()

internal fun JSONObject.requireNullableLightInt(
    key: String,
    minimum: Int = Int.MIN_VALUE,
    maximum: Int = Int.MAX_VALUE
): Int? = when (val value = get(key)) {
    JSONObject.NULL -> null
    else -> requireLightInteger(value, key, minimum.toLong(), maximum.toLong()).toInt()
}

internal fun JSONObject.requireLightLong(
    key: String,
    minimum: Long = Long.MIN_VALUE,
    maximum: Long = Long.MAX_VALUE
): Long = requireLightInteger(get(key), key, minimum, maximum)

internal fun JSONObject.requireNullableLightLong(
    key: String,
    minimum: Long = Long.MIN_VALUE,
    maximum: Long = Long.MAX_VALUE
): Long? = when (val value = get(key)) {
    JSONObject.NULL -> null
    else -> requireLightInteger(value, key, minimum, maximum)
}

private fun requireLightInteger(value: Any, key: String, minimum: Long, maximum: Long): Long {
    val number = value as? Number ?: error("$key must be an integer.")
    val asDouble = number.toDouble()
    val asLong = number.toLong()
    require(asDouble.isFinite() && asDouble == asLong.toDouble()) { "$key must be an integer." }
    require(asLong in minimum..maximum) { "$key is outside its supported range." }
    return asLong
}

internal fun JSONObject.requireLightDouble(
    key: String,
    minimum: Double = -Double.MAX_VALUE,
    maximum: Double = Double.MAX_VALUE
): Double = requireLightNumber(get(key), key, minimum, maximum)

internal fun JSONObject.requireNullableLightDouble(
    key: String,
    minimum: Double = -Double.MAX_VALUE,
    maximum: Double = Double.MAX_VALUE
): Double? = when (val value = get(key)) {
    JSONObject.NULL -> null
    else -> requireLightNumber(value, key, minimum, maximum)
}

private fun requireLightNumber(value: Any, key: String, minimum: Double, maximum: Double): Double {
    val number = value as? Number ?: error("$key must be numeric.")
    return number.toDouble().also {
        require(it.isFinite()) { "$key must be finite." }
        require(it in minimum..maximum) { "$key is outside its supported range." }
    }
}

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
