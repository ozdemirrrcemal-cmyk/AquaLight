package com.aqua.aqualight.application.devices.cooling.control

import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTelemetrySnapshot

private const val MINIMUM_PERCENT = 0
private const val MAXIMUM_PERCENT = 100

data class DeviceCoolingControlSnapshot(
    val mode: DeviceCoolingControlMode,
    val manualFanPercent: Int?,
    val actualFanPercent: Int?,
    val tankTemperatureC: Double?,
    val capabilities: DeviceCoolingControlCapabilities,
    val telemetry: DeviceCoolingTelemetrySnapshot? = null,
    val operatingState: DeviceCoolingOperatingState? = null,
    val controlReason: DeviceCoolingControlReason = DeviceCoolingControlReason.UNKNOWN,
    val targetFanPercent: Int? = null,
    val manualActive: Boolean? = null,
    val programRuntime: DeviceCoolingProgramRuntimeSnapshot? = null
) {
    init {
        require(mode in capabilities.supportedModes)
        require(manualFanPercent == null || manualFanPercent in MINIMUM_PERCENT..MAXIMUM_PERCENT)
        require(actualFanPercent == null || actualFanPercent in MINIMUM_PERCENT..MAXIMUM_PERCENT)
        require(targetFanPercent == null || targetFanPercent in MINIMUM_PERCENT..MAXIMUM_PERCENT)
        require(tankTemperatureC == null || tankTemperatureC.isFinite())
    }
}
