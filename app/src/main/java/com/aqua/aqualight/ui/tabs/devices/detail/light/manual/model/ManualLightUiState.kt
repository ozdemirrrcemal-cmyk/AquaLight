package com.aqua.aqualight.ui.tabs.devices.detail.light.manual.model

data class ManualLightUiState(
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
    val powerText: String = "-- W",

    val savedPresets: List<ManualLightPreset> = emptyList(),

    val isDeviceOnline: Boolean = false,
    val controlsEnabled: Boolean = false,
    val connectionStatusText: String = "Checking device connection"
)
