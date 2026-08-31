package com.aqua.aqualight.application.devices.cooling.control

data class DeviceCoolingControlSnapshot(
    val mode: DeviceCoolingControlMode,
    val manualFanPercent: Int?,
    val actualFanPercent: Int?,
    val tankTemperatureC: Double?,
    val capabilities: DeviceCoolingControlCapabilities
) {
    init {
        require(mode in capabilities.supportedModes)
        require(manualFanPercent == null || manualFanPercent in 0..100)
        require(actualFanPercent == null || actualFanPercent in 0..100)
        require(tankTemperatureC == null || tankTemperatureC.isFinite())
    }
}
