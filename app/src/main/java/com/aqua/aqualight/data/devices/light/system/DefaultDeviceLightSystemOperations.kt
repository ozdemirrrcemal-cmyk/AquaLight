package com.aqua.aqualight.data.devices.light.system

import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.light.system.DeviceLightFanMode
import com.aqua.aqualight.application.devices.light.system.DeviceLightSystemCondition
import com.aqua.aqualight.application.devices.light.system.DeviceLightSystemFailure
import com.aqua.aqualight.application.devices.light.system.DeviceLightSystemFanSnapshot
import com.aqua.aqualight.application.devices.light.system.DeviceLightSystemMutationResult
import com.aqua.aqualight.application.devices.light.system.DeviceLightSystemOperations
import com.aqua.aqualight.application.devices.light.system.DeviceLightSystemReadResult
import com.aqua.aqualight.application.devices.light.system.DeviceLightSystemSettings
import com.aqua.aqualight.application.devices.light.system.DeviceLightSystemSnapshot
import com.aqua.aqualight.application.devices.light.system.DeviceLightSystemTemperaturePolicy
import com.aqua.aqualight.data.devices.DefaultDeviceRootOperations
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.modules.DeviceRuntimeModuleProvider
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightTemperatureProtectionSetPayload
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightTemperatureProtectionStatus
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightThermalConfigApplyPayload
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightThermalConfigApplyResult
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightThermalFan
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightThermalMode
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightThermalRuntimeState
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightThermalStatus
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightThermalV1Contract
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf

/** Owner-scoped adapter over the authoritative Light thermal and protection runtime owners. */
internal class DefaultDeviceLightSystemOperations(
    private val devicesRepository: DevicesRepository
) : DeviceLightSystemOperations {

    private val rootOperations = DefaultDeviceRootOperations(devicesRepository)

    override fun observe(deviceUid: String): Flow<DeviceLightSystemReadResult> {
        val resolution = resolve(deviceUid)
        return when (resolution) {
            is SystemRuntimeResolution.Failed -> flowOf(readFailure(resolution.failure))
            is SystemRuntimeResolution.Ready -> combine(
                rootOperations.observe(resolution.deviceUid.value),
                resolution.modules.lightThermal.states,
                resolution.modules.lightTemperatureProtection.states
            ) { root, thermalStates, protectionStates ->
                project(
                    deviceUid = resolution.deviceUid,
                    root = root,
                    thermal = thermalStates[resolution.deviceUid],
                    protection = protectionStates[resolution.deviceUid]
                )
            }.distinctUntilChanged()
        }
    }

    override fun current(deviceUid: String): DeviceLightSystemReadResult =
        when (val resolution = resolve(deviceUid)) {
            is SystemRuntimeResolution.Failed -> readFailure(resolution.failure)
            is SystemRuntimeResolution.Ready -> resolution.projectCurrent()
        }

    override suspend fun refresh(deviceUid: String): DeviceLightSystemReadResult =
        when (val resolution = resolve(deviceUid)) {
            is SystemRuntimeResolution.Failed -> readFailure(resolution.failure)
            is SystemRuntimeResolution.Ready -> refresh(resolution)
        }

    override suspend fun save(
        deviceUid: String,
        settings: DeviceLightSystemSettings
    ): DeviceLightSystemMutationResult = when (val resolution = resolve(deviceUid)) {
        is SystemRuntimeResolution.Failed -> mutationFailure(resolution.failure)
        is SystemRuntimeResolution.Ready -> save(resolution, settings)
    }

    private suspend fun refresh(
        resolution: SystemRuntimeResolution.Ready
    ): DeviceLightSystemReadResult {
        devicesRepository.connectRuntime(resolution.deviceUid).onFailure {
            return readFailure(DeviceLightSystemFailure.NOT_CONNECTED)
        }
        val thermal = resolution.modules.lightThermal.requestStatus(resolution.deviceUid)
        if (thermal !is DeviceRuntimeCommandOutcome.Success) {
            return readFailure(thermal.toSystemFailure())
        }
        val protection = resolution.modules.lightTemperatureProtection.requestStatus(
            resolution.deviceUid
        )
        if (protection !is DeviceRuntimeCommandOutcome.Success) {
            return readFailure(protection.toSystemFailure())
        }
        return resolution.projectCurrent()
    }

    private suspend fun save(
        resolution: SystemRuntimeResolution.Ready,
        settings: DeviceLightSystemSettings
    ): DeviceLightSystemMutationResult {
        val current = resolution.projectCurrent()
        val snapshot = (current as? DeviceLightSystemReadResult.Available)?.snapshot
            ?: return mutationFailure(
                (current as DeviceLightSystemReadResult.Failed).failure
            )
        if (!snapshot.accepts(settings)) {
            return mutationFailure(DeviceLightSystemFailure.INVALID_DATA)
        }

        val thermalOutcome = resolution.modules.lightThermal.applyConfig(
            resolution.deviceUid,
            DeviceLightThermalConfigApplyPayload(
                mode = settings.mode.toRuntimeMode(),
                minTemperatureC = settings.startTemperatureCelsius.toDouble(),
                maxTemperatureC = settings.fullSpeedTemperatureCelsius.toDouble(),
                save = true
            )
        )
        val thermalResult = (thermalOutcome as? DeviceRuntimeCommandOutcome.Success)?.value
        if (thermalResult == null || !thermalResult.persisted()) {
            return mutationFailure(thermalOutcome.toSystemFailure())
        }

        val protectionOutcome = resolution.modules.lightTemperatureProtection.setThreshold(
            resolution.deviceUid,
            DeviceLightTemperatureProtectionSetPayload(
                thresholdC = settings.protectionThresholdCelsius.toDouble(),
                save = true
            )
        )
        val protectionResult = (protectionOutcome as? DeviceRuntimeCommandOutcome.Success)?.value
        if (
            protectionResult == null ||
            !protectionResult.saveRequested ||
            !protectionResult.saved
        ) {
            resolution.modules.lightThermal.requestStatus(resolution.deviceUid)
            resolution.modules.lightTemperatureProtection.requestStatus(resolution.deviceUid)
            return mutationFailure(
                failure = protectionOutcome.toSystemFailure(),
                partialApplyPossible = true
            )
        }

        return when (val projected = resolution.projectCurrent()) {
            is DeviceLightSystemReadResult.Available ->
                DeviceLightSystemMutationResult.Success(projected.snapshot)
            is DeviceLightSystemReadResult.Failed -> mutationFailure(projected.failure)
        }
    }

    private fun resolve(deviceUid: String): SystemRuntimeResolution {
        val uid = deviceUid.trim().takeIf(String::isNotBlank)?.let(::DeviceUid)
        val root = uid?.let { rootOperations.current(it.value) }
        return when {
            uid == null || root == null ->
                SystemRuntimeResolution.Failed(DeviceLightSystemFailure.UNAVAILABLE)
            !root.supportsLightSystem() ->
                SystemRuntimeResolution.Failed(DeviceLightSystemFailure.UNSUPPORTED)
            else -> devicesRepository.runtimeModules()?.let { modules ->
                SystemRuntimeResolution.Ready(uid, root, modules)
            } ?: SystemRuntimeResolution.Failed(DeviceLightSystemFailure.UNAVAILABLE)
        }
    }
}

private sealed interface SystemRuntimeResolution {
    data class Ready(
        val deviceUid: DeviceUid,
        val root: DeviceRootSnapshot,
        val modules: DeviceRuntimeModuleProvider
    ) : SystemRuntimeResolution {
        fun projectCurrent(): DeviceLightSystemReadResult = project(
            deviceUid = deviceUid,
            root = root,
            thermal = modules.lightThermal.states.value[deviceUid],
            protection = modules.lightTemperatureProtection.currentStatus(deviceUid)
        )
    }

    data class Failed(val failure: DeviceLightSystemFailure) : SystemRuntimeResolution
}

private fun project(
    deviceUid: DeviceUid,
    root: DeviceRootSnapshot?,
    thermal: DeviceLightThermalRuntimeState?,
    protection: DeviceLightTemperatureProtectionStatus?
): DeviceLightSystemReadResult {
    if (root == null || !root.supportsLightSystem()) {
        return readFailure(DeviceLightSystemFailure.UNSUPPORTED)
    }
    val status = thermal?.status ?: return readFailure(DeviceLightSystemFailure.UNAVAILABLE)
    if (status.productKey != root.productKey || protection?.supported != true) {
        return readFailure(DeviceLightSystemFailure.INVALID_DATA)
    }
    return runCatching {
        DeviceLightSystemReadResult.Available(
            status.toSystemSnapshot(deviceUid, thermal.telemetry, protection)
        )
    }.getOrElse { readFailure(DeviceLightSystemFailure.INVALID_DATA) }
}

private fun DeviceLightThermalStatus.toSystemSnapshot(
    deviceUid: DeviceUid,
    telemetry: com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightThermalTelemetry?,
    protectionStatus: DeviceLightTemperatureProtectionStatus
): DeviceLightSystemSnapshot {
    val liveTemperature = telemetry?.temperature ?: temperature
    val liveFans = telemetry?.fans ?: fans
    require(liveFans.size == DeviceLightThermalV1Contract.FAN_OUTPUT_CAPACITY)
    val sensorHealthy = liveTemperature.readingValid &&
        liveTemperature.temperatureC != null &&
        !(telemetry?.sensorFailSafeActive ?: runtime.sensorFailSafeActive)
    val cycleHealthy = telemetry?.automaticOutputCycleHealthy
        ?: runtime.automaticOutputCycleHealthy
    val protection = protectionStatus.temperatureProtection
    require(protection.supported && protection.thresholdEditable)
    val protectionThreshold = protection.thresholdC.requireExactInt()
    val protectionMinimum = protection.minimumC.requireExactInt()
    val protectionMaximum = protection.maximumC.requireExactInt()
    val protectionActive = telemetry?.lightProtection?.active
        ?: (lightProtection.active || protection.active)
    val fanSnapshots = liveFans.sortedBy(DeviceLightThermalFan::fanKey).map { fan ->
        DeviceLightSystemFanSnapshot(
            key = fan.fanKey,
            percent = fan.percentNow.roundToInt().coerceIn(
                DeviceLightThermalV1Contract.FAN_PERCENT_MINIMUM.roundToInt(),
                DeviceLightThermalV1Contract.FAN_PERCENT_MAXIMUM.roundToInt()
            ),
            healthy = fan.hardware.pwmOutputHealth == PWM_HEALTH_OK &&
                fan.hardware.health != HARDWARE_FAULT
        )
    }
    val condition = when {
        !sensorHealthy -> DeviceLightSystemCondition.SENSOR_FAIL_SAFE
        !cycleHealthy || fanSnapshots.any { fan -> !fan.healthy } ->
            DeviceLightSystemCondition.FAN_FAULT
        protectionActive -> DeviceLightSystemCondition.PROTECTION_ACTIVE
        else -> DeviceLightSystemCondition.NORMAL
    }
    return DeviceLightSystemSnapshot(
        deviceUid = deviceUid.value,
        temperatureCelsius = liveTemperature.temperatureC,
        condition = condition,
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
        protectionThresholdCelsius = protectionThreshold,
        protectionThresholdPolicy = DeviceLightSystemTemperaturePolicy(
            minimum = protectionMinimum,
            maximum = protectionMaximum
        ),
        protectionActive = protectionActive
    )
}

private fun DeviceLightSystemSnapshot.accepts(settings: DeviceLightSystemSettings): Boolean =
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

private fun DeviceLightFanMode.toRuntimeMode(): DeviceLightThermalMode = when (this) {
    DeviceLightFanMode.AUTOMATIC -> DeviceLightThermalMode.AUTO
    DeviceLightFanMode.ON -> DeviceLightThermalMode.ON
    DeviceLightFanMode.OFF -> DeviceLightThermalMode.OFF
}

private fun DeviceRootSnapshot.supportsLightSystem(): Boolean =
    catalogState == DeviceRootCatalogState.VALID &&
        family == OwnerDeviceFamily.LIGHT &&
        productKey == DeviceLightThermalV1Contract.PRODUCT_KEY &&
        fanOutputCount == DeviceLightThermalV1Contract.FAN_OUTPUT_CAPACITY &&
        temperatureSensorCount == DeviceLightThermalV1Contract.TEMPERATURE_SENSOR_CAPACITY &&
        LIGHT_FAN_CONTROL in supportedFeatures &&
        LIGHT_TEMPERATURE_PROTECTION in supportedFeatures

private fun Double?.requireExactInt(): Int {
    val value = requireNotNull(this).also { require(it.isFinite()) }
    val rounded = value.roundToInt()
    require(abs(value - rounded.toDouble()) <= TEMPERATURE_EPSILON)
    return rounded
}

private fun DeviceLightThermalConfigApplyResult.persisted(): Boolean =
    saveRequested && saved

private fun DeviceRuntimeCommandOutcome<*>.toSystemFailure(): DeviceLightSystemFailure = when (this) {
    is DeviceRuntimeCommandOutcome.NotConnected,
    is DeviceRuntimeCommandOutcome.NotAuthenticated -> DeviceLightSystemFailure.NOT_CONNECTED
    is DeviceRuntimeCommandOutcome.UnsupportedByDevice -> DeviceLightSystemFailure.UNSUPPORTED
    is DeviceRuntimeCommandOutcome.FirmwareError -> DeviceLightSystemFailure.REJECTED
    is DeviceRuntimeCommandOutcome.ProtocolError -> DeviceLightSystemFailure.INVALID_DATA
    is DeviceRuntimeCommandOutcome.SendFailed,
    is DeviceRuntimeCommandOutcome.Timeout,
    is DeviceRuntimeCommandOutcome.Cancelled -> DeviceLightSystemFailure.UNAVAILABLE
    is DeviceRuntimeCommandOutcome.Success -> DeviceLightSystemFailure.INVALID_DATA
}

private fun readFailure(failure: DeviceLightSystemFailure) =
    DeviceLightSystemReadResult.Failed(failure)

private fun mutationFailure(
    failure: DeviceLightSystemFailure,
    partialApplyPossible: Boolean = false
) = DeviceLightSystemMutationResult.Failed(failure, partialApplyPossible)

private const val LIGHT_FAN_CONTROL = "LIGHT_FAN_CONTROL"
private const val LIGHT_TEMPERATURE_PROTECTION = "LIGHT_TEMPERATURE_PROTECTION"
private const val PWM_HEALTH_OK = "OK"
private const val HARDWARE_FAULT = "HARDWARE_FAULT"
private const val TEMPERATURE_EPSILON = 0.000_001
