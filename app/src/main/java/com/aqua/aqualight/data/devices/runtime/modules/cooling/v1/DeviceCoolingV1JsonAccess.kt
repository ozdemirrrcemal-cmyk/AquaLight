package com.aqua.aqualight.data.devices.runtime.modules.cooling.v1

import org.json.JSONArray
import org.json.JSONObject

internal fun JSONObject.requireExactKeys(expected: Set<String>, label: String) {
    val actual = keys().asSequence().toSet()
    require(actual == expected) {
        "$label keys differ from firmware; expected=$expected actual=$actual"
    }
}

internal fun JSONObject.requireObject(key: String): JSONObject =
    get(key) as? JSONObject ?: error("$key must be an object.")

internal fun JSONObject.requireArray(key: String): JSONArray =
    get(key) as? JSONArray ?: error("$key must be an array.")

internal fun JSONObject.requireText(key: String): String =
    (get(key) as? String)?.also { value ->
        require(value.isNotEmpty() && value.none(Char::isISOControl))
        require(value == value.trim())
    } ?: error("$key must be a canonical string.")

internal fun JSONObject.requireBoolean(key: String): Boolean =
    get(key) as? Boolean ?: error("$key must be boolean.")

internal fun JSONArray.objects(): List<JSONObject> = List(length()) { index ->
    get(index) as? JSONObject ?: error("[$index] must be an object.")
}

internal inline fun <reified T : Enum<T>> enumValue(
    value: String,
    wireValue: (T) -> String
): T = enumValues<T>().singleOrNull { wireValue(it) == value }
    ?: error("Unknown enum value: $value")
