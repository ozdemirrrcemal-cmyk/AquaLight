package com.aqua.aqualight.data.devices

import com.aqua.aqualight.application.devices.DeviceCoolingFanSnapshot
import com.aqua.aqualight.application.devices.DeviceCoolingModeOption
import com.aqua.aqualight.application.devices.DeviceCoolingOperationResult
import com.aqua.aqualight.application.devices.DeviceCoolingOperations
import com.aqua.aqualight.application.devices.DeviceCoolingSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.modules.cooling.DeviceCoolingFanDisplayNamePayload
import com.aqua.aqualight.data.devices.runtime.modules.cooling.DeviceCoolingMode
import com.aqua.aqualight.data.devices.runtime.modules.cooling.DeviceCoolingRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.cooling.DeviceCoolingRuntimeState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

internal class DefaultDeviceCoolingOperations(
    private val devicesRepository: DevicesRepository
) : DeviceCoolingOperations {
    override fun observeCooling(deviceUid: String): Flow<DeviceCoolingSnapshot?> {
        val uid = deviceUid.toDeviceUidOrNull() ?: return flowOf(null)
        val cooling = devicesRepository.runtimeModules()?.cooling ?: return flowOf(null)
        return cooling.states.map { states -> states[uid]?.toApplicationSnapshot() }
    }

    override fun currentCooling(deviceUid: String): DeviceCoolingSnapshot? {
        val uid = deviceUid.toDeviceUidOrNull() ?: return null
        return devicesRepository.runtimeModules()?.cooling?.states?.value
            ?.get(uid)
            ?.toApplicationSnapshot()
    }

    override suspend fun refresh(deviceUid: String): DeviceCoolingOperationResult =
        withCooling(deviceUid) { uid, cooling -> cooling.requestStatus(uid) }

    override suspend fun setMode(
        deviceUid: String,
        mode: DeviceCoolingModeOption,
        save: Boolean
    ): DeviceCoolingOperationResult = withCooling(deviceUid) { uid, cooling ->
        cooling.setMode(uid, mode.toRuntimeMode(), save)
    }

    override suspend fun setTemperatureRange(
        deviceUid: String,
        minTemperatureC: Double,
        maxTemperatureC: Double,
        save: Boolean
    ): DeviceCoolingOperationResult = withCooling(deviceUid) { uid, cooling ->
        cooling.setTemperatureRange(uid, minTemperatureC, maxTemperatureC, save)
    }

    override suspend fun setFanDisplayName(
        deviceUid: String,
        fanKey: String,
        displayName: String?,
        save: Boolean
    ): DeviceCoolingOperationResult = withCooling(deviceUid) { uid, cooling ->
        cooling.setFanDisplayNames(
            uid,
            listOf(DeviceCoolingFanDisplayNamePayload(fanKey, displayName)),
            save
        )
    }

    private suspend fun withCooling(
        deviceUid: String,
        block: suspend (DeviceUid, DeviceCoolingRuntimeRepository) -> DeviceRuntimeCommandOutcome<*>
    ): DeviceCoolingOperationResult {
        val uid = deviceUid.toDeviceUidOrNull()
            ?: return DeviceCoolingOperationResult.Failed("Device uid is missing.")
        val cooling = devicesRepository.runtimeModules()?.cooling
            ?: return DeviceCoolingOperationResult.NotConnected
        return runCatching { block(uid, cooling) }
            .fold(
                onSuccess = { outcome -> outcome.toApplicationResult() },
                onFailure = { error ->
                    DeviceCoolingOperationResult.Failed(error.message.orEmpty())
                }
            )
    }
}

private fun String.toDeviceUidOrNull(): DeviceUid? =
    trim().takeIf(String::isNotEmpty)?.let(::DeviceUid)

private fun DeviceCoolingRuntimeState.toApplicationSnapshot(): DeviceCoolingSnapshot? {
    val activeStatus = status
    val activeConfig = config
    if (activeStatus == null && activeConfig == null) return null
    val activeFans = activeStatus?.fans ?: activeConfig.orEmptyFans()
    val activeTemperature = temperature ?: activeStatus?.temperature
    return DeviceCoolingSnapshot(
        supported = activeStatus?.supported ?: checkNotNull(activeConfig).supported,
        mode = (activeStatus?.mode ?: checkNotNull(activeConfig).mode).toApplicationMode(),
        minTemperatureC = activeStatus?.minTemperatureC
            ?: checkNotNull(activeConfig).minTemperatureC,
        maxTemperatureC = activeStatus?.maxTemperatureC
            ?: checkNotNull(activeConfig).maxTemperatureC,
        temperatureSupported = activeStatus?.temperatureSupported
            ?: checkNotNull(activeConfig).temperatureSupported,
        readingValid = activeTemperature?.readingValid == true,
        temperatureC = activeTemperature?.temperatureC,
        sampledAtMs = activeTemperature?.sampledAtMs ?: 0L,
        fanCount = activeStatus?.fanOutputCount ?: checkNotNull(activeConfig).fanOutputCount,
        fanDisplayNamesEditable = activeStatus?.runtime?.supportsFanDisplayName
            ?: activeFans.any { fan -> fan.editable.displayName },
        fans = activeFans.map { fan ->
            DeviceCoolingFanSnapshot(
                key = fan.key,
                displayName = fan.displayName,
                percentNow = fan.percentNow,
                displayNameEditable = fan.editable.displayName
            )
        }
    )
}

private fun com.aqua.aqualight.data.devices.runtime.modules.cooling.DeviceCoolingConfigSnapshot?.orEmptyFans() =
    this?.fans?.sortedBy { snapshot -> snapshot.listIndex }?.map { snapshot -> snapshot.fan }.orEmpty()

private fun DeviceCoolingMode.toApplicationMode(): DeviceCoolingModeOption = when (this) {
    DeviceCoolingMode.AUTO -> DeviceCoolingModeOption.AUTO
    DeviceCoolingMode.ON -> DeviceCoolingModeOption.ON
    DeviceCoolingMode.OFF -> DeviceCoolingModeOption.OFF
}

private fun DeviceCoolingModeOption.toRuntimeMode(): DeviceCoolingMode = when (this) {
    DeviceCoolingModeOption.AUTO -> DeviceCoolingMode.AUTO
    DeviceCoolingModeOption.ON -> DeviceCoolingMode.ON
    DeviceCoolingModeOption.OFF -> DeviceCoolingMode.OFF
}

private fun DeviceRuntimeCommandOutcome<*>.toApplicationResult(): DeviceCoolingOperationResult =
    when (this) {
        is DeviceRuntimeCommandOutcome.Success -> DeviceCoolingOperationResult.Success
        is DeviceRuntimeCommandOutcome.UnsupportedByDevice -> DeviceCoolingOperationResult.Unsupported
        is DeviceRuntimeCommandOutcome.NotConnected,
        is DeviceRuntimeCommandOutcome.NotAuthenticated -> DeviceCoolingOperationResult.NotConnected
        is DeviceRuntimeCommandOutcome.FirmwareError ->
            DeviceCoolingOperationResult.Failed("$code: $message")
        is DeviceRuntimeCommandOutcome.ProtocolError ->
            DeviceCoolingOperationResult.Failed(reason)
        is DeviceRuntimeCommandOutcome.SendFailed ->
            DeviceCoolingOperationResult.Failed("WebSocket send failed.")
        is DeviceRuntimeCommandOutcome.Timeout ->
            DeviceCoolingOperationResult.Failed("Cooling command timed out.")
        is DeviceRuntimeCommandOutcome.Cancelled ->
            DeviceCoolingOperationResult.Failed(reason)
    }
