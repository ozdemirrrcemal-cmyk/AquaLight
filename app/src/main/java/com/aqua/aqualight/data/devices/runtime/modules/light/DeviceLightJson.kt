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

internal fun JSONObject.requireLightText(key: String): String {
    val value = get(key) as? String ?: error("$key must be a string.")
    return requireExactLightText(key, value)
}

internal fun JSONObject.requireNullableLightText(key: String): String? = when (val value = get(key)) {
    JSONObject.NULL -> null
    is String -> requireExactLightText(key, value)
    else -> error("$key must be a string or null.")
}

internal fun requireExactLightText(key: String, value: String): String {
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
