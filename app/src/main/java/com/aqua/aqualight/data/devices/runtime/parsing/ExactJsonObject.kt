package com.aqua.aqualight.data.devices.runtime.parsing

import org.json.JSONArray
import org.json.JSONObject

internal fun JSONObject.requireExactKeys(expected: Set<String>, label: String) {
    val actual = buildSet {
        val iterator = keys()
        while (iterator.hasNext()) add(iterator.next())
    }
    require(actual == expected) {
        "$label keys differ from the firmware contract: $actual"
    }
}

internal fun JSONObject.requireAllowedAndRequiredKeys(
    allowed: Set<String>,
    required: Set<String>,
    label: String
) {
    val actual = buildSet {
        val iterator = keys()
        while (iterator.hasNext()) add(iterator.next())
    }
    require(actual.all(allowed::contains)) {
        "$label contains unknown keys: ${actual - allowed}"
    }
    require(actual.containsAll(required)) {
        "$label is missing keys: ${required - actual}"
    }
}

internal fun JSONObject.requiredObject(key: String): JSONObject =
    get(key) as? JSONObject ?: error("$key must be an object.")

internal fun JSONObject.requiredArray(key: String): JSONArray =
    get(key) as? JSONArray ?: error("$key must be an array.")

internal fun JSONObject.requiredString(key: String): String =
    requiredStringAllowEmpty(key).also {
        require(it.isNotEmpty()) { "$key must not be empty." }
    }

internal fun JSONObject.requiredStringAllowEmpty(key: String): String {
    val value = get(key) as? String ?: error("$key must be a string.")
    require(value.none(Char::isISOControl)) { "$key contains control characters." }
    require(value.isEmpty() || (!value.first().isWhitespace() && !value.last().isWhitespace())) {
        "$key must not contain leading/trailing whitespace."
    }
    return value
}

internal fun JSONObject.requiredBoolean(key: String): Boolean =
    get(key) as? Boolean ?: error("$key must be a boolean.")

internal fun JSONObject.requiredInt(key: String): Int {
    val number = get(key) as? Number ?: error("$key must be numeric.")
    val doubleValue = number.toDouble()
    val longValue = number.toLong()
    require(doubleValue.isFinite() && doubleValue == longValue.toDouble()) {
        "$key must be an exact integer."
    }
    require(longValue in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
        "$key is outside Int range."
    }
    return longValue.toInt()
}

internal fun JSONObject.requiredNonNegativeLong(key: String): Long {
    val number = get(key) as? Number ?: error("$key must be numeric.")
    val doubleValue = number.toDouble()
    val longValue = number.toLong()
    require(doubleValue.isFinite() && doubleValue == longValue.toDouble() && longValue >= 0L) {
        "$key must be a non-negative exact integer."
    }
    return longValue
}

internal fun JSONObject.requiredFiniteDouble(key: String): Double =
    (get(key) as? Number)?.toDouble()?.also {
        require(it.isFinite()) { "$key must be finite." }
    } ?: error("$key must be a finite number.")
