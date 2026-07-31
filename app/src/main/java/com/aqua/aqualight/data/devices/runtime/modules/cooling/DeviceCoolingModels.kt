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
            return values().singleOrNull { it.wireValue == value }
                ?: error("Unknown firmware cooling mode: $value")
        }
    }
}

data class DeviceCoolingRuntimeCapabilities(
    val module: String,
    val readOnly: Boolean,
    val supportsConfigApply: Boolean,
    val supportsModeSet: Boolean,
    val supportsTemperatureRange: Boolean,
    val supportsFanDisplayName: Boolean,
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

data class DeviceCoolingFanConfig(
    val fanKey: String,
    val displayName: String?
) {
    init {
        requireCanonicalCoolingKey(fanKey, "fanKey")
        require(fanKey == fanKey.lowercase()) {
            "fanKey must use the canonical lowercase firmware key."
        }
        displayName?.let {
            requireCanonicalCoolingText(it, "displayName", allowEmpty = true)
            require(it.toByteArray(Charsets.UTF_8).size <= MAX_FAN_DISPLAY_NAME_BYTES) {
                "displayName must be at most $MAX_FAN_DISPLAY_NAME_BYTES UTF-8 bytes."
            }
        }
    }

    fun toJson(): JSONObject {
        return JSONObject()
            .put(FIELD_FAN_KEY, fanKey)
            .put(FIELD_DISPLAY_NAME, displayName ?: JSONObject.NULL)
    }
}

data class DeviceCoolingConfigApplyPayload(
    val mode: DeviceCoolingMode? = null,
    val minTemperatureC: Double? = null,
    val maxTemperatureC: Double? = null,
    val fans: List<DeviceCoolingFanConfig> = emptyList(),
    val save: Boolean = true
) {
    init {
        require(
            mode != null ||
                minTemperatureC != null ||
                maxTemperatureC != null ||
                fans.isNotEmpty()
        ) {
            "cooling.config.apply requires mode, temperature range and/or fan display names."
        }

        if (minTemperatureC != null) {
            require(minTemperatureC.isFinite()) { "minTemperatureC must be finite." }
            require(
                minTemperatureC in
                    DeviceCoolingRuntimeContract.Limit.LOWEST_MIN_C..
                    DeviceCoolingRuntimeContract.Limit.HIGHEST_MIN_C
            ) {
                "minTemperatureC must be between " +
                    "${DeviceCoolingRuntimeContract.Limit.LOWEST_MIN_C} and " +
                    "${DeviceCoolingRuntimeContract.Limit.HIGHEST_MIN_C}."
            }
        }

        if (maxTemperatureC != null) {
            require(maxTemperatureC.isFinite()) { "maxTemperatureC must be finite." }
            require(
                maxTemperatureC in
                    DeviceCoolingRuntimeContract.Limit.LOWEST_MAX_C..
                    DeviceCoolingRuntimeContract.Limit.HIGHEST_MAX_C
            ) {
                "maxTemperatureC must be between " +
                    "${DeviceCoolingRuntimeContract.Limit.LOWEST_MAX_C} and " +
                    "${DeviceCoolingRuntimeContract.Limit.HIGHEST_MAX_C}."
            }
        }

        if (minTemperatureC != null && maxTemperatureC != null) {
            require(maxTemperatureC > minTemperatureC) {
                "maxTemperatureC must be greater than minTemperatureC."
            }
        }

        require(fans.size <= MAX_COOLING_FANS_WS) {
            "Cooling supports at most $MAX_COOLING_FANS_WS fan display-name updates per request."
        }
        require(fans.map(DeviceCoolingFanConfig::fanKey).toSet().size == fans.size) {
            "fanKey values must be unique in a cooling config request."
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
            json.put(FIELD_FANS, JSONArray(fans.map(DeviceCoolingFanConfig::toJson)))
        }

        return json
    }
}

data class DeviceCoolingFanConfigSnapshot(
    val listIndex: Int,
    val fan: DeviceCoolingFanStatus
)

data class DeviceCoolingRuleConfigSnapshot(
    val listIndex: Int,
    val index: Int,
    val name: String,
    val enabled: Boolean,
    val fanIndex: Int,
    val channelKey: String,
    val bound: Boolean,
    val minTemperatureC: Double,
    val maxTemperatureC: Double,
    val group: Int
)

data class DeviceCoolingConfigSnapshot(
    val supported: Boolean,
    val fanSupported: Boolean,
    val temperatureSupported: Boolean,
    val fanOutputCount: Int,
    val ruleCount: Int,
    val mode: DeviceCoolingMode,
    val minTemperatureC: Double,
    val maxTemperatureC: Double,
    val fixedSensorIndex: Int,
    val hardwareEditable: Boolean,
    val fanMappingEditable: Boolean,
    val sensorMappingEditable: Boolean,
    val fans: List<DeviceCoolingFanConfigSnapshot>,
    val rules: List<DeviceCoolingRuleConfigSnapshot>
)

data class DeviceCoolingConfigApplyResult(
    val operation: String,
    val changed: Boolean,
    val saved: Boolean,
    val saveRequested: Boolean,
    val runtimeTransport: String,
    val command: String,
    val event: String,
    val appliedGlobalConfig: Boolean,
    val appliedFanDisplayNames: Boolean,
    val config: DeviceCoolingConfigSnapshot
)

private fun requireCanonicalCoolingKey(value: String, field: String) {
    requireCanonicalCoolingText(value, field, allowEmpty = false)
    require(value != "-" && !value.equals("none", ignoreCase = true)) {
        "$field must target a configured cooling fan."
    }
}

private fun requireCanonicalCoolingText(
    value: String,
    field: String,
    allowEmpty: Boolean
) {
    require(allowEmpty || value.isNotEmpty()) { "$field must not be empty." }
    require(value == value.trim()) { "$field must not contain surrounding whitespace." }
    require(value.none(Char::isISOControl)) { "$field must not contain control characters." }
}

private const val FIELD_FANS = "fans"
private const val FIELD_FAN_KEY = "fanKey"
private const val FIELD_DISPLAY_NAME = "displayName"
private const val MAX_COOLING_FANS_WS = 8
private const val MAX_FAN_DISPLAY_NAME_BYTES = 32
