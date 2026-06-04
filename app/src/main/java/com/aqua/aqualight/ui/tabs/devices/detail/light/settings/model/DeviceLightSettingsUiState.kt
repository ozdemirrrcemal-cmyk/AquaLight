package com.aqua.aqualight.ui.tabs.devices.detail.light.settings.model

data class DeviceLightSettingsUiState(
    val deviceName: String = "WRGB Pro",
    val deviceType: String = "WRGB Light Controller",
    val deviceModel: String = "AquaLight WRGB Pro",
    val firmwareVersion: String = "v1.0.0",
    val connectionState: String = "Online",

    val deviceTime: String = "15:42",
    val phoneTime: String = "15:42",
    val lastSyncTime: String = "Today 15:40",

    val temperatureProtectionEnabled: Boolean = true,
    val limitTemperatureCelsius: Int = 50,
    val lightReductionPercent: Int = 70,
    val recoveryIntervalSeconds: Int = 60,

    val coolingMode: String = "Auto default",
    val fanStartTemperatureCelsius: Int = 30,
    val fanFullSpeedTemperatureCelsius: Int = 50
)