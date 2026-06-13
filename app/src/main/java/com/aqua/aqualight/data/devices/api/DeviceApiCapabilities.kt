package com.aqua.aqualight.data.devices.api

data class DeviceApiCapabilities(
    val supportsIdentityRead: Boolean = false,
    val supportsLiveStatus: Boolean = false,
    val supportsProgramRead: Boolean = false,
    val supportsProgramWrite: Boolean = false,
    val supportsManualControl: Boolean = false,
    val supportsAutomation: Boolean = false,
    val supportsCalibration: Boolean = false,
    val supportsSchedules: Boolean = false,
    val supportsSettingsRead: Boolean = false,
    val supportsSettingsWrite: Boolean = false
) {
    companion object {
        val None = DeviceApiCapabilities()

        val V1Default = DeviceApiCapabilities(
            supportsIdentityRead = true,
            supportsLiveStatus = true,
            supportsProgramRead = true,
            supportsProgramWrite = true,
            supportsManualControl = true,
            supportsAutomation = true,
            supportsCalibration = true,
            supportsSchedules = true,
            supportsSettingsRead = true,
            supportsSettingsWrite = true
        )
    }
}
