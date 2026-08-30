package com.aqua.aqualight.data.devices.runtime.modules.cooling

object DeviceCoolingRuntimeContract {
    const val MODULE = "cooling"
    const val STATUS_EVENT = "cooling.status.changed"
    const val TEMPERATURE_EVENT = "temperature.changed"

    object Action {
        const val STATUS_GET = "status.get"
        const val CONFIG_APPLY = "config.apply"
        const val HISTORY_GET = "history.get"
    }

    object Field {
        const val SUPPORTED = "supported"
        const val FAN_SUPPORTED = "fanSupported"
        const val TEMPERATURE_SUPPORTED = "temperatureSupported"
        const val FAN_OUTPUT_COUNT = "fanOutputCount"
        const val RULE_COUNT = "ruleCount"
        const val MODE = "mode"
        const val MIN_TEMPERATURE_C = "minTemperatureC"
        const val AVG_TEMPERATURE_C = "avgTemperatureC"
        const val MAX_TEMPERATURE_C = "maxTemperatureC"
        const val TEMPERATURE_C = "temperatureC"
        const val FIXED_SENSOR_INDEX = "fixedSensorIndex"
        const val UPTIME_MS = "uptimeMs"
        const val TEMPERATURE = "temperature"
        const val FANS = "fans"
        const val RULES = "rules"
        const val RUNTIME = "runtime"
        const val SAVE = "save"
        const val FAN_KEY = "fanKey"
        const val DISPLAY_NAME = "displayName"
        const val RANGE = "range"
        const val GENERATED_AT_MS = "generatedAtMs"
        const val SAMPLED_AT_MS = "sampledAtMs"
        const val DAY_START_AT_MS = "dayStartAtMs"
        const val SUMMARY = "summary"
        const val SAMPLES = "samples"
        const val DAYS = "days"
    }

    object Limit {
        const val LOWEST_MIN_C = 0.0
        const val HIGHEST_MIN_C = 80.0
        const val LOWEST_MAX_C = 1.0
        const val HIGHEST_MAX_C = 90.0
        const val MAX_FAN_DISPLAY_NAME_BYTES = 32
        const val MAX_SENSOR_INDEX = 7
        const val MAX_FAN_COUNT = 8
        const val MAX_HISTORY_SAMPLE_COUNT = 4096
        const val MAX_HISTORY_DAY_COUNT = 31
    }

    object Literal {
        const val CONFIG_APPLY_OPERATION = "configApply"
        const val RUNTIME_TRANSPORT = "websocket"
        const val RUNTIME_MODULE = MODULE
        const val CHANNEL_KIND_GPIO = "gpio"
        const val CHANNEL_KIND_DIGITAL = "digital"
        const val CHANNEL_KIND_NONE = "none"
    }
}
