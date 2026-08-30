@file:Suppress("TooManyFunctions")

package com.aqua.aqualight.data.devices.dosing.v1

import org.json.JSONArray
import org.json.JSONObject

internal fun JSONObject.requireDosingKeys(expected: Set<String>, label: String) {
    val actual = keys().asSequence().toSet()
    require(actual == expected) {
        "$label keys differ from aqualight.dosing.v1; expected=$expected actual=$actual"
    }
}

internal fun JSONObject.requireDosingObject(key: String): JSONObject =
    get(key) as? JSONObject ?: error("$key must be a JSON object.")

internal fun JSONObject.requireDosingArray(key: String): JSONArray =
    get(key) as? JSONArray ?: error("$key must be a JSON array.")

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

internal fun JSONArray.requireDosingObject(index: Int): JSONObject =
    get(index) as? JSONObject ?: error("Array item $index must be a JSON object.")

internal fun JSONArray.requireDosingBoolean(index: Int): Boolean =
    get(index) as? Boolean ?: error("Array item $index must be a boolean.")

internal fun JSONArray.requireDosingString(index: Int): String =
    (get(index) as? String)?.also { text ->
        require(text.isNotEmpty()) { "Array item $index must not be empty." }
        require(text.none(Char::isFirmwareRejectedControl)) {
            "Array item $index must not contain firmware-rejected control characters."
        }
    } ?: error("Array item $index must be a string.")

private fun Char.isFirmwareRejectedControl(): Boolean = code < ASCII_SPACE || code == ASCII_DEL

internal fun dosingWireValue(value: String): DeviceDosingV1WireValue =
    DeviceDosingV1WireValue(value)

internal fun JSONObject.requireDosingChannelKey(key: String): DeviceDosingV1ChannelKey =
    DeviceDosingV1ChannelKey.parseCanonical(requireDosingString(key))

internal fun requireDosingUInt(value: Long, field: String) {
    require(value in 0L..DeviceDosingV1Contract.Limit.MAX_UNSIGNED_INT) {
        "$field must be an unsigned 32-bit integer."
    }
}

internal fun requireDosingTimeMillis(value: Long, field: String) {
    require(value in 0L until DeviceDosingV1Contract.Limit.MILLIS_PER_DAY) {
        "$field must be inside one local firmware day."
    }
}

private const val ASCII_SPACE = 0x20
private const val ASCII_DEL = 0x7F
