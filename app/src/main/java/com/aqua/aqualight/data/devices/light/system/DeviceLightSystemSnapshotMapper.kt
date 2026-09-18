package com.aqua.aqualight.data.devices.light.system

import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.light.system.DeviceLightFanMode
import com.aqua.aqualight.application.devices.light.system.DeviceLightSystemCondition
import com.aqua.aqualight.application.devices.light.system.DeviceLightSystemFailure
import com.aqua.aqualight.application.devices.light.system.DeviceLightSystemFanSnapshot
import com.aqua.aqualight.application.devices.light.system.DeviceLightSystemReadResult
import com.aqua.aqualight.application.devices.light.system.DeviceLightSystemSettings
import com.aqua.aqualight.application.devices.light.system.DeviceLightSystemSnapshot
import com.aqua.aqualight.application.devices.light.system.DeviceLightSystemTemperaturePolicy
import com.aqua.aqualight.data.devices.light.supportsLightSystem
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightTemperatureProtectionStatus
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightThermalFan
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightThermalMode
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightThermalRuntimeState
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightThermalStatus
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightThermalTemperature
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightThermalTelemetry
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightThermalV1Contract
import kotlin.math.abs
import kotlin.math.roundToInt

internal fun projectLightSystemSnapshot(
    deviceUid: DeviceUid,
    root: DeviceRootSnapshot?,
    thermal: DeviceLightThermalRuntimeState?,
    protection: DeviceLightTemperatureProtectionStatus?,
    firmwareWriteAuthoritative: Boolean
): DeviceLightSystemReadResult {
    val status = thermal?.status
    return when {
        root == null || !root.supportsLightSystem() ->
            systemReadFailure(DeviceLightSystemFailure.UNSUPPORTED)
        status == null -> systemReadFailure(DeviceLightSystemFailure.UNAVAILABLE)
        status.productKey != root.productKey || protection?.supported != true ->
            systemReadFailure(DeviceLightSystemFailure.INVALID_DATA)
        else -> runCatching {
            DeviceLightSystemReadResult.Available(
                status.toSystemSnapshot(
                    deviceUid,
                    thermal.telemetry,
                    protection,
                    firmwareWriteAuthoritative
                )
            )
        }.getOrElse { systemReadFailure(DeviceLightSystemFailure.INVALID_DATA) }
    }
}

private fun DeviceLightThermalStatus.toSystemSnapshot(
    deviceUid: DeviceUid,
    telemetry: DeviceLightThermalTelemetry?,
    protectionStatus: DeviceLightTemperatureProtectionStatus,
    firmwareWriteAuthoritative: Boolean
): DeviceLightSystemSnapshot {
    val liveTemperature = telemetry?.temperature ?: temperature
    val liveFans = telemetry?.fans ?: fans
    require(liveFans.size == DeviceLightThermalV1Contract.FAN_OUTPUT_CAPACITY)
    val sensorHealthy = liveTemperature.isHealthy(
        telemetry?.sensorFailSafeActive ?: runtime.sensorFailSafeActive
    )
    val cycleHealthy = telemetry?.automaticOutputCycleHealthy
        ?: runtime.automaticOutputCycleHealthy
    val protection = protectionStatus.temperatureProtection
    require(protection.supported && protection.thresholdEditable)
    val protectionActive = telemetry?.lightProtection?.active
        ?: (lightProtection.active || protection.active)
    val fanSnapshots = liveFans
        .sortedBy(DeviceLightThermalFan::fanKey)
        .map(DeviceLightThermalFan::toSystemFanSnapshot)
    return DeviceLightSystemSnapshot(
        deviceUid = deviceUid.value,
        temperatureCelsius = liveTemperature.temperatureC,
        condition = systemCondition(sensorHealthy, cycleHealthy, fanSnapshots, protectionActive),
        sensorHealthy = sensorHealthy,
        fans = fanSnapshots,
        mode = (telemetry?.mode ?: config.mode).toApplicationMode(),
        startTemperatureCelsius = config.minTemperatureC.requireExactInt(),
        fullSpeedTemperatureCelsius = config.maxTemperatureC.requireExactInt(),
        startTemperaturePolicy = DeviceLightSystemTemperaturePolicy(
            minimum = DeviceLightThermalV1Contract.CONFIG_MINIMUM_TEMPERATURE_C.roundToInt(),
            maximum = DeviceLightThermalV1Contract.CONFIG_MAXIMUM_MIN_TEMPERATURE_C.roundToInt()
        ),
        fullSpeedTemperaturePolicy = DeviceLightSystemTemperaturePolicy(
            minimum = DeviceLightThermalV1Contract.CONFIG_MINIMUM_MAX_TEMPERATURE_C.roundToInt(),
            maximum = DeviceLightThermalV1Contract.CONFIG_MAXIMUM_TEMPERATURE_C.roundToInt()
        ),
        protectionThresholdCelsius = protection.thresholdC.requireExactInt(),
        protectionThresholdPolicy = DeviceLightSystemTemperaturePolicy(
            minimum = protection.minimumC.requireExactInt(),
            maximum = protection.maximumC.requireExactInt()
        ),
        protectionActive = protectionActive,
        firmwareWriteAuthoritative = firmwareWriteAuthoritative
    )
}

private fun DeviceLightThermalTemperature.isHealthy(failSafeActive: Boolean): Boolean =
    readingValid && temperatureC != null && !failSafeActive

private fun DeviceLightThermalFan.toSystemFanSnapshot() = DeviceLightSystemFanSnapshot(
    key = fanKey,
    percent = percentNow.roundToInt().coerceIn(
        DeviceLightThermalV1Contract.FAN_PERCENT_MINIMUM.roundToInt(),
        DeviceLightThermalV1Contract.FAN_PERCENT_MAXIMUM.roundToInt()
    ),
    healthy = hardware.pwmOutputHealth == PWM_HEALTH_OK && hardware.health != HARDWARE_FAULT
)

private fun systemCondition(
    sensorHealthy: Boolean,
    cycleHealthy: Boolean,
    fans: List<DeviceLightSystemFanSnapshot>,
    protectionActive: Boolean
): DeviceLightSystemCondition = when {
    !sensorHealthy -> DeviceLightSystemCondition.SENSOR_FAIL_SAFE
    !cycleHealthy || fans.any { fan -> !fan.healthy } -> DeviceLightSystemCondition.FAN_FAULT
    protectionActive -> DeviceLightSystemCondition.PROTECTION_ACTIVE
    else -> DeviceLightSystemCondition.NORMAL
}

internal fun DeviceLightSystemSnapshot.accepts(settings: DeviceLightSystemSettings): Boolean =
    settings.startTemperatureCelsius in startTemperaturePolicy.minimum..
        startTemperaturePolicy.maximum &&
        settings.fullSpeedTemperatureCelsius in fullSpeedTemperaturePolicy.minimum..
        fullSpeedTemperaturePolicy.maximum &&
        settings.startTemperatureCelsius < settings.fullSpeedTemperatureCelsius &&
        settings.protectionThresholdCelsius in protectionThresholdPolicy.minimum..
        protectionThresholdPolicy.maximum

private fun DeviceLightThermalMode.toApplicationMode(): DeviceLightFanMode = when (this) {
    DeviceLightThermalMode.AUTO -> DeviceLightFanMode.AUTOMATIC
    DeviceLightThermalMode.ON -> DeviceLightFanMode.ON
    DeviceLightThermalMode.OFF -> DeviceLightFanMode.OFF
}

private fun Double?.requireExactInt(): Int {
    val value = requireNotNull(this).also { require(it.isFinite()) }
    val rounded = value.roundToInt()
    require(abs(value - rounded.toDouble()) <= TEMPERATURE_EPSILON)
    return rounded
}

private fun systemReadFailure(failure: DeviceLightSystemFailure) =
    DeviceLightSystemReadResult.Failed(failure)

private const val PWM_HEALTH_OK = "OK"
private const val HARDWARE_FAULT = "HARDWARE_FAULT"
private const val TEMPERATURE_EPSILON = 0.000_001
