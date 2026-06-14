package com.aqua.aqualight.ui.tabs.devices.detail.light.manual.model

data class ManualLightUiState(
    val controlMode: ManualLightControlMode = ManualLightControlMode.AUTO,

    /**
     * Legacy booleans kept so the UI can stay simple while the screen moves to
     * a proper AUTO / MANUAL_OVERRIDE / SCENE_OVERRIDE model.
     */
    val isManualMode: Boolean = false,
    val isManualScene: Boolean = false,
    val isPowerOn: Boolean = false,

    val activeSceneName: String? = null,
    val activeSceneSource: String? = null,

    val masterOutputPercent: Int = 0,

    val red: Int = 0,
    val green: Int = 0,
    val blue: Int = 0,
    val white: Int = 0,

    val redMaxWatts: Double = 0.0,
    val greenMaxWatts: Double = 0.0,
    val blueMaxWatts: Double = 0.0,
    val whiteMaxWatts: Double = 0.0,

    val estimatedPowerWatts: Double = 0.0,
    val hasPowerCalibration: Boolean = false,
    val powerText: String = "0%",

    val savedPresets: List<ManualLightPreset> = emptyList(),

    val isDeviceOnline: Boolean = true,
    val controlsEnabled: Boolean = true,
    val connectionStatusText: String = "Auto schedule is running",
    val outputHintText: String = "Auto output preview · drag any slider to override"
) {
    val isAutoMode: Boolean
        get() = controlMode == ManualLightControlMode.AUTO

    val isManualOverrideActive: Boolean
        get() = controlMode != ManualLightControlMode.AUTO
}
