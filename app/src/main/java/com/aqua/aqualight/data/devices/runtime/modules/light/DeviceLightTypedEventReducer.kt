package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeEventPayload
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeTypedEvent

/** Accepts only complete Light V1 snapshots; command-result events trigger a status refresh. */
internal class DeviceLightTypedEventReducer(
    private val stateOwner: DeviceLightRuntimeStateOwner,
    private val accessProvider: (com.aqua.aqualight.data.devices.model.DeviceUid) ->
        DeviceLightRuntimeAccess = { DeviceLightRuntimeAccess.FINAL_V1 }
) {
    fun apply(event: DeviceRuntimeTypedEvent): DeviceLightEventApplyResult {
        val snapshot = event.payload as? DeviceRuntimeEventPayload.Snapshot
        return if (event.type != DeviceRuntimeTypedEvent.Type.LIGHT_STATUS_CHANGED || snapshot == null) {
            DeviceLightEventApplyResult.Ignored
        } else {
            runCatching {
                stateOwner.recordStatus(
                    event.deviceUid,
                    event.generation,
                    DeviceLightStatusParser.parse(
                        data = snapshot.data,
                        managedAutoPlanSupported =
                            accessProvider(event.deviceUid).supportsManagedAutoPlan
                    )
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
