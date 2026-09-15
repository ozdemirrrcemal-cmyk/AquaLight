package com.aqua.aqualight.data.devices.light.system

import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.light.system.DeviceLightFanMode
import com.aqua.aqualight.application.devices.light.system.DeviceLightSystemFailure
import com.aqua.aqualight.application.devices.light.system.DeviceLightSystemMutationResult
import com.aqua.aqualight.application.devices.light.system.DeviceLightSystemOperations
import com.aqua.aqualight.application.devices.light.system.DeviceLightSystemReadResult
import com.aqua.aqualight.application.devices.light.system.DeviceLightSystemSettings
import com.aqua.aqualight.data.devices.DefaultDeviceRootOperations
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.modules.DeviceRuntimeModuleProvider
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightTemperatureProtectionSetPayload
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightThermalConfigApplyPayload
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightThermalConfigApplyResult
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightThermalMode
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
                projectLightSystemSnapshot(
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
        val connection = devicesRepository.connectRuntime(resolution.deviceUid)
        return if (connection.isFailure) {
            readFailure(DeviceLightSystemFailure.NOT_CONNECTED)
        } else {
            val thermal = resolution.modules.lightThermal.requestStatus(resolution.deviceUid)
            if (thermal !is DeviceRuntimeCommandOutcome.Success) {
                readFailure(thermal.toSystemFailure())
            } else {
                val protection = resolution.modules.lightTemperatureProtection.requestStatus(
                    resolution.deviceUid
                )
                if (protection is DeviceRuntimeCommandOutcome.Success) {
                    resolution.projectCurrent()
                } else {
                    readFailure(protection.toSystemFailure())
                }
            }
        }
    }

    private suspend fun save(
        resolution: SystemRuntimeResolution.Ready,
        settings: DeviceLightSystemSettings
    ): DeviceLightSystemMutationResult = when (val current = resolution.projectCurrent()) {
        is DeviceLightSystemReadResult.Failed -> mutationFailure(current.failure)
        is DeviceLightSystemReadResult.Available -> saveAvailable(
            resolution = resolution,
            settings = settings,
            current = current
        )
    }

    private suspend fun saveAvailable(
        resolution: SystemRuntimeResolution.Ready,
        settings: DeviceLightSystemSettings,
        current: DeviceLightSystemReadResult.Available
    ): DeviceLightSystemMutationResult = if (!current.snapshot.accepts(settings)) {
        mutationFailure(DeviceLightSystemFailure.INVALID_DATA)
    } else {
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
            mutationFailure(thermalOutcome.toSystemFailure())
        } else {
            saveProtection(resolution, settings)
        }
    }

    private suspend fun saveProtection(
        resolution: SystemRuntimeResolution.Ready,
        settings: DeviceLightSystemSettings
    ): DeviceLightSystemMutationResult {
        val protectionOutcome = resolution.modules.lightTemperatureProtection.setThreshold(
            resolution.deviceUid,
            DeviceLightTemperatureProtectionSetPayload(
                thresholdC = settings.protectionThresholdCelsius.toDouble(),
                save = true
            )
        )
        val protectionResult = (protectionOutcome as? DeviceRuntimeCommandOutcome.Success)?.value
        return if (
            protectionResult == null ||
            !protectionResult.saveRequested ||
            !protectionResult.saved
        ) {
            resolution.modules.lightThermal.requestStatus(resolution.deviceUid)
            resolution.modules.lightTemperatureProtection.requestStatus(resolution.deviceUid)
            mutationFailure(
                failure = protectionOutcome.toSystemFailure(),
                partialApplyPossible = true
            )
        } else {
            when (val projected = resolution.projectCurrent()) {
                is DeviceLightSystemReadResult.Available ->
                    DeviceLightSystemMutationResult.Success(projected.snapshot)
                is DeviceLightSystemReadResult.Failed -> mutationFailure(projected.failure)
            }
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
        fun projectCurrent(): DeviceLightSystemReadResult = projectLightSystemSnapshot(
            deviceUid = deviceUid,
            root = root,
            thermal = modules.lightThermal.states.value[deviceUid],
            protection = modules.lightTemperatureProtection.currentStatus(deviceUid)
        )
    }

    data class Failed(val failure: DeviceLightSystemFailure) : SystemRuntimeResolution
}

private fun DeviceLightFanMode.toRuntimeMode(): DeviceLightThermalMode = when (this) {
    DeviceLightFanMode.AUTOMATIC -> DeviceLightThermalMode.AUTO
    DeviceLightFanMode.ON -> DeviceLightThermalMode.ON
    DeviceLightFanMode.OFF -> DeviceLightThermalMode.OFF
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
