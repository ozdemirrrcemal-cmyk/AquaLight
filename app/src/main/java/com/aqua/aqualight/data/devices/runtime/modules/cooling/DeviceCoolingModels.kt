package com.aqua.aqualight.data.devices.runtime.modules.cooling

import org.json.JSONArray
import org.json.JSONObject

enum class DeviceCoolingMode(val wireValue: String) {
    AUTO("Auto"),
    ON("On"),
    OFF("Off")
}

data class DeviceCoolingTemperatureSnapshot(
    val sensorIndex: Int,
    val readingValid: Boolean,
    val temperatureC: Double?,
    val sampledAtMs: Long
)

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

data class DeviceCoolingFanConfigSnapshot(
    val listIndex: Int,
    val fan: DeviceCoolingFanStatus
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
    val temperature: DeviceCoolingTemperatureSnapshot,
    val fans: List<DeviceCoolingFanStatus>,
    val rules: List<DeviceCoolingRuleStatus>,
    val runtime: DeviceCoolingRuntimeCapabilities
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

data class DeviceCoolingFanDisplayNamePayload(
    val fanKey: String,
    val displayName: String?
) {
    val normalizedFanKey: String = fanKey.trim().lowercase()
    val normalizedDisplayName: String? = displayName
        ?.trim()
        ?.takeIf(String::isNotEmpty)

    init {
        require(normalizedFanKey.isNotEmpty()) { "fanKey must not be blank." }
        require(normalizedFanKey.none(Char::isISOControl)) {
            "fanKey must not contain control characters."
        }
        require(normalizedDisplayName?.none(Char::isISOControl) != false) {
            "displayName must not contain control characters."
        }
        require(
            normalizedDisplayName
                ?.toByteArray(Charsets.UTF_8)
                ?.size
                ?.let { size -> size <= DeviceCoolingRuntimeContract.Limit.MAX_FAN_DISPLAY_NAME_BYTES }
                ?: true
        ) {
            "displayName must be at most " +
                "${DeviceCoolingRuntimeContract.Limit.MAX_FAN_DISPLAY_NAME_BYTES} UTF-8 bytes."
        }
    }

    internal fun toJson(): JSONObject = JSONObject()
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
    val fans: List<DeviceCoolingFanDisplayNamePayload> = emptyList(),
    val save: Boolean = true
) {
    init {
        require(
            mode != null ||
                minTemperatureC != null ||
                maxTemperatureC != null ||
                fans.isNotEmpty()
        ) { "cooling.config.apply requires at least one supported field." }

        minTemperatureC?.let { minimum ->
            require(
                minimum in DeviceCoolingRuntimeContract.Limit.LOWEST_MIN_C..
                    DeviceCoolingRuntimeContract.Limit.HIGHEST_MIN_C
            ) { "minTemperatureC is outside the supported range." }
        }
        maxTemperatureC?.let { maximum ->
            require(
                maximum in DeviceCoolingRuntimeContract.Limit.LOWEST_MAX_C..
                    DeviceCoolingRuntimeContract.Limit.HIGHEST_MAX_C
            ) { "maxTemperatureC is outside the supported range." }
        }
        if (minTemperatureC != null && maxTemperatureC != null) {
            require(maxTemperatureC > minTemperatureC) {
                "maxTemperatureC must be greater than minTemperatureC."
            }
        }
        require(fans.size <= DeviceCoolingRuntimeContract.Limit.MAX_FAN_COUNT) {
            "fans exceeds the WebSocket safety limit."
        }
        require(fans.map(DeviceCoolingFanDisplayNamePayload::normalizedFanKey).distinct().size == fans.size) {
            "fanKey must be unique in the request."
        }
    }

    val hasGlobalConfig: Boolean
        get() = mode != null || minTemperatureC != null || maxTemperatureC != null

    internal fun toJson(): JSONObject {
        val data = JSONObject()
        mode?.let { selected -> data.put(DeviceCoolingRuntimeContract.Field.MODE, selected.wireValue) }
        minTemperatureC?.let { minimum ->
            data.put(DeviceCoolingRuntimeContract.Field.MIN_TEMPERATURE_C, minimum)
        }
        maxTemperatureC?.let { maximum ->
            data.put(DeviceCoolingRuntimeContract.Field.MAX_TEMPERATURE_C, maximum)
        }
        if (fans.isNotEmpty()) {
            data.put(
                DeviceCoolingRuntimeContract.Field.FANS,
                JSONArray().also { array -> fans.forEach { fan -> array.put(fan.toJson()) } }
            )
        }
        return data.put(DeviceCoolingRuntimeContract.Field.SAVE, save)
    }
}
