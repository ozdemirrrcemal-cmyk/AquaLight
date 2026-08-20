package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeTypedEvent

/** Converts runtime events into invalidations and delegates authoritative refreshes. */
internal class DeviceDosingV1EventCoordinator(
    private val stateOwner: DeviceDosingV1StateOwner,
    private val refreshCoordinator: DeviceDosingV1RefreshCoordinator,
    private val operationGate: DeviceDosingV1ChannelOperationGate = DeviceDosingV1ChannelOperationGate()
) {
    suspend fun consume(event: DeviceRuntimeTypedEvent): DeviceDosingV1EventResult = when (event.type) {
        DeviceRuntimeTypedEvent.Type.DOSING_STATUS_CHANGED -> consumeStatusChanged(event)
        DeviceRuntimeTypedEvent.Type.TIME_STATUS_CHANGED -> consumeTimeStatusChanged(event)
        else -> DeviceDosingV1EventResult.Ignored
    }

    private suspend fun consumeStatusChanged(event: DeviceRuntimeTypedEvent): DeviceDosingV1EventResult {
        val invalidation = runCatching {
            DeviceDosingV1EventParser.parseInvalidation(event.payload)
        }.getOrElse { return DeviceDosingV1EventResult.Malformed }
        val address = DeviceDosingV1Address(event.deviceUid, invalidation.channelKey)
        return operationGate.withChannel(address) { refreshInvalidated(event, invalidation) }
    }

    /**
     * Scheduler readiness is device-wide. Phone/NTP/manual time synchronization can therefore
     * change every enabled channel from INVALID_TIME to runnable without changing channel revision.
     * Re-read all channels through the central refresh coordinator instead of mutating UI state or
     * projecting the time event itself into Dosing state.
     */
    private suspend fun consumeTimeStatusChanged(
        event: DeviceRuntimeTypedEvent
    ): DeviceDosingV1EventResult = if (refreshCoordinator.refreshAll(event.deviceUid.value)) {
        DeviceDosingV1EventResult.RefreshedAll
    } else {
        DeviceDosingV1EventResult.RefreshFailed
    }

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
        DeviceDosingV1InvalidationDisposition.APPLIED -> refreshCoordinator.refreshWithinGate(
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
