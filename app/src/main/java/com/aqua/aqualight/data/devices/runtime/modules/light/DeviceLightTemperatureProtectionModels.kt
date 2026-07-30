package com.aqua.aqualight.data.devices.runtime.modules.light

import org.json.JSONObject

data class DeviceLightTemperatureProtectionSnapshot(
    val supported: Boolean,
    val active: Boolean,
    val thresholdEditable: Boolean,
    val thresholdC: Double?,
    val minimumC: Double?,
    val maximumC: Double?
)

data class DeviceLightTemperatureProtectionRuntimeCapabilities(
    val module: String,
    val readOnly: Boolean,
    val supportsStatusGet: Boolean,
    val supportsSet: Boolean,
    val event: String
)

data class DeviceLightTemperatureProtectionStatus(
    val supported: Boolean,
    val temperatureProtection: DeviceLightTemperatureProtectionSnapshot,
    val runtime: DeviceLightTemperatureProtectionRuntimeCapabilities
)

data class DeviceLightTemperatureProtectionSetResult(
    val changed: Boolean,
    val saved: Boolean,
    val saveRequested: Boolean,
    val status: DeviceLightTemperatureProtectionStatus
)

data class DeviceLightTemperatureProtectionSetPayload(
    val thresholdC: Double,
    val save: Boolean = true
) {
    init {
        require(thresholdC.isFinite()) { "thresholdC must be finite." }
        require(
            thresholdC in
                DeviceLightRuntimeContract.Limit.MIN_TEMPERATURE_PROTECTION_C..
                    DeviceLightRuntimeContract.Limit.MAX_TEMPERATURE_PROTECTION_C
        ) {
            "thresholdC must be between " +
                "${DeviceLightRuntimeContract.Limit.MIN_TEMPERATURE_PROTECTION_C} and " +
                "${DeviceLightRuntimeContract.Limit.MAX_TEMPERATURE_PROTECTION_C}."
        }
    }

    fun toJson(): JSONObject = JSONObject()
        .put(DeviceLightRuntimeContract.Field.THRESHOLD_C, thresholdC)
        .put(DeviceLightRuntimeContract.Field.SAVE, save)
}
