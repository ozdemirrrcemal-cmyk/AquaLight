package com.aqua.aqualight.application.devices.cooling.control

private const val MINIMUM_PERCENT = 0
private const val MAXIMUM_PERCENT = 100

data class DeviceCoolingControlSnapshot(
    val mode: DeviceCoolingControlMode,
    val manualFanPercent: Int?,
    val actualFanPercent: Int?,
    val tankTemperatureC: Double?,
    val capabilities: DeviceCoolingControlCapabilities
) {
    init {
        require(mode in capabilities.supportedModes)
        require(manualFanPercent == null || manualFanPercent in MINIMUM_PERCENT..MAXIMUM_PERCENT)
        require(actualFanPercent == null || actualFanPercent in MINIMUM_PERCENT..MAXIMUM_PERCENT)
        require(tankTemperatureC == null || tankTemperatureC.isFinite())
    }
}
