package com.aqua.aqualight.data.devices.runtime.modules.light

import org.json.JSONObject

internal fun JSONObject.requireLightInt(
    key: String,
    minimum: Int = Int.MIN_VALUE,
    maximum: Int = Int.MAX_VALUE
): Int = requireLightInteger(get(key), key, minimum.toLong(), maximum.toLong()).toInt()

internal fun JSONObject.requireNullableLightInt(
    key: String,
    minimum: Int = Int.MIN_VALUE,
    maximum: Int = Int.MAX_VALUE
): Int? = when (val value = get(key)) {
    JSONObject.NULL -> null
    else -> requireLightInteger(value, key, minimum.toLong(), maximum.toLong()).toInt()
}

internal fun JSONObject.requireLightLong(
    key: String,
    minimum: Long = Long.MIN_VALUE,
    maximum: Long = Long.MAX_VALUE
): Long = requireLightInteger(get(key), key, minimum, maximum)

internal fun JSONObject.requireNullableLightLong(
    key: String,
    minimum: Long = Long.MIN_VALUE,
    maximum: Long = Long.MAX_VALUE
): Long? = when (val value = get(key)) {
    JSONObject.NULL -> null
    else -> requireLightInteger(value, key, minimum, maximum)
}

internal fun requireLightInteger(value: Any, key: String, minimum: Long, maximum: Long): Long {
    val number = value as? Number ?: error("$key must be an integer.")
    val asDouble = number.toDouble()
    val asLong = number.toLong()
    require(asDouble.isFinite() && asDouble == asLong.toDouble()) { "$key must be an integer." }
    require(asLong in minimum..maximum) { "$key is outside its supported range." }
    return asLong
}

internal fun JSONObject.requireLightDouble(
    key: String,
    minimum: Double = -Double.MAX_VALUE,
    maximum: Double = Double.MAX_VALUE
): Double = requireLightNumber(get(key), key, minimum, maximum)

internal fun JSONObject.requireNullableLightDouble(
    key: String,
    minimum: Double = -Double.MAX_VALUE,
    maximum: Double = Double.MAX_VALUE
): Double? = when (val value = get(key)) {
    JSONObject.NULL -> null
    else -> requireLightNumber(value, key, minimum, maximum)
}

private fun requireLightNumber(value: Any, key: String, minimum: Double, maximum: Double): Double {
    val number = value as? Number ?: error("$key must be numeric.")
    return number.toDouble().also {
        require(it.isFinite()) { "$key must be finite." }
        require(it in minimum..maximum) { "$key is outside its supported range." }
    }
}
