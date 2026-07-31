package com.aqua.aqualight.data.devices.runtime.modules.cooling

import org.json.JSONArray
import org.json.JSONObject

enum class DeviceCoolingMode(
    val wireValue: String
) {
    AUTO("Auto"),
    ON("On"),
    OFF("Off");

    companion object {
        fun fromWire(value: String): DeviceCoolingMode {
            return when (value.trim().lowercase()) {
                "auto", "schedule", "program" -> AUTO
                "on", "manual_on" -> ON
                else -> OFF
            }
        }
    }
}

data class DeviceCoolingRuntimeCapabilities(
    val module: String,
    val readOnly: Boolean,
    val supportsConfigApply: Boolean,
    val supportsModeSet: Boolean,
    val supportsTemperatureRange: Boolean,
    val hardwareEditable: Boolean,
    val fanMappingEditable: Boolean,
    val sensorMappingEditable: Boolean,
    val event: String
)

data class DeviceCoolingFanEditable(
    val hardware: Boolean,
    val displayName: Boolean,
    val hardwareCalibration: Boolean
)

data class DeviceCoolingFanStatus(
    val index: Int,
    val key: String,
    val name: String,
    val displayName: String,
    val profileManaged: Boolean,
    val mode: DeviceCoolingMode,
    val channelKind: String,
    val gpio: Int,
    val ledcChannel: Int,
    val group: Int,
    val valueNow: Double,
    val valueAuto: Double,
    val valueManual: Double,
    val valueMin: Double,
    val valueMax: Double,
    val manualTimeoutMs: Long,
    val percentNow: Double,
    val percentAuto: Double,
    val percentManual: Double,
    val percentMin: Double,
    val percentMax: Double,
    val invert: Boolean,
    val pwmResolutionBits: Int,
    val pwmFrequencyHz: Int,
    val editable: DeviceCoolingFanEditable
)

data class DeviceCoolingRuleStatus(
    val index: Int,
    val name: String,
    val enabled: Boolean,
    val fanIndex: Int,
    val channelKey: String,
    val bound: Boolean,
    val minTemperatureC: Double,
    val maxTemperatureC: Double,
    val group: Int,
    val sensorBindings: List<Int>
)

data class DeviceCoolingStatus(
    val supported: Boolean,
    val fanSupported: Boolean,
    val temperatureSupported: Boolean,
    val fanOutputCount: Int,
    val ruleCount: Int,
    val mode: DeviceCoolingMode,
    val minTemperatureC: Double,
    val maxTemperatureC: Double,
    val fixedSensorIndex: Int,
    val uptimeMs: Long,
    val fans: List<DeviceCoolingFanStatus>,
    val rules: List<DeviceCoolingRuleStatus>,
    val runtime: DeviceCoolingRuntimeCapabilities
)

/** One exact `cooling.config.apply.fans[]` item. Null/blank clears the override. */
data class DeviceCoolingFanConfig(
    val fanKey: String,
    val displayName: String?
) {
    val normalizedFanKey: String = fanKey.trim().lowercase()
    val normalizedDisplayName: String? = displayName?.trim()?.ifEmpty { null }

    init {
        require(normalizedFanKey.isNotEmpty() &&
            normalizedFanKey.none(Char::isISOControl)) {
            "fanKey must identify a configured cooling fan."
        }
        normalizedDisplayName?.let { value ->
            require(value.none(Char::isISOControl)) {
                "displayName must not contain control characters."
            }
            require(
                value.toByteArray(Charsets.UTF_8).size <=
                    DeviceCoolingRuntimeContract.Limit.DISPLAY_NAME_BYTES
            ) {
                "displayName must not exceed " +
                    "${DeviceCoolingRuntimeContract.Limit.DISPLAY_NAME_BYTES} UTF-8 bytes."
            }
        }
    }

    fun toJson(): JSONObject = JSONObject()
        .put(DeviceCoolingRuntimeContract.Field.FAN_KEY, normalizedFanKey)
        .put(
            DeviceCoolingRuntimeContract.Field.DISPLAY_NAME,
            normalizedDisplayName ?: JSONObject.NULL
        )
}

data class DeviceCoolingConfigApplyPayload(
    val mode: DeviceCoolingMode? = null,
    val minTemperatureC: Double? = null,
    val maxTemperatureC: Double? = null,
    val fans: List<DeviceCoolingFanConfig> = emptyList(),
    val save: Boolean = true
) {
    init {
        require(mode != null || minTemperatureC != null || maxTemperatureC != null ||
            fans.isNotEmpty()) {
            "cooling.config.apply requires mode, temperature range and/or fans."
        }

        if (minTemperatureC != null) {
            require(minTemperatureC in DeviceCoolingRuntimeContract.Limit.LOWEST_MIN_C..DeviceCoolingRuntimeContract.Limit.HIGHEST_MIN_C) {
                "minTemperatureC must be between ${DeviceCoolingRuntimeContract.Limit.LOWEST_MIN_C} and ${DeviceCoolingRuntimeContract.Limit.HIGHEST_MIN_C}."
            }
        }

        if (maxTemperatureC != null) {
            require(maxTemperatureC in DeviceCoolingRuntimeContract.Limit.LOWEST_MAX_C..DeviceCoolingRuntimeContract.Limit.HIGHEST_MAX_C) {
                "maxTemperatureC must be between ${DeviceCoolingRuntimeContract.Limit.LOWEST_MAX_C} and ${DeviceCoolingRuntimeContract.Limit.HIGHEST_MAX_C}."
            }
        }

        if (minTemperatureC != null && maxTemperatureC != null) {
            require(maxTemperatureC > minTemperatureC) {
                "maxTemperatureC must be greater than minTemperatureC."
            }
        }

        require(fans.size <= DeviceCoolingRuntimeContract.Limit.MAX_FANS) {
            "cooling.config.apply supports at most " +
                "${DeviceCoolingRuntimeContract.Limit.MAX_FANS} fan items."
        }
        require(fans.map(DeviceCoolingFanConfig::normalizedFanKey).distinct().size == fans.size) {
            "cooling.config.apply must not contain duplicate fanKey values."
        }
    }

    fun toJson(): JSONObject {
        val json = JSONObject()
            .put(DeviceCoolingRuntimeContract.Field.SAVE, save)

        if (mode != null) {
            json.put(DeviceCoolingRuntimeContract.Field.MODE, mode.wireValue)
        }

        if (minTemperatureC != null) {
            json.put(DeviceCoolingRuntimeContract.Field.MIN_TEMPERATURE_C, minTemperatureC)
        }

        if (maxTemperatureC != null) {
            json.put(DeviceCoolingRuntimeContract.Field.MAX_TEMPERATURE_C, maxTemperatureC)
        }

        if (fans.isNotEmpty()) {
            json.put(
                DeviceCoolingRuntimeContract.Field.FANS,
                JSONArray(fans.map(DeviceCoolingFanConfig::toJson))
            )
        }

        return json
    }
}

data class DeviceCoolingCommandResult(
    val sent: Boolean,
    val skipped: Boolean = false,
    val module: String = DeviceCoolingRuntimeContract.MODULE,
    val action: String,
    val messageId: String = "",
    val errorMessage: String = ""
) {
    val isSuccess: Boolean
        get() = sent && errorMessage.isBlank()
}
