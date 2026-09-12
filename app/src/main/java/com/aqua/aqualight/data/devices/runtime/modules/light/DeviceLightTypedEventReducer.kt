package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeEventPayload
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeTypedEvent

/** Accepts only complete Light V1 snapshots; command-result events trigger a status refresh. */
internal class DeviceLightTypedEventReducer(
    private val stateStore: DeviceLightRuntimeStateStore
) {
    fun apply(event: DeviceRuntimeTypedEvent): DeviceLightEventApplyResult {
        val snapshot = event.payload as? DeviceRuntimeEventPayload.Snapshot
        return if (event.type != DeviceRuntimeTypedEvent.Type.LIGHT_STATUS_CHANGED || snapshot == null) {
            DeviceLightEventApplyResult.Ignored
        } else {
            runCatching {
                stateStore.recordStatus(
                    event.deviceUid,
                    event.generation,
                    DeviceLightStatusParser.parse(snapshot.data)
                )
            }.fold(
                onSuccess = { applied ->
                    if (applied) DeviceLightEventApplyResult.Applied else DeviceLightEventApplyResult.Ignored
                },
                onFailure = { error -> DeviceLightEventApplyResult.Malformed(error.message.orEmpty()) }
            )
        }
    }
}

internal sealed interface DeviceLightEventApplyResult {
    data object Applied : DeviceLightEventApplyResult
    data object Ignored : DeviceLightEventApplyResult
    data class Malformed(val reason: String) : DeviceLightEventApplyResult
}
