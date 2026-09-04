package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.modules.common.DeviceRuntimeJsonCommand
import kotlinx.coroutines.flow.StateFlow

class DeviceLightThermalRuntimeRepository internal constructor(
    private val gateway: DeviceRuntimeCommandGateway,
    private val stateStore: DeviceLightThermalRuntimeStateStore
) {
    constructor(gateway: DeviceRuntimeCommandGateway) : this(
        gateway = gateway,
        stateStore = DeviceLightThermalRuntimeStateStore()
    )

    val states: StateFlow<Map<DeviceUid, DeviceLightThermalStatus>> = stateStore.statuses

    suspend fun requestStatus(
        deviceUid: DeviceUid
    ): DeviceRuntimeCommandOutcome<DeviceLightThermalStatus> = gateway.execute(
        deviceUid,
        DeviceRuntimeJsonCommand(
            module = AqlWsContract.MODULE_LIGHT,
            action = AqlWsContract.ACTION_LIGHT_THERMAL_STATUS_GET,
            successParser = DeviceLightThermalStatusParser::parse
        )
    ).recordLightThermalSuccess { status ->
        stateStore.recordStatus(deviceUid, status)
    }
}

private inline fun <T> DeviceRuntimeCommandOutcome<T>.recordLightThermalSuccess(
    block: (T) -> Unit
): DeviceRuntimeCommandOutcome<T> = also { outcome ->
    if (outcome is DeviceRuntimeCommandOutcome.Success) block(outcome.value)
}
