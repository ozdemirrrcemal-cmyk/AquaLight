package com.aqua.aqualight.application.devices.cooling.control

private const val MINIMUM_PERCENT = 0
private const val MAXIMUM_PERCENT = 100

data class DeviceCoolingManualFanCapabilities(
    val minimumPercent: Int,
    val maximumPercent: Int,
    val stepPercent: Int?,
    val writable: Boolean
) {
    init {
        require(minimumPercent in MINIMUM_PERCENT..MAXIMUM_PERCENT)
        require(maximumPercent in minimumPercent..MAXIMUM_PERCENT)
        require(stepPercent == null || stepPercent in 1..MAXIMUM_PERCENT)
        require(!writable || stepPercent != null) {
            "A writable manual fan control requires an authoritative step."
        }
    }
}

data class DeviceCoolingControlCapabilities(
    val supportedModes: Set<DeviceCoolingControlMode>,
    val modeSelectionWritable: Boolean,
    val manualFan: DeviceCoolingManualFanCapabilities?
) {
    init {
        require(supportedModes.isNotEmpty())
        require(!modeSelectionWritable || supportedModes.isNotEmpty())
    }
}
