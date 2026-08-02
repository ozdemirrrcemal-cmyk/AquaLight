package com.aqua.aqualight.data.devices.runtime.modules.timer

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeEventPayload
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeTypedEvent

/** Applies validated `timer.status.changed` payloads to the shared Timer state. */
internal class DeviceTimerTypedEventReducer(
    private val stateStore: DeviceTimerRuntimeStateStore,
    private val accessProvider: (DeviceUid) -> DeviceTimerRuntimeAccess
) {
    fun apply(event: DeviceRuntimeTypedEvent): DeviceTimerEventApplyResult {
        val access = accessProvider(event.deviceUid)
        return when {
            event.type != DeviceRuntimeTypedEvent.Type.TIMER_STATUS_CHANGED ->
                DeviceTimerEventApplyResult.Ignored
            !access.supportsApi -> DeviceTimerEventApplyResult.Ignored
            else -> applyValidatedEvent(event, access)
        }
    }

    private fun applyValidatedEvent(
        event: DeviceRuntimeTypedEvent,
        access: DeviceTimerRuntimeAccess
    ): DeviceTimerEventApplyResult {
        return runCatching { applyPayload(event.deviceUid, event.payload, access) }.fold(
            onSuccess = { applied ->
                if (applied) DeviceTimerEventApplyResult.Applied
                else DeviceTimerEventApplyResult.Ignored
            },
            onFailure = { error ->
                DeviceTimerEventApplyResult.Malformed(error.message.orEmpty())
            }
        )
    }

    private fun applyPayload(
        deviceUid: DeviceUid,
        payload: DeviceRuntimeEventPayload,
        access: DeviceTimerRuntimeAccess
    ): Boolean = when (payload) {
        is DeviceRuntimeEventPayload.Snapshot -> {
            val status = DeviceTimerStatusParser.parse(payload.data)
            DeviceTimerCommandValidation.validateStatus(status, access)
            stateStore.recordStatus(deviceUid, status)
        }
        is DeviceRuntimeEventPayload.CommandResult -> applyCommandResult(
            deviceUid,
            payload,
            access
        )
    }

    private fun applyCommandResult(
        deviceUid: DeviceUid,
        payload: DeviceRuntimeEventPayload.CommandResult,
        access: DeviceTimerRuntimeAccess
    ): Boolean {
        require(payload.commandModule == DeviceTimerRuntimeContract.MODULE) {
            "Timer event command module differs from the event module."
        }
        return when (payload.commandAction) {
            DeviceTimerRuntimeContract.Action.CONFIG_APPLY -> {
                val result = DeviceTimerMutationParser.parseConfigApply(payload.result)
                DeviceTimerCommandValidation.validateConfigSnapshot(
                    result.config,
                    stateStore.states.value[deviceUid]?.status,
                    access
                )
                stateStore.recordConfig(
                    deviceUid,
                    result
                )
                true
            }
            DeviceTimerRuntimeContract.Action.CHANNEL_SET -> {
                val result = DeviceTimerMutationParser.parseChannelSet(payload.result)
                DeviceTimerCommandValidation.validateChannelSnapshot(
                    result,
                    stateStore.states.value[deviceUid]?.status,
                    access
                )
                stateStore.recordChannel(deviceUid, result)
            }
            else -> false
        }
    }
}

internal sealed interface DeviceTimerEventApplyResult {
    data object Applied : DeviceTimerEventApplyResult
    data object Ignored : DeviceTimerEventApplyResult
    data class Malformed(val reason: String) : DeviceTimerEventApplyResult
}
