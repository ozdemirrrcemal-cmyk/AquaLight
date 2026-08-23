package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Coordinates refreshes without owning any authoritative state. */
internal class DeviceDosingV1RefreshCoordinator(
    private val repository: DeviceDosingV1Repository,
    private val stateOwner: DeviceDosingV1StateOwner,
    private val stateAccess: DeviceDosingV1StateAccess,
    private val producerScope: CoroutineScope? = null,
    private val operationGate: DeviceDosingV1ChannelOperationGate =
        DeviceDosingV1ChannelOperationGate()
) {
    private val refreshFlights = DeviceDosingV1RefreshFlights(producerScope)
    private val refreshProducer = DeviceDosingV1RefreshProducer(
        repository = repository,
        stateOwner = stateOwner,
        stateAccess = stateAccess
    )

    suspend fun refresh(deviceUid: String, slotId: String): DeviceDosingV1RefreshResult =
        refresh(dosingV1Address(deviceUid, slotId))

    suspend fun refreshAll(deviceUid: String): Boolean = DeviceUid(deviceUid.trim()).let { uid ->
        refreshFlights.device(uid) { refreshAllProducer(uid) }
    }

    private suspend fun refreshAllProducer(uid: DeviceUid): Boolean {
        val discovery = repository.requestGlobalStatus(uid)
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

    suspend fun refresh(address: DeviceDosingV1Address): DeviceDosingV1RefreshResult =
        refreshFlights.channel(address) {
            operationGate.withRefresh(
                address = address,
                preempted = DeviceDosingV1RefreshResult.RejectedStale
            ) { refreshProducer.refreshWithinGate(address) }
        }

    /** A user write outranks any in-flight readback but never another firmware mutation. */
    internal suspend fun preemptForMutation(address: DeviceDosingV1Address) {
        refreshFlights.cancelChannel(address)
    }

    internal suspend fun refreshInvalidated(
        address: DeviceDosingV1Address,
        connectionGeneration: DeviceRuntimeConnectionGeneration,
        revisionHint: Long?,
        runtimeEventSequenceHint: Long?
    ): DeviceDosingV1RefreshResult {
        var attempt = 0
        var resolved: DeviceDosingV1RefreshResult = DeviceDosingV1RefreshResult.RejectedStale
        var needsAnotherAttempt = true
        while (attempt < MAX_INVALIDATED_REFRESH_ATTEMPTS && needsAnotherAttempt) {
            val candidate = refreshFlights.channel(address) {
                operationGate.withRefresh(
                    address = address,
                    preempted = DeviceDosingV1RefreshResult.RejectedStale
                ) {
                    authoritativeAfterInvalidation(
                        address = address,
                        connectionGeneration = connectionGeneration,
                        revisionHint = revisionHint,
                        runtimeEventSequenceHint = runtimeEventSequenceHint
                    ) ?: refreshProducer.refreshWithinGate(address)
                }
            }
            val authoritative = authoritativeAfterInvalidation(
                address = address,
                connectionGeneration = connectionGeneration,
                revisionHint = revisionHint,
                runtimeEventSequenceHint = runtimeEventSequenceHint
            )
            if (authoritative != null) {
                resolved = authoritative
                needsAnotherAttempt = false
            } else {
                resolved = candidate
                needsAnotherAttempt = candidate is DeviceDosingV1RefreshResult.Success
            }
            attempt += 1
        }
        return if (resolved is DeviceDosingV1RefreshResult.Success && needsAnotherAttempt) {
            DeviceDosingV1RefreshResult.RejectedStale
        } else {
            resolved
        }
    }

    private fun authoritativeAfterInvalidation(
        address: DeviceDosingV1Address,
        connectionGeneration: DeviceRuntimeConnectionGeneration,
        revisionHint: Long?,
        runtimeEventSequenceHint: Long?
    ): DeviceDosingV1RefreshResult.Success? = stateOwner.reads
        .currentAuthoritativeStateAtLeast(
            deviceUid = address.deviceUid,
            channelKey = address.channelKey,
            connectionGeneration = connectionGeneration,
            revisionHint = revisionHint,
            runtimeEventSequenceHint = runtimeEventSequenceHint
        )
        ?.let(DeviceDosingV1RefreshResult::Success)

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
    ): DeviceDosingV1RefreshResult = refreshProducer.refreshWithinGate(address)
}

/** Produces and atomically commits one coherent global/channel/progress readback. */
private class DeviceDosingV1RefreshProducer(
    private val repository: DeviceDosingV1Repository,
    private val stateOwner: DeviceDosingV1StateOwner,
    private val stateAccess: DeviceDosingV1StateAccess
) {
    suspend fun refreshWithinGate(
        address: DeviceDosingV1Address
    ): DeviceDosingV1RefreshResult {
        repeat(MAX_REFRESH_STABILITY_ATTEMPTS) { attempt ->
            val token = stateOwner.beginRequest(address.deviceUid, address.channelKey)
            val result = refresh(
                address,
                token,
                repository.requestGlobalStatus(address.deviceUid)
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

/** Owner-scoped single-flight registry; waiter cancellation never cancels the shared producer. */
private class DeviceDosingV1RefreshFlights(
    private val producerScope: CoroutineScope?
) {
    private val channels = ConcurrentHashMap<
        DeviceDosingV1Address,
        Deferred<DeviceDosingV1RefreshResult>
    >()
    private val devices = ConcurrentHashMap<DeviceUid, Deferred<Boolean>>()

    suspend fun channel(
        address: DeviceDosingV1Address,
        producer: suspend () -> DeviceDosingV1RefreshResult
    ): DeviceDosingV1RefreshResult = flight(
        key = address,
        flights = channels,
        fallback = DeviceDosingV1RefreshResult.Malformed,
        cancellationFallback = DeviceDosingV1RefreshResult.RejectedStale,
        producer = producer
    )

    suspend fun device(uid: DeviceUid, producer: suspend () -> Boolean): Boolean = flight(
        key = uid,
        flights = devices,
        fallback = false,
        cancellationFallback = false,
        producer = producer
    )

    suspend fun cancelChannel(address: DeviceDosingV1Address) {
        if (producerScope == null) return
        val active = channels[address] ?: return
        active.cancelAndJoin()
        channels.remove(address, active)
    }

    private suspend fun <K : Any, V> flight(
        key: K,
        flights: ConcurrentHashMap<K, Deferred<V>>,
        fallback: V,
        cancellationFallback: V,
        producer: suspend () -> V
    ): V = producerScope?.let { scope ->
        ownerScoped(
            scope = scope,
            key = key,
            flights = flights,
            fallback = fallback,
            cancellationFallback = cancellationFallback,
            producer = producer
        )
    } ?: callerScoped(key, flights, fallback, cancellationFallback, producer)

    private suspend fun <K : Any, V> ownerScoped(
        scope: CoroutineScope,
        key: K,
        flights: ConcurrentHashMap<K, Deferred<V>>,
        fallback: V,
        cancellationFallback: V,
        producer: suspend () -> V
    ): V {
        val candidate = scope.async(start = CoroutineStart.LAZY) {
            try {
                producer()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                fallback
            }
        }
        val existing = flights.putIfAbsent(key, candidate)
        val selected = if (existing == null) {
            candidate.invokeOnCompletion { flights.remove(key, candidate) }
            candidate.start()
            candidate
        } else {
            candidate.cancel()
            existing
        }
        return try {
            selected.await()
        } catch (cancellation: CancellationException) {
            currentCoroutineContext().ensureActive()
            cancellationFallback
        }
    }

    /** Test-only fallback for adapters that do not own a production reconciliation scope. */
    private suspend fun <K : Any, V> callerScoped(
        key: K,
        flights: ConcurrentHashMap<K, Deferred<V>>,
        fallback: V,
        cancellationFallback: V,
        producer: suspend () -> V
    ): V {
        val pending = CompletableDeferred<V>()
        val existing = flights.putIfAbsent(key, pending)
        return if (existing != null) {
            existing.await()
        } else {
            try {
                producer().also(pending::complete)
            } catch (cancellation: CancellationException) {
                pending.complete(cancellationFallback)
                throw cancellation
            } finally {
                pending.complete(fallback)
                flights.remove(key, pending)
            }
        }
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

private const val MAX_REFRESH_STABILITY_ATTEMPTS = 2
private const val MAX_INVALIDATED_REFRESH_ATTEMPTS = 2
