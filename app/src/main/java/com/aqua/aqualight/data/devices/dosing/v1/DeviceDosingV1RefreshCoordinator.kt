package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.debug.dosing.DosingDebugTrace
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred

/** Coordinates refreshes without owning any authoritative state. */
internal class DeviceDosingV1RefreshCoordinator(
    private val repository: DeviceDosingV1Repository,
    private val stateOwner: DeviceDosingV1StateOwner,
    private val stateAccess: DeviceDosingV1StateAccess,
    private val operationGate: DeviceDosingV1ChannelOperationGate =
        DeviceDosingV1ChannelOperationGate()
) {
    private val inFlightRefreshes = ConcurrentHashMap<
        DeviceDosingV1Address,
        CompletableDeferred<DeviceDosingV1RefreshResult>
    >()

    suspend fun refresh(deviceUid: String, slotId: String): DeviceDosingV1RefreshResult =
        refresh(dosingV1Address(deviceUid, slotId))

    suspend fun refreshAll(deviceUid: String): Boolean {
        val uid = DeviceUid(deviceUid.trim())
        DosingDebugTrace.log(
            "REFRESH",
            "ALL start device=${DosingDebugTrace.shortDevice(uid.value)}"
        )
        val discovery = repository.requestGlobalStatus(uid)
        if (discovery !is DeviceRuntimeCommandOutcome.Success) {
            DosingDebugTrace.log(
                "REFRESH",
                "ALL discovery failed device=${DosingDebugTrace.shortDevice(uid.value)} ${discovery.traceSummary()}"
            )
            return false
        }

        var allAuthoritative = true
        discovery.value.channels.forEach { channel ->
            val address = DeviceDosingV1Address(uid, channel.channelKey)
            // Each channel receives a fresh coherent global/channel/progress triplet. Concurrent
            // screen, event and lifecycle callers share the same per-channel flight.
            val result = refresh(address)
            if (!result.isAuthoritative()) allAuthoritative = false
        }
        DosingDebugTrace.log(
            "REFRESH",
            "ALL end device=${DosingDebugTrace.shortDevice(uid.value)} authoritative=$allAuthoritative"
        )
        return allAuthoritative
    }

    suspend fun refresh(address: DeviceDosingV1Address): DeviceDosingV1RefreshResult {
        val pending = CompletableDeferred<DeviceDosingV1RefreshResult>()
        val existing = inFlightRefreshes.putIfAbsent(address, pending)
        if (existing != null) {
            DosingDebugTrace.log("REFRESH", "JOIN ${address.traceAddress()}")
            return existing.await().also { result ->
                DosingDebugTrace.log(
                    "REFRESH",
                    "JOIN done ${address.traceAddress()} ${result.traceSummary()}"
                )
            }
        }

        DosingDebugTrace.log("REFRESH", "START ${address.traceAddress()}")
        return try {
            val result = operationGate.withChannel(address) { refreshWithinGate(address) }
            pending.complete(result)
            DosingDebugTrace.log("REFRESH", "END ${address.traceAddress()} ${result.traceSummary()}")
            result
        } catch (cancellation: CancellationException) {
            // The producer belongs to its caller, but joined event/lifecycle consumers must not be
            // cancelled with that caller. They receive a normal stale result and may retry later.
            pending.complete(DeviceDosingV1RefreshResult.RejectedStale)
            DosingDebugTrace.log("REFRESH", "CANCEL ${address.traceAddress()}")
            throw cancellation
        } finally {
            // Repository commands normally model failures as outcomes. This fail-closed completion
            // protects joined callers if an unexpected producer exception escapes instead.
            pending.complete(DeviceDosingV1RefreshResult.Malformed)
            inFlightRefreshes.remove(address, pending)
        }
    }

    /** Checks and reconciles a durable ACK inside the shared per-channel serialization gate. */
    internal suspend fun reconcileCommitted(
        address: DeviceDosingV1Address,
        minimumRevision: Long
    ): DeviceDosingV1RefreshResult = stateAccess.currentState(address)
        ?.takeIf { current -> current.channel.revision >= minimumRevision }
        ?.let(DeviceDosingV1RefreshResult::Success)
        ?: refresh(address)

    /**
     * Authoritative refresh for callers that already hold [DeviceDosingV1ChannelOperationGate].
     * Keeping this separate prevents nested locking while ensuring every externally initiated
     * reconciliation shares the same per-channel serialization boundary as mutations and events.
     */
    internal suspend fun refreshWithinGate(
        address: DeviceDosingV1Address
    ): DeviceDosingV1RefreshResult {
        val token = stateOwner.beginRequest(address.deviceUid, address.channelKey)
        DosingDebugTrace.log(
            "REFRESH",
            "GATE ${address.traceAddress()} requestGen=${token.requestGeneration}"
        )
        val global = repository.requestGlobalStatus(address.deviceUid)
        DosingDebugTrace.log(
            "REFRESH",
            "GLOBAL ${address.traceAddress()} ${global.traceSummary()}${global.globalRevisionSuffix(address)}"
        )
        return refresh(address, token, global)
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
    ): DeviceDosingV1RefreshResult {
        val channel = repository.requestChannelStatus(address.deviceUid, address.channelKey)
        DosingDebugTrace.log(
            "REFRESH",
            "CHANNEL ${address.traceAddress()} ${channel.traceSummary()}${channel.channelRevisionSuffix()}"
        )
        return when (channel) {
            is DeviceRuntimeCommandOutcome.Success -> refreshProgress(address, token, global, channel)
            else -> DeviceDosingV1RefreshResult.Failed(channel)
        }
    }

    private suspend fun refreshProgress(
        address: DeviceDosingV1Address,
        token: DeviceDosingV1RequestToken,
        global: DeviceRuntimeCommandOutcome.Success<DeviceDosingV1GlobalStatus>,
        channel: DeviceRuntimeCommandOutcome.Success<DeviceDosingV1ChannelStatus>
    ): DeviceDosingV1RefreshResult {
        val progress = repository.requestProgress(address.deviceUid, address.channelKey)
        DosingDebugTrace.log(
            "REFRESH",
            "PROGRESS ${address.traceAddress()} ${progress.traceSummary()}${progress.progressRevisionSuffix()}"
        )
        return when (progress) {
            is DeviceRuntimeCommandOutcome.Success -> commit(address, token, global, channel, progress)
            else -> DeviceDosingV1RefreshResult.Failed(progress)
        }
    }

    private fun commit(
        address: DeviceDosingV1Address,
        token: DeviceDosingV1RequestToken,
        global: DeviceRuntimeCommandOutcome.Success<DeviceDosingV1GlobalStatus>,
        channel: DeviceRuntimeCommandOutcome.Success<DeviceDosingV1ChannelStatus>,
        progress: DeviceRuntimeCommandOutcome.Success<DeviceDosingV1ProgressStatus>
    ): DeviceDosingV1RefreshResult {
        if (!sameConnectionGeneration(global, channel, progress)) {
            DosingDebugTrace.log(
                "STATE",
                "REJECT generation mismatch ${address.traceAddress()} " +
                    "global=${global.generation.value} channel=${channel.generation.value} progress=${progress.generation.value}"
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
        val result = disposition.toRefreshResult(address, stateAccess)
        DosingDebugTrace.log(
            "STATE",
            "commitRefresh ${address.traceAddress()} disposition=$disposition result=${result.traceSummary()}"
        )
        return result
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

private fun DeviceDosingV1Address.traceAddress(): String =
    "device=${DosingDebugTrace.shortDevice(deviceUid.value)} channel=${channelKey.value}"

private fun DeviceDosingV1RefreshResult.traceSummary(): String = when (this) {
    is DeviceDosingV1RefreshResult.Success ->
        "SUCCESS rev=${state.channel.revision} executionCurrent=${state.channel.progress.executionCurrent}"
    is DeviceDosingV1RefreshResult.Failed -> "FAILED ${outcome.traceSummary()}"
    DeviceDosingV1RefreshResult.Malformed -> "MALFORMED"
    DeviceDosingV1RefreshResult.RejectedStale -> "REJECTED_STALE"
}

private fun DeviceRuntimeCommandOutcome<*>.traceSummary(): String = when (this) {
    is DeviceRuntimeCommandOutcome.Success<*> ->
        "SUCCESS id=$messageId gen=${generation.value} status=$statusCode"
    is DeviceRuntimeCommandOutcome.Timeout -> "TIMEOUT id=$messageId ${timeoutMillis}ms"
    is DeviceRuntimeCommandOutcome.ProtocolError ->
        "PROTOCOL_ERROR id=$messageId " +
            "reason=${DosingDebugTrace.compact(reason, TRACE_REASON_CHARS)}"
    is DeviceRuntimeCommandOutcome.FirmwareError ->
        "FW_ERROR id=$messageId status=$statusCode code=$code field=$field"
    is DeviceRuntimeCommandOutcome.SendFailed -> "SEND_FAILED id=$messageId"
    is DeviceRuntimeCommandOutcome.NotConnected -> "NOT_CONNECTED"
    is DeviceRuntimeCommandOutcome.NotAuthenticated -> "NOT_AUTHENTICATED"
    is DeviceRuntimeCommandOutcome.UnsupportedByDevice -> "UNSUPPORTED"
    is DeviceRuntimeCommandOutcome.Cancelled -> "CANCELLED id=$messageId"
}

private fun DeviceRuntimeCommandOutcome<DeviceDosingV1GlobalStatus>.globalRevisionSuffix(
    address: DeviceDosingV1Address
): String = when (this) {
    is DeviceRuntimeCommandOutcome.Success -> value.channels
        .singleOrNull { channel -> channel.channelKey == address.channelKey }
        ?.let { channel -> " targetRev=${channel.revision} mode=${channel.programMode.raw}" }
        .orEmpty()
    else -> ""
}

private fun DeviceRuntimeCommandOutcome<DeviceDosingV1ChannelStatus>.channelRevisionSuffix(): String =
    when (this) {
        is DeviceRuntimeCommandOutcome.Success -> value.channel.let { channel ->
            " rev=${channel.revision} recovery=${channel.program?.missedDoseRecoveryEnabled} " +
                "mode=${channel.program?.mode?.raw}"
        }
        else -> ""
    }

private fun DeviceRuntimeCommandOutcome<DeviceDosingV1ProgressStatus>.progressRevisionSuffix(): String =
    when (this) {
        is DeviceRuntimeCommandOutcome.Success -> value.let { status ->
            " rev=${status.revision} executionCurrent=${status.progress.executionCurrent} " +
                "total=${status.progress.total} completed=${status.progress.completed} " +
                "mode=${status.programMode.raw}"
        }
        else -> ""
    }

private const val TRACE_REASON_CHARS = 220
