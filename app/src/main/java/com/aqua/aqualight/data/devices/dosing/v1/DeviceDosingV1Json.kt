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

internal fun dosingWireValue(value: String): DeviceDosingV1WireValue =
    DeviceDosingV1WireValue(value)


internal fun JSONObject.requireDosingChannelKey(key: String): DeviceDosingV1ChannelKey =
    DeviceDosingV1ChannelKey.parseCanonical(requireDosingString(key))
