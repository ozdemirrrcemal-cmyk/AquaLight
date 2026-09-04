package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
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

    internal fun isAuthoritative(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration
    ): Boolean = stateStore.isTemperatureProtectionAuthoritative(deviceUid, generation)

    suspend fun requestStatus(
        deviceUid: DeviceUid
    ): DeviceRuntimeCommandOutcome<DeviceLightTemperatureProtectionStatus> {
        val outcome = gateway.execute(
            deviceUid,
            DeviceRuntimeJsonCommand(
                module = DeviceLightRuntimeContract.MODULE,
                action = DeviceLightRuntimeContract.Action.TEMPERATURE_PROTECTION_STATUS_GET,
                successParser = { data ->
                    DeviceLightTemperatureProtectionParser.parseStatus(data).getOrThrow()
                }
            )
        )
        if (outcome is DeviceRuntimeCommandOutcome.Success) {
            stateStore.recordTemperatureProtection(deviceUid, outcome.generation, outcome.value)
        }
        return outcome
    }

    suspend fun setThreshold(
        deviceUid: DeviceUid,
        payload: DeviceLightTemperatureProtectionSetPayload
    ): DeviceRuntimeCommandOutcome<DeviceLightTemperatureProtectionSetResult> {
        val status = stateStore.currentAuthoritativeTemperatureProtection(deviceUid)
        if (status != null && (!status.supported || !status.runtime.supportsSet)) {
            return DeviceRuntimeCommandOutcome.UnsupportedByDevice(
                deviceUid = deviceUid,
                module = DeviceLightRuntimeContract.MODULE,
                action = DeviceLightRuntimeContract.Action.TEMPERATURE_PROTECTION_SET
            )
        }
        val outcome = gateway.execute(
            deviceUid,
            DeviceRuntimeJsonCommand(
                module = DeviceLightRuntimeContract.MODULE,
                action = DeviceLightRuntimeContract.Action.TEMPERATURE_PROTECTION_SET,
                dataFactory = payload::toJson,
                successParser = { data ->
                    DeviceLightTemperatureProtectionParser.parseSetResult(data).also { parsed ->
                        parsed.onSuccess { result ->
                            DeviceLightCommandValidation.validateTemperatureProtection(
                                payload,
                                result
                            )
                        }
                    }.getOrThrow()
                }
            )
        )
        if (
            outcome is DeviceRuntimeCommandOutcome.Success &&
            !stateStore.recordTemperatureProtection(
                deviceUid,
                outcome.generation,
                outcome.value.status
            )
        ) {
            requestStatus(deviceUid)
        }
        return outcome
    }
}
