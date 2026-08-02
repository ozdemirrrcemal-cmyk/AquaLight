package com.aqua.aqualight.data.devices.runtime.modules.timer

import org.json.JSONArray
import org.json.JSONObject

internal fun JSONArray.requireTimerObject(index: Int): JSONObject =
    get(index) as? JSONObject ?: error("[$index] must be a JSON object.")

internal fun JSONArray.requireTimerBoolean(index: Int): Boolean =
    get(index) as? Boolean ?: error("[$index] must be a boolean.")
