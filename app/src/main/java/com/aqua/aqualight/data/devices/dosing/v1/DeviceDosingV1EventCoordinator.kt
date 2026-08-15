package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeTypedEvent

/** Converts runtime events into invalidations and delegates authoritative refreshes. */
internal class DeviceDosingV1EventCoordinator(
    private val stateOwner: DeviceDosingV1StateOwner,
    private val refreshCoordinator: DeviceDosingV1RefreshCoordinator
) {
    suspend fun consume(event: DeviceRuntimeTypedEvent): DeviceDosingV1EventResult =
        when (event.type) {
            DeviceRuntimeTypedEvent.Type.DOSING_STATUS_CHANGED -> consumeStatusChanged(event)
            else -> DeviceDosingV1EventResult.Ignored
        }

    private suspend fun consumeStatusChanged(
        event: DeviceRuntimeTypedEvent
    ): DeviceDosingV1EventResult = runCatching {
        DeviceDosingV1EventParser.parseInvalidation(event.payload)
    }.fold(
        onSuccess = { invalidation -> refreshInvalidated(event, invalidation) },
        onFailure = { DeviceDosingV1EventResult.Malformed }
    )

    private suspend fun refreshInvalidated(
        event: DeviceRuntimeTypedEvent,
        invalidation: DeviceDosingV1Invalidation
    ): DeviceDosingV1EventResult = when (
        stateOwner.invalidate(
            deviceUid = event.deviceUid,
            channelKey = invalidation.channelKey,
            connectionGeneration = event.generation,
            revisionHint = invalidation.revisionHint
        )
    ) {
        DeviceDosingV1InvalidationDisposition.STALE_CONNECTION,
        DeviceDosingV1InvalidationDisposition.STALE_REVISION -> DeviceDosingV1EventResult.Ignored
        DeviceDosingV1InvalidationDisposition.APPLIED -> refreshCoordinator.refresh(
            DeviceDosingV1Address(event.deviceUid, invalidation.channelKey)
        ).toEventResult()
    }
}

private fun DeviceDosingV1RefreshResult.toEventResult(): DeviceDosingV1EventResult = when (this) {
    is DeviceDosingV1RefreshResult.Success -> DeviceDosingV1EventResult.Refreshed(state)
    DeviceDosingV1RefreshResult.Malformed -> DeviceDosingV1EventResult.Malformed
    is DeviceDosingV1RefreshResult.Failed,
    DeviceDosingV1RefreshResult.RejectedStale -> DeviceDosingV1EventResult.RefreshFailed
}
