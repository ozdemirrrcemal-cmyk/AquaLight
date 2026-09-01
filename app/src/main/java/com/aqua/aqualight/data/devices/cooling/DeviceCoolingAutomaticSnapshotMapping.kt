package com.aqua.aqualight.data.devices.cooling

import com.aqua.aqualight.application.devices.cooling.DEVICE_COOLING_FAN_PERCENT_MAXIMUM
import com.aqua.aqualight.application.devices.cooling.DEVICE_COOLING_FAN_PERCENT_MINIMUM
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAutomaticFailure
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAutomaticSettingsSnapshot
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAutomaticTemperaturePolicy
import com.aqua.aqualight.data.devices.runtime.modules.cooling.DeviceCoolingRuntimeContract
import com.aqua.aqualight.data.devices.runtime.modules.cooling.DeviceCoolingStatus
import kotlin.math.abs

internal fun DeviceCoolingStatus?.toAutomaticSnapshot(): DeviceCoolingAutomaticSettingsSnapshot {
    val status = this ?: return DeviceCoolingAutomaticSettingsSnapshot()
    val temperature = status.temperature
        .takeIf { snapshot -> snapshot.readingValid }
        ?.temperatureC
        ?.takeIf(Double::isFinite)
    val fanPercent = status.fans
        .firstOrNull()
        ?.percentNow
        ?.takeIf(Double::isFinite)
        ?.coerceIn(
            DEVICE_COOLING_FAN_PERCENT_MINIMUM.toDouble(),
            DEVICE_COOLING_FAN_PERCENT_MAXIMUM.toDouble()
        )
    val available = status.supported && status.fanSupported && status.temperatureSupported
    return DeviceCoolingAutomaticSettingsSnapshot(
        available = available,
        loaded = true,
        editable = available && status.automaticWriteFailure() == null,
        startTemperatureC = status.minTemperatureC.takeIf(Double::isFinite),
        maximumSpeedTemperatureC = status.maxTemperatureC.takeIf(Double::isFinite),
        tankTemperatureC = temperature,
        fanPercentNow = fanPercent,
        policy = AUTOMATIC_TEMPERATURE_POLICY.takeIf { available }
    )
}

internal fun DeviceCoolingStatus.automaticWriteFailure(): DeviceCoolingAutomaticFailure? = when {
    !supported || !fanSupported || !temperatureSupported -> DeviceCoolingAutomaticFailure.Unsupported
    runtime.readOnly -> DeviceCoolingAutomaticFailure.ReadOnly
    !runtime.supportsConfigApply || !runtime.supportsTemperatureRange ->
        DeviceCoolingAutomaticFailure.Unsupported
    else -> null
}

internal fun isRequestedAutomaticRangeValid(
    startTemperatureC: Double,
    maximumSpeedTemperatureC: Double
): Boolean {
    val policy = AUTOMATIC_TEMPERATURE_POLICY
    return startTemperatureC.isFinite() &&
        maximumSpeedTemperatureC.isFinite() &&
        startTemperatureC in policy.startMinimumC..policy.startMaximumC &&
        maximumSpeedTemperatureC in policy.maximumSpeedMinimumC..policy.maximumSpeedMaximumC &&
        maximumSpeedTemperatureC - startTemperatureC >= policy.minimumGapC - RANGE_EPSILON &&
        isAlignedToAutomaticStep(startTemperatureC, policy.startMinimumC, policy.stepC) &&
        isAlignedToAutomaticStep(
            maximumSpeedTemperatureC,
            policy.maximumSpeedMinimumC,
            policy.stepC
        )
}

private fun isAlignedToAutomaticStep(value: Double, origin: Double, step: Double): Boolean {
    val units = (value - origin) / step
    return abs(units - kotlin.math.round(units)) <= RANGE_EPSILON
}

private val AUTOMATIC_TEMPERATURE_POLICY = DeviceCoolingAutomaticTemperaturePolicy(
    startMinimumC = DeviceCoolingRuntimeContract.Limit.LOWEST_MIN_C,
    startMaximumC = DeviceCoolingRuntimeContract.Limit.HIGHEST_MIN_C,
    maximumSpeedMinimumC = DeviceCoolingRuntimeContract.Limit.LOWEST_MAX_C,
    maximumSpeedMaximumC = DeviceCoolingRuntimeContract.Limit.HIGHEST_MAX_C,
    stepC = 0.5,
    minimumGapC = 0.5
)

private const val RANGE_EPSILON = 0.000_001
