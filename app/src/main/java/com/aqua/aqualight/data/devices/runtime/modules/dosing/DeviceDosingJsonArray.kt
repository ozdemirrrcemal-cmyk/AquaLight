package com.aqua.aqualight.data.devices.runtime.modules.dosing

import org.json.JSONArray
import org.json.JSONObject

internal fun JSONArray.requireDosingObject(index: Int): JSONObject =
    get(index) as? JSONObject ?: error("[$index] must be a JSON object.")

internal fun JSONArray.requireDosingBoolean(index: Int): Boolean =
    get(index) as? Boolean ?: error("[$index] must be a boolean.")
