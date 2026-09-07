package com.aqua.aqualight.data.devices.runtime.modules.light

import org.json.JSONObject

/** Strict WRGB Pro Elite thermal contract owned by firmware. */
object DeviceLightThermalV1Contract {
    const val SCHEMA = "aql.light-thermal.v1"
    const val SCHEMA_VERSION = 1
    const val PRODUCT_KEY = "LIGHT_WRGB_PRO_ELITE"
    const val FIXTURE_SENSOR_KEY = "fixture"
    const val FAN_1_KEY = "fan1"
    const val FAN_2_KEY = "fan2"
    const val FAN_OUTPUT_CAPACITY = 2
    const val TEMPERATURE_SENSOR_CAPACITY = 1
    const val CONFIG_MINIMUM_TEMPERATURE_C = 0.0
    const val CONFIG_MAXIMUM_MIN_TEMPERATURE_C = 80.0
    const val CONFIG_MINIMUM_MAX_TEMPERATURE_C = 1.0
    const val CONFIG_MAXIMUM_TEMPERATURE_C = 90.0
    const val SENSOR_READING_MINIMUM_C = -40.0
    const val SENSOR_READING_MAXIMUM_C = 125.0
    const val SENSOR_INDEX_UNAVAILABLE = -1
    const val SENSOR_INDEX_MAXIMUM = 7
    const val FAN_INDEX_MINIMUM = 0
    const val FAN_INDEX_MAXIMUM = FAN_OUTPUT_CAPACITY - 1
    const val GPIO_MINIMUM = 0
    const val GPIO_MAXIMUM = 48
    const val LEDC_CHANNEL_MINIMUM = 0
    const val LEDC_CHANNEL_MAXIMUM = 15
    const val PWM_FREQUENCY_MINIMUM_HZ = 1
    const val PWM_RESOLUTION_MINIMUM_BITS = 1
    const val PWM_RESOLUTION_MAXIMUM_BITS = 16
    const val NORMALIZED_OUTPUT_MINIMUM = 0.0
    const val NORMALIZED_OUTPUT_MAXIMUM = 1.0
    const val FAN_PERCENT_MINIMUM = 0.0
    const val FAN_PERCENT_MAXIMUM = 100.0
    const val PROTECTION_THRESHOLD_MINIMUM_C = 50.0
    const val PROTECTION_THRESHOLD_MAXIMUM_C = 70.0
    const val SENSOR_STALE_AFTER_MS = 10_000L
    const val SENSOR_FAULT_FAN_PERCENT = 100.0

    object Action {
        const val STATUS_GET = "thermal.status.get"
        const val CONFIG_APPLY = "thermal.config.apply"
    }

    object Event {
        const val STATUS_CHANGED = "light.thermal.status.changed"
        const val TELEMETRY_CHANGED = "light.thermal.telemetry.changed"
    }
}

enum class DeviceLightThermalMode(val wireValue: String) {
    AUTO("Auto"),
    ON("On"),
    OFF("Off")
}

data class DeviceLightThermalConfigApplyPayload(
    val mode: DeviceLightThermalMode? = null,
    val minTemperatureC: Double? = null,
    val maxTemperatureC: Double? = null,
    val save: Boolean = true
) {
    init {
        require(mode != null || minTemperatureC != null || maxTemperatureC != null) {
            "light.thermal.config.apply requires at least one config field."
        }
        minTemperatureC?.let { value ->
            require(
                value.isFinite() &&
                    value in
                    DeviceLightThermalV1Contract.CONFIG_MINIMUM_TEMPERATURE_C..
                    DeviceLightThermalV1Contract.CONFIG_MAXIMUM_MIN_TEMPERATURE_C
            )
        }
        maxTemperatureC?.let { value ->
            require(
                value.isFinite() &&
                    value in
                    DeviceLightThermalV1Contract.CONFIG_MINIMUM_MAX_TEMPERATURE_C..
                    DeviceLightThermalV1Contract.CONFIG_MAXIMUM_TEMPERATURE_C
            )
        }
        if (minTemperatureC != null && maxTemperatureC != null) {
            require(minTemperatureC < maxTemperatureC)
        }
    }

    fun toJson(): JSONObject = JSONObject().also { data ->
        mode?.let { data.put("mode", it.wireValue) }
        minTemperatureC?.let { data.put("minTemperatureC", it) }
        maxTemperatureC?.let { data.put("maxTemperatureC", it) }
        data.put("save", save)
    }
}
