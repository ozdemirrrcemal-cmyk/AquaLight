package com.aqua.aqualight.application.devices.cooling.control

import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTelemetrySnapshot

private const val MINIMUM_PERCENT = 0
private const val MAXIMUM_PERCENT = 100

data class DeviceCoolingControlSnapshot(
    val mode: DeviceCoolingControlMode,
    val manualFanPercent: Int?,
    /** Firmware-reported physical output. Automatic control is intentionally continuous. */
    val actualFanPercent: Double?,
    val tankTemperatureC: Double?,
    val capabilities: DeviceCoolingControlCapabilities,
    val telemetry: DeviceCoolingTelemetrySnapshot? = null,
    val operatingState: DeviceCoolingOperatingState? = null,
    val controlReason: DeviceCoolingControlReason = DeviceCoolingControlReason.UNKNOWN,
    /** Firmware-owned control target; never quantized to the writable one-percent step. */
    val targetFanPercent: Double? = null,
    val manualActive: Boolean? = null,
    val programRuntime: DeviceCoolingProgramRuntimeSnapshot? = null
) {
    init {
        require(mode in capabilities.supportedModes)
        require(manualFanPercent == null || manualFanPercent in MINIMUM_PERCENT..MAXIMUM_PERCENT)
        require(actualFanPercent == null || actualFanPercent.isValidRuntimePercent())
        require(targetFanPercent == null || targetFanPercent.isValidRuntimePercent())
        require(tankTemperatureC == null || tankTemperatureC.isFinite())
    }
}

private fun Double.isValidRuntimePercent(): Boolean =
    isFinite() && this in MINIMUM_PERCENT.toDouble()..MAXIMUM_PERCENT.toDouble()
