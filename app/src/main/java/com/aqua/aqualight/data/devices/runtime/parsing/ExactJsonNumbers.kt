package com.aqua.aqualight.data.devices.runtime.parsing

import org.json.JSONObject

private const val MIN_NETWORK_PORT = 1
private const val MAX_NETWORK_PORT = 65_535

internal fun JSONObject.requiredNonNegativeInt(key: String): Int =
    requiredInt(key).also {
        require(it >= 0) { "$key must be non-negative." }
    }

internal fun JSONObject.requiredPort(key: String): Int =
    requiredInt(key).also {
        require(it in MIN_NETWORK_PORT..MAX_NETWORK_PORT) {
            "$key must be a valid network port."
        }
    }

internal fun JSONObject.optionalNonNegativeLong(key: String): Long? =
    if (has(key)) requiredNonNegativeLong(key) else null

internal fun JSONObject.optionalBoolean(key: String): Boolean? =
    if (has(key)) requiredBoolean(key) else null
