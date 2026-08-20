package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.application.devices.dosing.DeviceDosingDiagnosticTrace
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
        val globalOutcome = repository.requestGlobalStatus(uid)
        return when (globalOutcome) {
            is DeviceRuntimeCommandOutcome.Success -> globalOutcome.value.channels.all { channel ->
                val address = DeviceDosingV1Address(uid, channel.channelKey)
                operationGate.withChannel(address) {
                    refreshWithinGate(address, globalOutcome)
                }.isAuthoritative()
            }
            else -> false
        }
    }

    suspend fun refresh(address: DeviceDosingV1Address): DeviceDosingV1RefreshResult =
        operationGate.withChannel(address) {
            refreshWithinGate(address)
        }

    /**
     * Authoritative refresh for callers that already hold [DeviceDosingV1ChannelOperationGate].
     * Keeping this separate prevents nested locking while ensuring every externally initiated
     * reconciliation shares the same per-channel serialization boundary as mutations and events.
     */
    internal suspend fun refreshWithinGate(
        address: DeviceDosingV1Address,
        diagnosticId: Long? = null
    ): DeviceDosingV1RefreshResult {
        val token = stateOwner.beginRequest(address.deviceUid, address.channelKey)
        traceRefresh(
            address,
            diagnosticId,
            "REFRESH",
            "START requestGeneration=${token.requestGeneration}"
        )
        val globalOutcome = repository.requestGlobalStatus(address.deviceUid)
        traceRefresh(
            address,
            diagnosticId,
            "GLOBAL",
            globalOutcome.refreshDiagnosticSummary(address)
        )
        return refresh(address, token, globalOutcome, diagnosticId)
    }

    private suspend fun refreshWithinGate(
        address: DeviceDosingV1Address,
        global: DeviceRuntimeCommandOutcome.Success<DeviceDosingV1GlobalStatus>
    ): DeviceDosingV1RefreshResult = refresh(
        address = address,
        token = stateOwner.beginRequest(address.deviceUid, address.channelKey),
        globalOutcome = global,
        diagnosticId = null
    )

    private suspend fun refresh(
        address: DeviceDosingV1Address,
        token: DeviceDosingV1RequestToken,
        globalOutcome: DeviceRuntimeCommandOutcome<DeviceDosingV1GlobalStatus>,
        diagnosticId: Long?
    ): DeviceDosingV1RefreshResult = when (globalOutcome) {
        is DeviceRuntimeCommandOutcome.Success ->
            refreshChannel(address, token, globalOutcome, diagnosticId)
        else -> DeviceDosingV1RefreshResult.Failed(globalOutcome)
    }

    private suspend fun refreshChannel(
        address: DeviceDosingV1Address,
        token: DeviceDosingV1RequestToken,
        global: DeviceRuntimeCommandOutcome.Success<DeviceDosingV1GlobalStatus>,
        diagnosticId: Long?
    ): DeviceDosingV1RefreshResult = when (
        val channel = repository.requestChannelStatus(address.deviceUid, address.channelKey)
    ) {
        is DeviceRuntimeCommandOutcome.Success -> {
            traceRefresh(
                address,
                diagnosticId,
                "CHANNEL",
                channel.refreshDiagnosticSummary(address)
            )
            refreshProgress(address, token, global, channel, diagnosticId)
        }
        else -> {
            traceRefresh(
                address,
                diagnosticId,
                "CHANNEL",
                channel.refreshDiagnosticSummary(address)
            )
            DeviceDosingV1RefreshResult.Failed(channel)
        }
    }

    private suspend fun refreshProgress(
        address: DeviceDosingV1Address,
        token: DeviceDosingV1RequestToken,
        global: DeviceRuntimeCommandOutcome.Success<DeviceDosingV1GlobalStatus>,
        channel: DeviceRuntimeCommandOutcome.Success<DeviceDosingV1ChannelStatus>,
        diagnosticId: Long?
    ): DeviceDosingV1RefreshResult = when (
        val progress = repository.requestProgress(address.deviceUid, address.channelKey)
    ) {
        is DeviceRuntimeCommandOutcome.Success -> {
            traceRefresh(
                address,
                diagnosticId,
                "PROGRESS",
                progress.refreshDiagnosticSummary(address)
            )
            commit(address, token, global, channel, progress, diagnosticId)
        }
        else -> {
            traceRefresh(
                address,
                diagnosticId,
                "PROGRESS",
                progress.refreshDiagnosticSummary(address)
            )
            DeviceDosingV1RefreshResult.Failed(progress)
        }
    }

    private fun commit(
        address: DeviceDosingV1Address,
        token: DeviceDosingV1RequestToken,
        global: DeviceRuntimeCommandOutcome.Success<DeviceDosingV1GlobalStatus>,
        channel: DeviceRuntimeCommandOutcome.Success<DeviceDosingV1ChannelStatus>,
        progress: DeviceRuntimeCommandOutcome.Success<DeviceDosingV1ProgressStatus>,
        diagnosticId: Long?
    ): DeviceDosingV1RefreshResult {
        if (!sameConnectionGeneration(global, channel, progress)) {
            traceRefresh(
                address,
                diagnosticId,
                "JOIN",
                "STALE generation mismatch global=${global.generation.value} " +
                    "channel=${channel.generation.value} progress=${progress.generation.value}"
            )
            return DeviceDosingV1RefreshResult.RejectedStale
        }

        val disposition = stateOwner.commitRefresh(
            token = token,
            connectionGeneration = global.generation,
            global = global.value,
            channelStatus = channel.value,
            progressStatus = progress.value
        )
        val globalRevision = global.value.channels
            .singleOrNull { candidate -> candidate.channelKey == address.channelKey }
            ?.revision
        traceRefresh(
            address,
            diagnosticId,
            "JOIN",
            "globalRev=$globalRevision channelRev=${channel.value.channel.revision} " +
                "progressRev=${progress.value.revision} commit=$disposition"
        )
        return disposition.toRefreshResult(address, stateAccess)
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

private fun DeviceRuntimeCommandOutcome<*>.refreshDiagnosticSummary(
    address: DeviceDosingV1Address
): String {
    val revision = when (this) {
        is DeviceRuntimeCommandOutcome.Success<*> -> when (val value = value) {
            is DeviceDosingV1GlobalStatus -> value.channels
                .singleOrNull { candidate -> candidate.channelKey == address.channelKey }
                ?.revision
            is DeviceDosingV1ChannelStatus -> value.channel.revision
            is DeviceDosingV1ProgressStatus -> value.revision
            else -> null
        }
        else -> null
    }
    return if (revision == null) {
        dosingDiagnosticSummary()
    } else {
        "${dosingDiagnosticSummary()} rev=$revision"
    }
}

private fun traceRefresh(
    address: DeviceDosingV1Address,
    diagnosticId: Long?,
    stage: String,
    detail: String
) {
    DeviceDosingDiagnosticTrace.record(
        deviceUid = address.deviceUid.value,
        slotId = DeviceDosingV1SlotKeyMapper.slotId(address.channelKey),
        operationId = diagnosticId,
        stage = stage,
        detail = detail
    )
}
