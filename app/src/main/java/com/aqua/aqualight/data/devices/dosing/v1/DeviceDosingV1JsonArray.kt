package com.aqua.aqualight.data.devices.dosing.v1

import org.json.JSONArray
import org.json.JSONObject

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

private fun Char.isFirmwareRejectedControl(): Boolean = code < ASCII_SPACE || code == ASCII_DEL

private const val ASCII_SPACE = 0x20
private const val ASCII_DEL = 0x7F
