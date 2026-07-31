package com.aqua.aqualight.data.devices.runtime.modules.cooling

object DeviceCoolingRuntimeContract {
    const val MODULE = "cooling"

    object Action {
        const val STATUS_GET = "status.get"
        const val CONFIG_APPLY = "config.apply"
    }

    object Field {
        const val MODE = "mode"
        const val MIN_TEMPERATURE_C = "minTemperatureC"
        const val MAX_TEMPERATURE_C = "maxTemperatureC"
        const val FANS = "fans"
        const val FAN_KEY = "fanKey"
        const val DISPLAY_NAME = "displayName"
        const val SAVE = "save"
    }

    object Limit {
        const val LOWEST_MIN_C = 0.0
        const val HIGHEST_MIN_C = 80.0
        const val LOWEST_MAX_C = 1.0
        const val HIGHEST_MAX_C = 90.0
        const val MAX_FANS_PER_REQUEST = 8
        const val MAX_DISPLAY_NAME_BYTES = 32
    }
}
