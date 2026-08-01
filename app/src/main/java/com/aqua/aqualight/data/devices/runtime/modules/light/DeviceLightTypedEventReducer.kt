package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeEventPayload
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeTypedEvent

/** Applies validated `light.status.changed` payloads to the shared current-state store. */
internal class DeviceLightTypedEventReducer(
    private val stateStore: DeviceLightRuntimeStateStore
) {
    fun apply(event: DeviceRuntimeTypedEvent): DeviceLightEventApplyResult {
        if (event.type != DeviceRuntimeTypedEvent.Type.LIGHT_STATUS_CHANGED) {
            return DeviceLightEventApplyResult.Ignored
        }
        return runCatching { applyPayload(event.deviceUid, event.payload) }.fold(
            onSuccess = { applied ->
                if (applied) DeviceLightEventApplyResult.Applied
                else DeviceLightEventApplyResult.Ignored
            },
            onFailure = { error ->
                DeviceLightEventApplyResult.Malformed(error.message.orEmpty())
            }
        )
    }

    private fun applyPayload(
        deviceUid: DeviceUid,
        payload: DeviceRuntimeEventPayload
    ): Boolean = when (payload) {
        is DeviceRuntimeEventPayload.Snapshot -> {
            stateStore.recordStatus(deviceUid, DeviceLightStatusParser.parse(payload.data))
            true
        }
        is DeviceRuntimeEventPayload.CommandResult -> applyCommandResult(deviceUid, payload)
    }

    private fun applyCommandResult(
        deviceUid: DeviceUid,
        payload: DeviceRuntimeEventPayload.CommandResult
    ): Boolean {
        require(payload.commandModule == DeviceLightRuntimeContract.MODULE) {
            "Light event command module differs from the event module."
        }
        return when (payload.commandAction) {
            DeviceLightRuntimeContract.Action.MANUAL_SET -> stateStore.recordManual(
                deviceUid,
                DeviceLightMutationParser.parseManual(payload.result)
            )
            DeviceLightRuntimeContract.Action.CHANNEL_REGIME_SET ->
                stateStore.recordChannelRegime(
                    deviceUid,
                    DeviceLightMutationParser.parseChannelRegime(payload.result)
                )
            DeviceLightRuntimeContract.Action.PROGRAM_APPLY -> stateStore.recordProgramApply(
                deviceUid,
                DeviceLightMutationParser.parseProgramApply(payload.result)
            )
            DeviceLightRuntimeContract.Action.PROGRAM_DELETE -> stateStore.recordProgramDelete(
                deviceUid,
                DeviceLightMutationParser.parseProgramDelete(payload.result)
            )
            DeviceLightRuntimeContract.Action.TEMPERATURE_PROTECTION_SET -> {
                val parsed = DeviceLightTemperatureProtectionParser
                    .parseSetResult(payload.result)
                    .getOrThrow()
                stateStore.recordTemperatureProtection(deviceUid, parsed.status)
                true
            }
            else -> false
        }
    }
}

internal sealed interface DeviceLightEventApplyResult {
    data object Applied : DeviceLightEventApplyResult
    data object Ignored : DeviceLightEventApplyResult
    data class Malformed(val reason: String) : DeviceLightEventApplyResult
}
