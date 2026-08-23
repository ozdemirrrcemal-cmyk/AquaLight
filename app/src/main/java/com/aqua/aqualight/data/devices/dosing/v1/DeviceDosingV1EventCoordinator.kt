package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.base.diagnostics.AppDiagnosticTrace
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeTypedEvent

/** Converts runtime events into invalidations and delegates authoritative refreshes. */
internal class DeviceDosingV1EventCoordinator(
    private val stateOwner: DeviceDosingV1StateOwner,
    private val refreshCoordinator: DeviceDosingV1RefreshCoordinator
) {
    suspend fun consume(event: DeviceRuntimeTypedEvent): DeviceDosingV1EventResult {
        event.trace("arrived")
        return when (event.type) {
            DeviceRuntimeTypedEvent.Type.DOSING_STATUS_CHANGED -> consumeStatusChanged(event)
            DeviceRuntimeTypedEvent.Type.TIME_STATUS_CHANGED -> consumeTimeStatusChanged(event)
            else -> DeviceDosingV1EventResult.Ignored
        }.also { result -> event.trace("completed", "result" to result.traceName()) }
    }

    private suspend fun consumeStatusChanged(event: DeviceRuntimeTypedEvent): DeviceDosingV1EventResult {
        val invalidation = runCatching {
            DeviceDosingV1EventParser.parseInvalidation(event.payload)
        }.getOrElse {
            event.trace("parse_failed")
            return DeviceDosingV1EventResult.Malformed
        }
        event.trace(
            "parsed",
            "slot" to invalidation.channelKey.value,
            "revision" to invalidation.revisionHint,
            "runtimeSequence" to invalidation.runtimeEventSequenceHint
        )
        val address = DeviceDosingV1Address(event.deviceUid, invalidation.channelKey)
        return refreshInvalidated(address, event, invalidation)
    }

    /**
     * Scheduler readiness is device-wide. Phone/NTP/manual time synchronization can therefore
     * change every enabled channel from INVALID_TIME to runnable without changing channel revision.
     * Re-read all channels through the central refresh coordinator instead of mutating UI state or
     * projecting the time event itself into Dosing state.
     */
    private suspend fun consumeTimeStatusChanged(
        event: DeviceRuntimeTypedEvent
    ): DeviceDosingV1EventResult {
        val refreshed = refreshCoordinator.refreshAll(event.deviceUid.value)
        event.trace("time_readback_completed", "authoritative" to refreshed)
        return if (refreshed) {
            DeviceDosingV1EventResult.RefreshedAll
        } else {
            DeviceDosingV1EventResult.RefreshFailed
        }
    }

    private suspend fun refreshInvalidated(
        address: DeviceDosingV1Address,
        event: DeviceRuntimeTypedEvent,
        invalidation: DeviceDosingV1Invalidation
    ): DeviceDosingV1EventResult {
        val disposition = stateOwner.invalidate(
            deviceUid = event.deviceUid,
            channelKey = invalidation.channelKey,
            connectionGeneration = event.generation,
            revisionHint = invalidation.revisionHint,
            runtimeEventSequenceHint = invalidation.runtimeEventSequenceHint
        )
        event.trace(
            "invalidation_completed",
            "slot" to invalidation.channelKey.value,
            "revision" to invalidation.revisionHint,
            "runtimeSequence" to invalidation.runtimeEventSequenceHint,
            "disposition" to disposition.name
        )
        return when (disposition) {
            DeviceDosingV1InvalidationDisposition.STALE_CONNECTION,
            DeviceDosingV1InvalidationDisposition.STALE_REVISION,
            DeviceDosingV1InvalidationDisposition.DUPLICATE_EVENT ->
                DeviceDosingV1EventResult.Ignored
            DeviceDosingV1InvalidationDisposition.APPLIED -> refreshCoordinator.refresh(address)
                .also { readback ->
                    event.trace(
                        "readback_completed",
                        "slot" to invalidation.channelKey.value,
                        "result" to readback.traceName()
                    )
                }
                .toEventResult()
        }
    }
}

private fun DeviceDosingV1RefreshResult.toEventResult(): DeviceDosingV1EventResult = when (this) {
    is DeviceDosingV1RefreshResult.Success -> DeviceDosingV1EventResult.Refreshed(state)
    DeviceDosingV1RefreshResult.Malformed -> DeviceDosingV1EventResult.Malformed
    is DeviceDosingV1RefreshResult.Failed,
    DeviceDosingV1RefreshResult.RejectedStale -> DeviceDosingV1EventResult.RefreshFailed
}

private fun DeviceRuntimeTypedEvent.trace(name: String, vararg fields: Pair<String, Any?>) {
    AppDiagnosticTrace.event(
        DOSING_EVENT_CATEGORY,
        name,
        "device" to AppDiagnosticTrace.deviceRef(deviceUid.value),
        "eventId" to messageId,
        "generation" to generation.value,
        "eventType" to type.name,
        *fields
    )
}

private fun DeviceDosingV1EventResult.traceName(): String = javaClass.simpleName

private fun DeviceDosingV1RefreshResult.traceName(): String = javaClass.simpleName

private const val DOSING_EVENT_CATEGORY = "dosing_event"
