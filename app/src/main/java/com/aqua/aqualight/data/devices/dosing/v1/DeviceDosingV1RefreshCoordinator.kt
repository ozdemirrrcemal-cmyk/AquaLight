package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.base.diagnostics.AppDiagnosticTrace
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

/** Coordinates refreshes without owning any authoritative state. */
internal class DeviceDosingV1RefreshCoordinator(
    private val repository: DeviceDosingV1Repository,
    private val stateOwner: DeviceDosingV1StateOwner,
    private val stateAccess: DeviceDosingV1StateAccess,
    private val producerScope: CoroutineScope? = null,
    private val operationGate: DeviceDosingV1ChannelOperationGate =
        DeviceDosingV1ChannelOperationGate()
) {
    private val inFlightRefreshes = ConcurrentHashMap<
        DeviceDosingV1Address,
        Deferred<DeviceDosingV1RefreshResult>
    >()
    private val inFlightRefreshAll = ConcurrentHashMap<DeviceUid, Deferred<Boolean>>()

    suspend fun refresh(deviceUid: String, slotId: String): DeviceDosingV1RefreshResult =
        refresh(dosingV1Address(deviceUid, slotId))

    suspend fun refreshAll(deviceUid: String): Boolean {
        val uid = DeviceUid(deviceUid.trim())
        uid.traceRefreshAll("requested")
        val scope = producerScope ?: return refreshAllCallerScoped(uid)
        val candidate = scope.async(start = CoroutineStart.LAZY) {
            try {
                uid.traceRefreshAll("producer_started")
                refreshAllProducer(uid).also { result ->
                    uid.traceRefreshAll("producer_completed", "authoritative" to result)
                }
            } catch (cancellation: CancellationException) {
                uid.traceRefreshAll("producer_cancelled")
                throw cancellation
            } catch (_: Exception) {
                uid.traceRefreshAll("producer_failed")
                false
            }
        }
        val existing = inFlightRefreshAll.putIfAbsent(uid, candidate)
        if (existing != null) {
            uid.traceRefreshAll("joined_existing")
            candidate.cancel()
            return existing.await()
        }

        candidate.invokeOnCompletion { inFlightRefreshAll.remove(uid, candidate) }
        candidate.start()
        return candidate.await()
    }

    /** Test-only fallback for adapters that do not own a production reconciliation scope. */
    private suspend fun refreshAllCallerScoped(uid: DeviceUid): Boolean {
        val pending = CompletableDeferred<Boolean>()
        val existing = inFlightRefreshAll.putIfAbsent(uid, pending)
        if (existing != null) return existing.await()

        return try {
            val result = refreshAllProducer(uid)
            pending.complete(result)
            result
        } catch (cancellation: CancellationException) {
            pending.complete(false)
            throw cancellation
        } finally {
            pending.complete(false)
            inFlightRefreshAll.remove(uid, pending)
        }
    }

    private suspend fun refreshAllProducer(uid: DeviceUid): Boolean {
        val discovery = repository.requestGlobalStatus(uid)
        uid.traceRefreshAll("discovery_completed", "outcome" to discovery.traceName())
        if (discovery !is DeviceRuntimeCommandOutcome.Success) return false

        var allAuthoritative = true
        discovery.value.channels.forEach { channel ->
            val address = DeviceDosingV1Address(uid, channel.channelKey)
            // Each channel receives a fresh coherent global/channel/progress triplet. Concurrent
            // screen, event and lifecycle callers share the same per-channel flight.
            val result = refresh(address)
            if (!result.isAuthoritative()) allAuthoritative = false
        }
        return allAuthoritative
    }

    suspend fun refresh(address: DeviceDosingV1Address): DeviceDosingV1RefreshResult {
        address.traceRefresh("requested")
        val scope = producerScope ?: return refreshCallerScoped(address)
        val candidate = scope.async(start = CoroutineStart.LAZY) {
            try {
                address.traceRefresh("producer_started")
                operationGate.withChannel(address) { refreshWithinGate(address) }.also { result ->
                    address.traceRefresh("producer_completed", "result" to result.traceName())
                }
            } catch (cancellation: CancellationException) {
                address.traceRefresh("producer_cancelled")
                throw cancellation
            } catch (_: Exception) {
                address.traceRefresh("producer_failed")
                DeviceDosingV1RefreshResult.Malformed
            }
        }
        val existing = inFlightRefreshes.putIfAbsent(address, candidate)
        if (existing != null) {
            address.traceRefresh("joined_existing")
            candidate.cancel()
            return existing.await()
        }

        candidate.invokeOnCompletion { inFlightRefreshes.remove(address, candidate) }
        candidate.start()
        return candidate.await()
    }

    /** Test-only fallback for adapters that do not own a production reconciliation scope. */
    private suspend fun refreshCallerScoped(
        address: DeviceDosingV1Address
    ): DeviceDosingV1RefreshResult {
        val pending = CompletableDeferred<DeviceDosingV1RefreshResult>()
        val existing = inFlightRefreshes.putIfAbsent(address, pending)
        if (existing != null) return existing.await()

        return try {
            val result = operationGate.withChannel(address) { refreshWithinGate(address) }
            pending.complete(result)
            result
        } catch (cancellation: CancellationException) {
            // The producer belongs to its caller, but joined event/lifecycle consumers must not be
            // cancelled with that caller. They receive a normal stale result and may retry later.
            pending.complete(DeviceDosingV1RefreshResult.RejectedStale)
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
    ): DeviceDosingV1RefreshResult {
        address.traceRefresh("reconcile_started", "minimumRevision" to minimumRevision)
        val current = stateAccess.currentState(address)
        val result = current
            ?.takeIf { state -> state.channel.revision >= minimumRevision }
            ?.let(DeviceDosingV1RefreshResult::Success)
            ?: refresh(address)
        address.traceRefresh(
            "reconcile_completed",
            "minimumRevision" to minimumRevision,
            "cachedRevision" to current?.channel?.revision,
            "result" to result.traceName()
        )
        return result
    }

    /**
     * Authoritative refresh for callers that already hold [DeviceDosingV1ChannelOperationGate].
     * Keeping this separate prevents nested locking while ensuring every externally initiated
     * reconciliation shares the same per-channel serialization boundary as mutations and events.
     */
    internal suspend fun refreshWithinGate(
        address: DeviceDosingV1Address
    ): DeviceDosingV1RefreshResult {
        repeat(MAX_REFRESH_STABILITY_ATTEMPTS) { attempt ->
            val token = stateOwner.beginRequest(address.deviceUid, address.channelKey)
            address.traceRefresh(
                "attempt_started",
                "attempt" to attempt,
                "requestGeneration" to token.requestGeneration
            )
            val global = repository.requestGlobalStatus(address.deviceUid)
            address.traceRefresh(
                "global_completed",
                "attempt" to attempt,
                "outcome" to global.traceName(),
                "generation" to global.generationOrNull()
            )
            val result = refresh(
                address,
                token,
                global
            )
            address.traceRefresh(
                "attempt_completed",
                "attempt" to attempt,
                "requestGeneration" to token.requestGeneration,
                "result" to result.traceName()
            )
            if (
                result != DeviceDosingV1RefreshResult.RejectedStale ||
                attempt == MAX_REFRESH_STABILITY_ATTEMPTS - 1
            ) {
                return result
            }
        }
        error("Refresh stability loop exhausted without returning")
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
        val channelRevision = when (channel) {
            is DeviceRuntimeCommandOutcome.Success -> channel.value.channel.revision
            else -> null
        }
        val runtimeSequence = when (channel) {
            is DeviceRuntimeCommandOutcome.Success -> channel.value.channel.lastRuntimeEvent
                .takeIf { event -> event.valid }
                ?.sequence
            else -> null
        }
        address.traceRefresh(
            "channel_completed",
            "requestGeneration" to token.requestGeneration,
            "outcome" to channel.traceName(),
            "generation" to channel.generationOrNull(),
            "revision" to channelRevision,
            "runtimeSequence" to runtimeSequence
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
        val progressRevision = when (progress) {
            is DeviceRuntimeCommandOutcome.Success -> progress.value.revision
            else -> null
        }
        address.traceRefresh(
            "progress_completed",
            "requestGeneration" to token.requestGeneration,
            "outcome" to progress.traceName(),
            "generation" to progress.generationOrNull(),
            "revision" to progressRevision
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
    ): DeviceDosingV1RefreshResult = if (sameConnectionGeneration(global, channel, progress)) {
        val disposition = stateOwner.commitRefresh(
            token = token,
            connectionGeneration = global.generation,
            global = global.value,
            channelStatus = channel.value,
            progressStatus = progress.value
        )
        address.traceRefresh(
            "commit_completed",
            "requestGeneration" to token.requestGeneration,
            "generation" to global.generation.value,
            "revision" to channel.value.channel.revision,
            "disposition" to disposition.name
        )
        disposition.toRefreshResult(address, stateAccess)
    } else {
        address.traceRefresh(
            "commit_generation_mismatch",
            "requestGeneration" to token.requestGeneration,
            "globalGeneration" to global.generation.value,
            "channelGeneration" to channel.generation.value,
            "progressGeneration" to progress.generation.value
        )
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
    DeviceDosingV1CommitDisposition.STALE_REVISION,
    DeviceDosingV1CommitDisposition.STALE_RUNTIME_EVENT ->
        DeviceDosingV1RefreshResult.RejectedStale
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

private fun DeviceDosingV1Address.traceRefresh(
    name: String,
    vararg fields: Pair<String, Any?>
) {
    AppDiagnosticTrace.event(
        DOSING_REFRESH_CATEGORY,
        name,
        "device" to AppDiagnosticTrace.deviceRef(deviceUid.value),
        "slot" to channelKey.value,
        *fields
    )
}

private fun DeviceUid.traceRefreshAll(name: String, vararg fields: Pair<String, Any?>) {
    AppDiagnosticTrace.event(
        DOSING_REFRESH_CATEGORY,
        name,
        "device" to AppDiagnosticTrace.deviceRef(value),
        "scope" to "all_channels",
        *fields
    )
}

private fun DeviceRuntimeCommandOutcome<*>.traceName(): String = javaClass.simpleName

private fun DeviceRuntimeCommandOutcome<*>.generationOrNull(): Long? = when (this) {
    is DeviceRuntimeCommandOutcome.Success<*> -> generation.value
    is DeviceRuntimeCommandOutcome.NotAuthenticated -> generation.value
    is DeviceRuntimeCommandOutcome.SendFailed -> generation.value
    is DeviceRuntimeCommandOutcome.Timeout -> generation.value
    is DeviceRuntimeCommandOutcome.FirmwareError -> generation.value
    is DeviceRuntimeCommandOutcome.ProtocolError -> generation.value
    is DeviceRuntimeCommandOutcome.Cancelled -> generation.value
    is DeviceRuntimeCommandOutcome.NotConnected,
    is DeviceRuntimeCommandOutcome.UnsupportedByDevice -> null
}

private fun DeviceDosingV1RefreshResult.traceName(): String = javaClass.simpleName

private const val MAX_REFRESH_STABILITY_ATTEMPTS = 2
private const val DOSING_REFRESH_CATEGORY = "dosing_refresh"
