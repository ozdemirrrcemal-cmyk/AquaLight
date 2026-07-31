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
        private val exactValues = entries.associateBy(DeviceCoolingMode::wireValue)

        fun fromWireExact(value: String): DeviceCoolingMode? = exactValues[value]

        fun fromWire(value: String): DeviceCoolingMode = when (value.trim().lowercase()) {
            "auto", "schedule", "program" -> AUTO
            "on", "manual_on" -> ON
            else -> OFF
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

data class DeviceCoolingFanDisplayNameConfig(
    val fanKey: String,
    val displayName: String?
) {
    init {
        require(fanKey.isNotBlank()) { "fanKey must not be blank." }
        require(fanKey == fanKey.trim().lowercase()) {
            "fanKey must use the canonical lowercase firmware key."
        }
        if (displayName != null) {
            require(displayName == displayName.trim()) {
                "displayName must not contain surrounding whitespace."
            }
            require(
                displayName.toByteArray(Charsets.UTF_8).size <=
                    DeviceCoolingRuntimeContract.Limit.MAX_DISPLAY_NAME_BYTES
            ) {
                "displayName exceeds the firmware UTF-8 byte limit."
            }
        }
    }

    fun toJson(): JSONObject = JSONObject()
        .put(DeviceCoolingRuntimeContract.Field.FAN_KEY, fanKey)
        .put(
            DeviceCoolingRuntimeContract.Field.DISPLAY_NAME,
            displayName ?: JSONObject.NULL
        )
}

data class DeviceCoolingConfigApplyPayload(
    val mode: DeviceCoolingMode? = null,
    val minTemperatureC: Double? = null,
    val maxTemperatureC: Double? = null,
    val fans: List<DeviceCoolingFanDisplayNameConfig> = emptyList(),
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
        require(fans.size <= DeviceCoolingRuntimeContract.Limit.MAX_FANS_PER_REQUEST) {
            "Too many cooling fan display-name updates."
        }
        require(fans.map(DeviceCoolingFanDisplayNameConfig::fanKey).distinct().size == fans.size) {
            "fanKey must be unique in a cooling config request."
        }

        if (minTemperatureC != null) {
            require(
                minTemperatureC in
                    DeviceCoolingRuntimeContract.Limit.LOWEST_MIN_C..
                        DeviceCoolingRuntimeContract.Limit.HIGHEST_MIN_C
            ) {
                "minTemperatureC is outside the firmware range."
            }
        }
        if (maxTemperatureC != null) {
            require(
                maxTemperatureC in
                    DeviceCoolingRuntimeContract.Limit.LOWEST_MAX_C..
                        DeviceCoolingRuntimeContract.Limit.HIGHEST_MAX_C
            ) {
                "maxTemperatureC is outside the firmware range."
            }
        }
        if (minTemperatureC != null && maxTemperatureC != null) {
            require(maxTemperatureC > minTemperatureC) {
                "maxTemperatureC must be greater than minTemperatureC."
            }
        }
    }

    fun toJson(): JSONObject {
        val json = JSONObject().put(DeviceCoolingRuntimeContract.Field.SAVE, save)
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
                JSONArray(fans.map(DeviceCoolingFanDisplayNameConfig::toJson))
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
