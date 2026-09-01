package com.aqua.aqualight.data.devices.cooling

import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAutomaticCommandResult
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAutomaticFailure
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAutomaticSettingsOperations
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAutomaticSettingsSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.modules.cooling.DeviceCoolingRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.cooling.DeviceCoolingStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Firmware-backed automatic Cooling adapter. No local shadow configuration is persisted here. */
internal class DefaultDeviceCoolingAutomaticSettingsOperations(
    private val devicesRepository: DevicesRepository
) : DeviceCoolingAutomaticSettingsOperations {

    override fun observeAutomaticSettings(
        deviceUid: String
    ): Flow<DeviceCoolingAutomaticSettingsSnapshot> = resolveRuntime(deviceUid)
        ?.let { access ->
            access.runtime.states.map { states ->
                states[access.deviceUid]?.status.toAutomaticSnapshot()
            }
        }
        ?.distinctUntilChanged()
        ?: flowOf(DeviceCoolingAutomaticSettingsSnapshot())

    override fun currentAutomaticSettings(
        deviceUid: String
    ): DeviceCoolingAutomaticSettingsSnapshot = resolveRuntime(deviceUid)
        ?.let { access -> access.runtime.states.value[access.deviceUid]?.status }
        .toAutomaticSnapshot()

    override suspend fun refreshAutomaticSettings(
        deviceUid: String
    ): DeviceCoolingAutomaticCommandResult = resolveRuntime(deviceUid)
        ?.let { access -> refreshResolved(access) }
        ?: automaticFailure(DeviceCoolingAutomaticFailure.Unavailable)

    override suspend fun saveAutomaticTemperatureRange(
        deviceUid: String,
        startTemperatureC: Double,
        maximumSpeedTemperatureC: Double
    ): DeviceCoolingAutomaticCommandResult = if (
        !isRequestedAutomaticRangeValid(startTemperatureC, maximumSpeedTemperatureC)
    ) {
        automaticFailure(DeviceCoolingAutomaticFailure.InvalidConfiguration)
    } else {
        resolveRuntime(deviceUid)
            ?.let { access ->
                saveResolved(
                    access = access,
                    startTemperatureC = startTemperatureC,
                    maximumSpeedTemperatureC = maximumSpeedTemperatureC
                )
            }
            ?: automaticFailure(DeviceCoolingAutomaticFailure.Unavailable)
    }

    private suspend fun refreshResolved(
        access: AutomaticRuntimeAccess
    ): DeviceCoolingAutomaticCommandResult = if (
        devicesRepository.connectRuntime(access.deviceUid).isFailure
    ) {
        automaticFailure(DeviceCoolingAutomaticFailure.NotConnected)
    } else {
        requestAutomaticStatusWithRetry {
            access.runtime.requestStatus(access.deviceUid)
        }.toAutomaticRefreshResult()
    }

    private suspend fun saveResolved(
        access: AutomaticRuntimeAccess,
        startTemperatureC: Double,
        maximumSpeedTemperatureC: Double
    ): DeviceCoolingAutomaticCommandResult = if (
        devicesRepository.connectRuntime(access.deviceUid).isFailure
    ) {
        automaticFailure(DeviceCoolingAutomaticFailure.NotConnected)
    } else {
        when (val statusResult = access.loadStatus()) {
            is AutomaticStatusResult.Failed -> automaticFailure(statusResult.failure)
            is AutomaticStatusResult.Loaded -> statusResult.status
                .automaticWriteFailure()
                ?.let(::automaticFailure)
                ?: access.runtime.setTemperatureRange(
                    deviceUid = access.deviceUid,
                    minTemperatureC = startTemperatureC,
                    maxTemperatureC = maximumSpeedTemperatureC,
                    save = true
                ).toAutomaticSaveResult(
                    requestedStartC = startTemperatureC,
                    requestedMaximumC = maximumSpeedTemperatureC
                )
        }
    }

    private fun resolveRuntime(deviceUid: String): AutomaticRuntimeAccess? = deviceUid
        .trim()
        .takeIf(String::isNotBlank)
        ?.let(::DeviceUid)
        ?.takeIf { uid -> devicesRepository.currentDevice(uid) != null }
        ?.let { uid ->
            devicesRepository.runtimeModules()?.cooling?.let { runtime ->
                AutomaticRuntimeAccess(deviceUid = uid, runtime = runtime)
            }
        }
}

internal data class AutomaticRuntimeAccess(
    val deviceUid: DeviceUid,
    val runtime: DeviceCoolingRuntimeRepository
) {
    suspend fun loadStatus(): AutomaticStatusResult = runtime.states.value[deviceUid]?.status
        ?.let(AutomaticStatusResult::Loaded)
        ?: when (val outcome = requestAutomaticStatusWithRetry { runtime.requestStatus(deviceUid) }) {
            is DeviceRuntimeCommandOutcome.Success -> AutomaticStatusResult.Loaded(outcome.value)
            else -> AutomaticStatusResult.Failed(outcome.toAutomaticFailure())
        }
}

internal sealed interface AutomaticStatusResult {
    data class Loaded(val status: DeviceCoolingStatus) : AutomaticStatusResult
    data class Failed(val failure: DeviceCoolingAutomaticFailure) : AutomaticStatusResult
}
