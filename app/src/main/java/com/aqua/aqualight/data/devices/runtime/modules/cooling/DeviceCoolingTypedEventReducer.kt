package com.aqua.aqualight.data.devices.runtime.modules.cooling

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeEventPayload
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeTypedEvent

/** Applies legacy production Cooling events until Contract V1 is connected to UI state. */
internal class DeviceCoolingTypedEventReducer(
    private val stateStore: DeviceCoolingRuntimeStateStore
) {
    fun apply(event: DeviceRuntimeTypedEvent): DeviceCoolingEventApplyResult = when (event.type) {
        DeviceRuntimeTypedEvent.Type.COOLING_STATUS_CHANGED ->
            guarded { applyCooling(event.deviceUid, event.payload) }
        DeviceRuntimeTypedEvent.Type.COOLING_TELEMETRY_CHANGED ->
            DeviceCoolingEventApplyResult.Ignored
        else -> DeviceCoolingEventApplyResult.Ignored
    }

    private fun applyCooling(
        deviceUid: DeviceUid,
        payload: DeviceRuntimeEventPayload
    ): Boolean = when (payload) {
        is DeviceRuntimeEventPayload.Snapshot -> {
            stateStore.recordStatus(deviceUid, DeviceCoolingStatusParser.parse(payload.data))
            true
        }
        is DeviceRuntimeEventPayload.CommandResult -> {
            require(payload.commandModule == DeviceCoolingRuntimeContract.MODULE)
            require(payload.commandAction == DeviceCoolingRuntimeContract.Action.CONFIG_APPLY)
            stateStore.recordConfig(
                deviceUid,
                DeviceCoolingMutationParser.parseConfigApply(payload.result)
            )
            true
        }
    }

    private fun guarded(block: () -> Boolean): DeviceCoolingEventApplyResult =
        runCatching(block).fold(
            onSuccess = { applied ->
                if (applied) DeviceCoolingEventApplyResult.Applied
                else DeviceCoolingEventApplyResult.Ignored
            },
            onFailure = { error ->
                DeviceCoolingEventApplyResult.Malformed(error.message.orEmpty())
            }
        )
}

internal sealed interface DeviceCoolingEventApplyResult {
    data object Applied : DeviceCoolingEventApplyResult
    data object Ignored : DeviceCoolingEventApplyResult
    data class Malformed(val reason: String) : DeviceCoolingEventApplyResult
}
