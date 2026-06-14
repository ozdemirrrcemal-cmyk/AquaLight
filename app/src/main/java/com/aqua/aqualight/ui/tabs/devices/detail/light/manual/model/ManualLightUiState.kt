package com.aqua.aqualight.ui.tabs.devices.detail.light.manual.model

data class ManualLightUiState(
    val controlMode: ManualLightControlMode = ManualLightControlMode.AUTO,

    val isManualMode: Boolean = false,
    val isManualScene: Boolean = false,
    val isPowerOn: Boolean = false,

    val activeSceneName: String? = null,
    val activeSceneSource: String? = null,

    val powerLoadPercent: Int = 0,
    val currentPowerWatts: Double? = null,
    val maxPowerWatts: Double? = null,
    val powerText: String = "-- W",

    val red: Int = 0,
    val green: Int = 0,
    val blue: Int = 0,
    val white: Int = 0,

    val savedPresets: List<ManualLightPreset> = emptyList(),

    val isDeviceOnline: Boolean = true,
    val controlsEnabled: Boolean = true,
    val connectionStatusText: String = "Auto schedule is running",
    val outputHintText: String = "Auto power preview · drag any slider to override"
) {
    val isAutoMode: Boolean
        get() = controlMode == ManualLightControlMode.AUTO

    val isManualOverrideActive: Boolean
        get() = controlMode != ManualLightControlMode.AUTO
}
