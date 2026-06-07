package com.aqua.aqualight.ui.tabs.devices.detail.light.settings.model

data class DeviceLightSettingsUiState(
    val deviceName: String = "—",
    val deviceType: String = "—",
    val firmwareVersion: String = "—",
    val deviceIp: String = "—",
    val serialNumber: String = "—",

    val deviceTime: String = "--:--",
    val phoneTime: String = "--:--",
    val lastSyncTime: String = "Never",

    val thermalProtectionStatusText: String = "SYNC",
    val currentTemperatureText: String = "-- °C",
    val temperatureSensorCount: Int = 0,
    val limitTemperatureCelsius: Int = 50,
    val lightReductionPercent: Int = 70,
    val recoveryIntervalSeconds: Int = 60,

    val coolingStatusText: String = "Syncing",
    val coolingFansText: String = "Syncing",
    val coolingMode: String = "Auto",
    val coolingModeEnabled: Boolean = true,
    val coolingFanCount: Int = 0,
    val fanStartTemperatureCelsius: Int = 30,
    val fanFullSpeedTemperatureCelsius: Int = 50
)