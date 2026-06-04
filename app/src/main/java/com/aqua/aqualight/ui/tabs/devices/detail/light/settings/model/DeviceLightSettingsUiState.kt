package com.aqua.aqualight.ui.tabs.devices.detail.light.settings.model

data class DeviceLightSettingsUiState(
    val deviceName: String = "—",
    val deviceType: String = "—",
    val deviceModel: String = "—",
    val firmwareVersion: String = "—",
    val connectionState: String = "Offline",

    val deviceTime: String = "--:--",
    val phoneTime: String = "--:--",
    val lastSyncTime: String = "Never",

    // Safe firmware defaults. Later these will be loaded from ESP32.
    val temperatureProtectionEnabled: Boolean = true,
    val limitTemperatureCelsius: Int = 50,
    val lightReductionPercent: Int = 70,
    val recoveryIntervalSeconds: Int = 60,

    // Cooling is device-managed. Later these will be loaded from ESP32.
    val coolingMode: String = "Auto default",
    val fanStartTemperatureCelsius: Int = 30,
    val fanFullSpeedTemperatureCelsius: Int = 50
)