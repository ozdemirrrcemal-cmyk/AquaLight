package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome

/** Coordinates refreshes without owning any authoritative state. */
internal class DeviceDosingV1RefreshCoordinator(
    private val repository: DeviceDosingV1Repository,
    private val stateOwner: DeviceDosingV1StateOwner,
    private val stateAccess: DeviceDosingV1StateAccess,
    private val operationGate: DeviceDosingV1ChannelOperationGate =
        DeviceDosingV1ChannelOperationGate()
) {
    suspend fun refresh(deviceUid: String, slotId: String): DeviceDosingV1RefreshResult =
        refresh(stateAccess.address(deviceUid, slotId))

    suspend fun refreshAll(deviceUid: String): Boolean {
        val uid = DeviceUid(deviceUid.trim())
        val discovery = repository.requestGlobalStatus(uid)
        if (discovery !is DeviceRuntimeCommandOutcome.Success) return false

        var allAuthoritative = true
        discovery.value.channels.forEach { channel ->
            val address = DeviceDosingV1Address(uid, channel.channelKey)
            val result = operationGate.withChannel(address) {
                // Each channel receives a fresh coherent global/channel/progress triplet. The
                // discovery document is used only to enumerate firmware-owned channel keys.
                refreshWithinGate(address)
            }
            if (!result.isAuthoritative()) allAuthoritative = false
        }
        return allAuthoritative
    }

    suspend fun refresh(address: DeviceDosingV1Address): DeviceDosingV1RefreshResult =
        operationGate.withChannel(address) {
            refreshWithinGate(address)
        }

    /** Checks and reconciles a durable ACK inside the shared per-channel serialization gate. */
    internal suspend fun reconcileCommitted(
        address: DeviceDosingV1Address,
        minimumRevision: Long
    ): DeviceDosingV1RefreshResult = operationGate.withChannel(address) {
        val current = stateAccess.currentState(address)
        if (current != null && current.channel.revision >= minimumRevision) {
            DeviceDosingV1RefreshResult.Success(current)
        } else {
            refreshWithinGate(address)
        }
    }

    /**
     * Authoritative refresh for callers that already hold [DeviceDosingV1ChannelOperationGate].
     * Keeping this separate prevents nested locking while ensuring every externally initiated
     * reconciliation shares the same per-channel serialization boundary as mutations and events.
     */
    internal suspend fun refreshWithinGate(
        address: DeviceDosingV1Address
    ): DeviceDosingV1RefreshResult {
        val token = stateOwner.beginRequest(address.deviceUid, address.channelKey)
        return refresh(address, token, repository.requestGlobalStatus(address.deviceUid))
    }

    private suspend fun refresh(
        address: DeviceDosingV1Address,
        token: DeviceDosingV1RequestToken,
        globalOutcome: DeviceRuntimeCommandOutcome<DeviceDosingV1GlobalStatus>
    ): DeviceDosingV1RefreshResult = when (globalOutcome) {
        is DeviceRuntimeCommandOutcome.Success -> refreshChannel(address, token, globalOutcome)
        else -> DeviceDosingV1RefreshResult.Failed(globalOutcome)
    }

    private suspend fun refreshChannel(
        address: DeviceDosingV1Address,
        token: DeviceDosingV1RequestToken,
        global: DeviceRuntimeCommandOutcome.Success<DeviceDosingV1GlobalStatus>
    ): DeviceDosingV1RefreshResult = when (
        val channel = repository.requestChannelStatus(address.deviceUid, address.channelKey)
    ) {
        is DeviceRuntimeCommandOutcome.Success -> refreshProgress(address, token, global, channel)
        else -> DeviceDosingV1RefreshResult.Failed(channel)
    }

    private suspend fun refreshProgress(
        address: DeviceDosingV1Address,
        token: DeviceDosingV1RequestToken,
        global: DeviceRuntimeCommandOutcome.Success<DeviceDosingV1GlobalStatus>,
        channel: DeviceRuntimeCommandOutcome.Success<DeviceDosingV1ChannelStatus>
    ): DeviceDosingV1RefreshResult = when (
        val progress = repository.requestProgress(address.deviceUid, address.channelKey)
    ) {
        is DeviceRuntimeCommandOutcome.Success -> commit(address, token, global, channel, progress)
        else -> DeviceDosingV1RefreshResult.Failed(progress)
    }

    private fun commit(
        address: DeviceDosingV1Address,
        token: DeviceDosingV1RequestToken,
        global: DeviceRuntimeCommandOutcome.Success<DeviceDosingV1GlobalStatus>,
        channel: DeviceRuntimeCommandOutcome.Success<DeviceDosingV1ChannelStatus>,
        progress: DeviceRuntimeCommandOutcome.Success<DeviceDosingV1ProgressStatus>
    ): DeviceDosingV1RefreshResult = if (sameConnectionGeneration(global, channel, progress)) {
        stateOwner.commitRefresh(
            token = token,
            connectionGeneration = global.generation,
            global = global.value,
            channelStatus = channel.value,
            progressStatus = progress.value
        ).toRefreshResult(address, stateAccess)
    } else {
        DeviceDosingV1RefreshResult.RejectedStale
    }
}

private fun DeviceDosingV1CommitDisposition.toRefreshResult(
    address: DeviceDosingV1Address,
    stateAccess: DeviceDosingV1StateAccess
): DeviceDosingV1RefreshResult = when (this) {
    DeviceDosingV1CommitDisposition.APPLIED -> stateAccess.currentState(address)?.let {
        DeviceDosingV1RefreshResult.Success(it)
    } ?: DeviceDosingV1RefreshResult.Malformed
    DeviceDosingV1CommitDisposition.MALFORMED -> DeviceDosingV1RefreshResult.Malformed
    DeviceDosingV1CommitDisposition.STALE_CONNECTION,
    DeviceDosingV1CommitDisposition.STALE_REVISION -> DeviceDosingV1RefreshResult.RejectedStale
    DeviceDosingV1CommitDisposition.STALE_REQUEST -> stateAccess.currentState(address)?.let {
        DeviceDosingV1RefreshResult.Success(it)
    } ?: DeviceDosingV1RefreshResult.RejectedStale
}

private fun DeviceDosingV1RefreshResult.isAuthoritative(): Boolean =
    this is DeviceDosingV1RefreshResult.Success

private fun sameConnectionGeneration(
    first: DeviceRuntimeCommandOutcome.Success<*>,
    second: DeviceRuntimeCommandOutcome.Success<*>,
    third: DeviceRuntimeCommandOutcome.Success<*>
): Boolean = first.generation == second.generation && second.generation == third.generation
