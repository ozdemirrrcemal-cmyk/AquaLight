package com.aqua.aqualight.data.devices.dosing.v1

import org.json.JSONObject

internal fun JSONObject.requireDosingString(
    key: String,
    allowEmpty: Boolean = false
): String {
    val value = get(key) as? String ?: error("$key must be a string.")
    require(allowEmpty || value.isNotEmpty()) { "$key must not be empty." }
    require(value.none(Char::isFirmwareRejectedControl)) {
        "$key must not contain firmware-rejected control characters."
    }
    return value
}

internal fun JSONObject.requireDosingNullableString(key: String): String? {
    val value = get(key)
    if (value === JSONObject.NULL) return null
    return (value as? String)?.also { text ->
        require(text.none(Char::isFirmwareRejectedControl)) {
            "$key must not contain firmware-rejected control characters."
        }
    } ?: error("$key must be a string or null.")
}

internal fun JSONObject.requireDosingBoolean(key: String): Boolean =
    get(key) as? Boolean ?: error("$key must be a boolean.")

internal fun JSONObject.requireDosingLong(
    key: String,
    minimum: Long = Long.MIN_VALUE,
    maximum: Long = Long.MAX_VALUE
): Long {
    val number = get(key) as? Number ?: error("$key must be an integer.")
    val doubleValue = number.toDouble()
    val longValue = number.toLong()
    require(doubleValue.isFinite() && doubleValue == longValue.toDouble()) {
        "$key must be an integer."
    }
    require(longValue in minimum..maximum) { "$key is outside the supported range." }
    return longValue
}

internal fun JSONObject.requireDosingInt(
    key: String,
    minimum: Int = Int.MIN_VALUE,
    maximum: Int = Int.MAX_VALUE
): Int = requireDosingLong(key, minimum.toLong(), maximum.toLong()).toInt()

internal fun JSONObject.requireDosingDouble(
    key: String,
    minimum: Double = -Double.MAX_VALUE,
    maximum: Double = Double.MAX_VALUE
): Double {
    val number = get(key) as? Number ?: error("$key must be numeric.")
    return number.toDouble().also { value ->
        require(value.isFinite()) { "$key must be finite." }
        require(value in minimum..maximum) { "$key is outside the supported range." }
    }
}

internal fun JSONObject.requireDosingNullableDouble(
    key: String,
    minimum: Double = -Double.MAX_VALUE,
    maximum: Double = Double.MAX_VALUE
): Double? {
    val value = get(key)
    if (value === JSONObject.NULL) return null
    val number = value as? Number ?: error("$key must be numeric or null.")
    return number.toDouble().also { parsed ->
        require(parsed.isFinite()) { "$key must be finite." }
        require(parsed in minimum..maximum) { "$key is outside the supported range." }
    }
}

private fun Char.isFirmwareRejectedControl(): Boolean = code < ASCII_SPACE || code == ASCII_DEL

private const val ASCII_SPACE = 0x20
private const val ASCII_DEL = 0x7F
