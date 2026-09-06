package com.aqua.aqualight.data.devices.runtime.modules.cooling.v1

import org.json.JSONObject

internal fun JSONObject.requireInt(key: String, minimum: Int, maximum: Int): Int {
    val value = get(key) as? Number ?: error("$key must be integer.")
    val long = value.toLong()
    require(value.toDouble().isFinite() && value.toDouble() == long.toDouble())
    require(long in minimum.toLong()..maximum.toLong())
    return long.toInt()
}

internal fun JSONObject.requireNullableInt(
    key: String,
    minimum: Int,
    maximum: Int
): Int? = if (get(key) === JSONObject.NULL) null else requireInt(key, minimum, maximum)

internal fun JSONObject.requireNonNegativeLong(key: String): Long {
    val value = get(key) as? Number ?: error("$key must be integer.")
    val long = value.toLong()
    require(value.toDouble().isFinite() && value.toDouble() == long.toDouble())
    require(long >= 0L)
    return long
}

internal fun JSONObject.requireRevision(key: String): Long =
    requireNonNegativeLong(key).also { requireRevision(it, key) }

internal fun JSONObject.requireDouble(
    key: String,
    minimum: Double,
    maximum: Double = Double.MAX_VALUE
): Double = (get(key) as? Number)?.toDouble()?.also { value ->
    require(value.isFinite() && value in minimum..maximum)
} ?: error("$key must be numeric.")

internal fun JSONObject.requireNullableDouble(
    key: String,
    minimum: Double,
    maximum: Double = Double.MAX_VALUE
): Double? = if (get(key) === JSONObject.NULL) {
    null
} else {
    requireDouble(key, minimum, maximum)
}

internal fun JSONObject.requireTemperature(key: String): Double =
    requireDouble(
        key,
        DeviceCoolingV1Contract.Limit.TEMPERATURE_MINIMUM_C,
        DeviceCoolingV1Contract.Limit.TEMPERATURE_MAXIMUM_C
    ).also(::requireCoolingTemperature)

internal fun JSONObject.requirePercent(key: String): Double =
    requireDouble(
        key,
        DeviceCoolingV1Contract.Limit.FAN_PERCENT_MINIMUM,
        DeviceCoolingV1Contract.Limit.FAN_PERCENT_MAXIMUM
    ).also(::requireCoolingFanPercent)

/**
 * Firmware-computed control output is continuous across the automatic cooling curve.
 * The catalog step applies to writable Manual/Program values, not runtime telemetry.
 */
internal fun JSONObject.requireRuntimePercent(key: String): Double =
    requireDouble(
        key,
        DeviceCoolingV1Contract.Limit.FAN_PERCENT_MINIMUM,
        DeviceCoolingV1Contract.Limit.FAN_PERCENT_MAXIMUM
    )
