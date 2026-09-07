package com.aqua.aqualight.data.devices.runtime.modules.timer

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeEventPayload
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeTypedEvent

/** Applies the firmware's direct `timer.status.changed` runtime delta contract. */
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
            event.payload is DeviceRuntimeEventPayload.CommandResult ->
                DeviceTimerEventApplyResult.Ignored
            else -> applySnapshot(event)
        }
    }

    private fun applySnapshot(event: DeviceRuntimeTypedEvent): DeviceTimerEventApplyResult =
        runCatching {
            val payload = event.payload as DeviceRuntimeEventPayload.Snapshot
            val change = DeviceTimerStatusChangedEventParser.parse(payload.data)
            when (
                val result = stateStore.recordRuntimeEvent(
                    event.deviceUid,
                    event.generation,
                    change
                )
            ) {
                DeviceTimerStateEventResult.Applied -> DeviceTimerEventApplyResult.Applied
                DeviceTimerStateEventResult.Ignored -> DeviceTimerEventApplyResult.Ignored
                is DeviceTimerStateEventResult.RefreshRequired ->
                    DeviceTimerEventApplyResult.RefreshRequired(result.channelKey)
            }
        }.getOrElse { error ->
            DeviceTimerEventApplyResult.Malformed(error.message.orEmpty())
        }
}

internal sealed interface DeviceTimerEventApplyResult {
    data object Applied : DeviceTimerEventApplyResult
    data object Ignored : DeviceTimerEventApplyResult
    data class RefreshRequired(val channelKey: String) : DeviceTimerEventApplyResult
    data class Malformed(val reason: String) : DeviceTimerEventApplyResult
}
