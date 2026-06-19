package com.aqua.aqualight.ui.tabs.devices.detail.light.settings.model

data class DeviceLightSettingsUiState(
    val deviceName: String = "—",
    val deviceType: String = "—",
    val firmwareVersion: String = "—",
    val lastKnownIpText: String = "—",
    val serialNumber: String = "—",
    val productId: String = "",
    val productKey: String = "",
    val skuCode: String = "",
    val setupCode: String = "",
    val deviceUid: String = "",
    val macAddress: String = "",
    val hardwareRevision: String = "",
    val protocolVersion: String = "",

    val deviceTime: String = "--:--",
    val phoneTime: String = "--:--",
    val lastSyncTime: String = "Never",

    val thermalProtectionStatusText: String = "Protected",
    val currentTemperatureText: String = "-- °C",
    val temperatureSensorCount: Int = 0,
    val limitTemperatureCelsius: Int = 50,
    val lightReductionPercent: Int = 70,
    val recoveryIntervalSeconds: Int = 60,

    val coolingStatusText: String = "Syncing",
    val coolingFansText: String = "—",
    val coolingMode: String = "Syncing",
    val coolingModeEnabled: Boolean = false,
    val coolingFanCount: Int = 0,
    val fanStartTemperatureCelsius: Int = 30,
    val fanFullSpeedTemperatureCelsius: Int = 50,

    val isDeviceOnline: Boolean = false,
    val controlsEnabled: Boolean = false,
    val connectionStatusText: String = "Checking device connection"
)
