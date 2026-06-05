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

    // Later these will come from ESP32 MPWMChanel.W values.
    val redMaxWatts: Double = 0.0,
    val greenMaxWatts: Double = 0.0,
    val blueMaxWatts: Double = 0.0,
    val whiteMaxWatts: Double = 0.0,

    val estimatedPowerWatts: Double = 0.0,
    val hasPowerCalibration: Boolean = false,

    val previewRed: Int = 80,
    val previewGreen: Int = 140,
    val previewBlue: Int = 255,

    val savedPresets: List<ManualLightPreset> = emptyList(),

    val isDeviceOnline: Boolean = true
)