package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.modules.common.DeviceRuntimeJsonCommand
import kotlinx.coroutines.flow.StateFlow

class DeviceLightTemperatureProtectionRuntimeRepository internal constructor(
    private val gateway: DeviceRuntimeCommandGateway,
    private val stateStore: DeviceLightRuntimeStateStore
) {
    constructor(gateway: DeviceRuntimeCommandGateway) : this(
        gateway = gateway,
        stateStore = DeviceLightRuntimeStateStore()
    )

    val states: StateFlow<Map<DeviceUid, DeviceLightTemperatureProtectionStatus>> =
        stateStore.temperatureProtection

    fun currentStatus(deviceUid: DeviceUid): DeviceLightTemperatureProtectionStatus? =
        states.value[deviceUid]

    suspend fun requestStatus(
        deviceUid: DeviceUid
    ): DeviceRuntimeCommandOutcome<DeviceLightTemperatureProtectionStatus> = gateway.execute(
        deviceUid,
        DeviceRuntimeJsonCommand(
            module = DeviceLightRuntimeContract.MODULE,
            action = DeviceLightRuntimeContract.Action.TEMPERATURE_PROTECTION_STATUS_GET,
            successParser = { data ->
                DeviceLightTemperatureProtectionParser.parseStatus(data).getOrThrow()
            }
        )
    ).recordTemperatureSuccess { status ->
        stateStore.recordTemperatureProtection(deviceUid, status)
    }

    suspend fun setThreshold(
        deviceUid: DeviceUid,
        payload: DeviceLightTemperatureProtectionSetPayload
    ): DeviceRuntimeCommandOutcome<DeviceLightTemperatureProtectionSetResult> = gateway.execute(
        deviceUid,
        DeviceRuntimeJsonCommand(
            module = DeviceLightRuntimeContract.MODULE,
            action = DeviceLightRuntimeContract.Action.TEMPERATURE_PROTECTION_SET,
            dataFactory = payload::toJson,
            successParser = { data ->
                DeviceLightTemperatureProtectionParser.parseSetResult(data).getOrThrow()
            }
        )
    ).recordTemperatureSuccess { result ->
        stateStore.recordTemperatureProtection(deviceUid, result.status)
    }
}

private fun <T> DeviceRuntimeCommandOutcome<T>.recordTemperatureSuccess(
    recorder: (T) -> Unit
): DeviceRuntimeCommandOutcome<T> = also { outcome ->
    if (outcome is DeviceRuntimeCommandOutcome.Success) recorder(outcome.value)
}
