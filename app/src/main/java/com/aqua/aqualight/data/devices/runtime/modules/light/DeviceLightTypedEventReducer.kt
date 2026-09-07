package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeEventPayload
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeTypedEvent

/** Applies validated `light.status.changed` payloads to the single Light state owner. */
internal class DeviceLightTypedEventReducer(
    private val stateStore: DeviceLightRuntimeStateStore
) {
    fun apply(event: DeviceRuntimeTypedEvent): DeviceLightEventApplyResult {
        if (event.type != DeviceRuntimeTypedEvent.Type.LIGHT_STATUS_CHANGED) {
            return DeviceLightEventApplyResult.Ignored
        }
        return runCatching { applyPayload(event) }.fold(
            onSuccess = { applied ->
                if (applied) DeviceLightEventApplyResult.Applied
                else DeviceLightEventApplyResult.Ignored
            },
            onFailure = { error ->
                DeviceLightEventApplyResult.Malformed(error.message.orEmpty())
            }
        )
    }

    private fun applyPayload(event: DeviceRuntimeTypedEvent): Boolean = when (val payload = event.payload) {
        is DeviceRuntimeEventPayload.Snapshot -> stateStore.recordStatus(
            event.deviceUid,
            event.generation,
            DeviceLightStatusParser.parse(payload.data)
        )
        is DeviceRuntimeEventPayload.CommandResult -> applyCommandResult(event, payload)
    }

    private fun applyCommandResult(
        event: DeviceRuntimeTypedEvent,
        payload: DeviceRuntimeEventPayload.CommandResult
    ): Boolean {
        require(payload.commandModule == DeviceLightRuntimeContract.MODULE) {
            "Light event command module differs from the event module."
        }
        return when (payload.commandAction) {
            DeviceLightRuntimeContract.Action.MANUAL_SET -> stateStore.recordManual(
                event.deviceUid,
                event.generation,
                DeviceLightMutationParser.parseManual(payload.result)
            )
            DeviceLightRuntimeContract.Action.CHANNEL_REGIME_SET ->
                stateStore.recordChannelRegime(
                    event.deviceUid,
                    event.generation,
                    DeviceLightMutationParser.parseChannelRegime(payload.result)
                )
            DeviceLightRuntimeContract.Action.PROGRAM_APPLY -> stateStore.recordProgramApply(
                event.deviceUid,
                event.generation,
                DeviceLightMutationParser.parseProgramApply(payload.result)
            )
            DeviceLightRuntimeContract.Action.PROGRAM_DELETE -> stateStore.recordProgramDelete(
                event.deviceUid,
                event.generation,
                DeviceLightMutationParser.parseProgramDelete(payload.result)
            )
            DeviceLightRuntimeContract.Action.TEMPERATURE_PROTECTION_SET -> {
                val parsed = DeviceLightTemperatureProtectionParser
                    .parseSetResult(payload.result)
                    .getOrThrow()
                stateStore.recordTemperatureProtection(
                    event.deviceUid,
                    event.generation,
                    parsed.status
                )
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
