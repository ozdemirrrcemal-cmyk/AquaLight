package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelRejection
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes all per-channel operations that may reconcile authoritative Dosing state. */
internal class DeviceDosingV1ChannelOperationGate {
    private val locks = ConcurrentHashMap<DeviceDosingV1Address, Mutex>()

    suspend fun <T> withChannel(
        address: DeviceDosingV1Address,
        block: suspend () -> T
    ): T = lock(address).withLock { block() }

    private fun lock(address: DeviceDosingV1Address): Mutex =
        locks.computeIfAbsent(address) { Mutex() }
}

/** A replay policy and firmware assignment handled as one serialized persisted mutation. */
internal data class DeviceDosingV1PersistedMutation<T>(
    val assignmentSatisfied: ((DeviceDosingChannelSnapshot) -> Boolean)? = null,
    val execute: suspend (
        DeviceUid,
        DeviceDosingV1ChannelKey,
        Long,
        DeviceDosingChannelSnapshot
    ) -> DeviceRuntimeCommandOutcome<T>,
    val channel: (T) -> DeviceDosingV1ChannelDetail,
    val onAccepted: () -> Unit = {}
)

/** Serializes mutations per channel and reconciles their authoritative refresh. */
internal class DeviceDosingV1MutationCoordinator(
    private val stateOwner: DeviceDosingV1StateOwner,
    private val stateAccess: DeviceDosingV1StateAccess,
    private val refreshCoordinator: DeviceDosingV1RefreshCoordinator,
    private val operationGate: DeviceDosingV1ChannelOperationGate = DeviceDosingV1ChannelOperationGate(),
    private val scheduleBackgroundReconciliation: ((DeviceDosingV1Address, Long) -> Unit)? = null,
    private val cancelBackgroundReconciliation: ((DeviceDosingV1Address) -> Unit)? = null
) {
    private val conflictCoordinator = DeviceDosingV1ConflictCoordinator(stateOwner, refreshCoordinator)

    suspend fun <T> mutatePersisted(
        deviceUid: String,
        slotId: String,
        mutation: DeviceDosingV1PersistedMutation<T>
    ): DeviceDosingV1MutationResult<T> = mutateSerialized(
        address = dosingV1Address(deviceUid, slotId),
        mutation = DosingMutationDefinition(
            persistedMutation = true,
            assignmentSatisfied = mutation.assignmentSatisfied,
            execute = mutation.execute,
            channel = mutation.channel,
            onAccepted = mutation.onAccepted
        )
    )

    suspend fun <T> mutateRuntime(
        deviceUid: String,
        slotId: String,
        execute: suspend (
            DeviceUid,
            DeviceDosingV1ChannelKey,
            Long,
            DeviceDosingChannelSnapshot
        ) -> DeviceRuntimeCommandOutcome<T>,
        channel: (T) -> DeviceDosingV1ChannelDetail
    ): DeviceDosingV1MutationResult<T> = mutateSerialized(
        address = dosingV1Address(deviceUid, slotId),
        mutation = DosingMutationDefinition(
            persistedMutation = false,
            execute = execute,
            channel = channel
        )
    )

    private suspend fun <T> mutateSerialized(
        address: DeviceDosingV1Address,
        mutation: DosingMutationDefinition<T>
    ): DeviceDosingV1MutationResult<T> {
        // A user mutation always outranks optional post-ACK consistency work. Cancellation happens
        // before waiting for the channel gate so a slow background readback cannot add UX latency.
        cancelBackgroundReconciliation?.invoke(address)
        return operationGate.withChannel(address) {
            var baseline = mutationBaseline(address, mutation)
                ?: return@withChannel DeviceDosingV1MutationResult.Malformed
            if (mutation.assignmentSatisfied?.invoke(baseline.state.channel) == true) {
                return@withChannel acceptSatisfiedAssignment(address, baseline, mutation)
            }
            var attempt = 0
            while (attempt < MAX_REPLAY_SAFE_ASSIGNMENT_ATTEMPTS) {
                val result = mutateAgainstBaseline(
                    address = address,
                    baseline = baseline,
                    mutation = mutation
                )
                val replayableFailure = mutation.assignmentSatisfied != null &&
                    result.isReplayableAssignmentFailure()
                if (!replayableFailure) return@withChannel result

                // Failure reconciliation already refreshed inside this same channel gate. Replay
                // only when it produced a coherent authoritative baseline.
                val reconciled = stateAccess.currentState(address) ?: return@withChannel result
                if (mutation.assignmentSatisfied?.invoke(reconciled.channel) == true) {
                    mutation.onAccepted()
                    val acceptedState = stateAccess.currentState(address) ?: reconciled
                    return@withChannel DeviceDosingV1MutationResult.Reconciled(acceptedState)
                }
                if (attempt >= MAX_REPLAY_SAFE_ASSIGNMENT_ATTEMPTS - 1) {
                    return@withChannel result
                }
                baseline = DosingMutationBaseline(
                    state = reconciled,
                    source = DosingMutationBaselineSource.AUTHORITATIVE
                )
                attempt += 1
            }
            error("Replay-safe assignment loop must return within its bounded attempt budget.")
        }
    }

    private fun <T> acceptSatisfiedAssignment(
        address: DeviceDosingV1Address,
        baseline: DosingMutationBaseline,
        mutation: DosingMutationDefinition<T>
    ): DeviceDosingV1MutationResult<T> {
        mutation.onAccepted()
        return if (baseline.source == DosingMutationBaselineSource.COMMITTED_MUTATION) {
            scheduleBackgroundReconciliation?.invoke(address, baseline.state.channel.revision)
            DeviceDosingV1MutationResult.Committed(baseline.state.channel.revision)
        } else {
            val acceptedState = stateAccess.currentState(address) ?: baseline.state
            DeviceDosingV1MutationResult.Reconciled(acceptedState)
        }
    }

    private suspend fun <T> mutateAgainstBaseline(
        address: DeviceDosingV1Address,
        baseline: DosingMutationBaseline,
        mutation: DosingMutationDefinition<T>
    ): DeviceDosingV1MutationResult<T> {
        val revision = baseline.state.channel.revision
        val token = stateOwner.beginRequest(address.deviceUid, address.channelKey)
        return when (
            val execution = executeDosingMutation(
                address,
                revision,
                baseline.state.channel,
                mutation.execute
            )
        ) {
            is DosingExecutionOutcome.Rejected ->
                DeviceDosingV1MutationResult.LocallyRejected(execution.reason)
            is DosingExecutionOutcome.Completed -> when (val outcome = execution.outcome) {
                is DeviceRuntimeCommandOutcome.Success -> commitMutation(
                    AcceptedDosingMutation(
                        address = address,
                        token = token,
                        outcome = outcome,
                        channel = mutation.channel,
                        persistedMutation = mutation.persistedMutation,
                        onAccepted = mutation.onAccepted
                    )
                )
                else -> conflictCoordinator.reconcile(address, outcome)
            }
        }
    }

    private suspend fun <T> commitMutation(
        accepted: AcceptedDosingMutation<T>
    ): DeviceDosingV1MutationResult<T> = runCatching {
        accepted.channel(accepted.outcome.value)
    }.fold(
        onSuccess = { detail -> recordMutation(accepted, detail) },
        onFailure = { DeviceDosingV1MutationResult.Malformed }
    )

    private suspend fun <T> recordMutation(
        accepted: AcceptedDosingMutation<T>,
        detail: DeviceDosingV1ChannelDetail
    ): DeviceDosingV1MutationResult<T> = runCatching {
        stateOwner.recordMutation(accepted.token, accepted.outcome.generation, detail)
    }.fold(
        onSuccess = { disposition ->
            if (disposition == DeviceDosingV1CommitDisposition.MALFORMED) {
                DeviceDosingV1MutationResult.Malformed
            } else {
                accepted.onAccepted()
                finishAcceptedMutation(
                    address = accepted.address,
                    value = accepted.outcome.value,
                    disposition = disposition,
                    persistedMutation = accepted.persistedMutation,
                    committedRevision = detail.revision
                ).also { result ->
                    if (result is DeviceDosingV1MutationResult.Committed) {
                        scheduleBackgroundReconciliation?.invoke(
                            accepted.address,
                            detail.revision
                        )
                    }
                }
            }
        },
        onFailure = { DeviceDosingV1MutationResult.Malformed }
    )

    private suspend fun <T> finishAcceptedMutation(
        address: DeviceDosingV1Address,
        value: T,
        disposition: DeviceDosingV1CommitDisposition,
        persistedMutation: Boolean,
        committedRevision: Long
    ): DeviceDosingV1MutationResult<T> = when {
        disposition == DeviceDosingV1CommitDisposition.MALFORMED ->
            DeviceDosingV1MutationResult.Malformed
        persistedMutation && scheduleBackgroundReconciliation != null ->
            DeviceDosingV1MutationResult.Committed(committedRevision)
        else -> resolveAcceptedReadback(
            address = address,
            value = value,
            persistedMutation = persistedMutation,
            committedRevision = committedRevision,
            refreshed = refreshCoordinator.refreshWithinGate(address)
        )
    }

    /**
     * A successful firmware response is the commit boundary for persisted writes. Production
     * publishes the ACK channel projection immediately and performs coherent readback owner-scoped;
     * runtime mutations retain their stricter synchronous readback contract.
     */
    private fun <T> resolveAcceptedReadback(
        address: DeviceDosingV1Address,
        value: T,
        persistedMutation: Boolean,
        committedRevision: Long,
        refreshed: DeviceDosingV1RefreshResult
    ): DeviceDosingV1MutationResult<T> {
        val refreshedState = (refreshed as? DeviceDosingV1RefreshResult.Success)?.state
        val currentState = if (refreshedState == null) stateAccess.currentState(address) else null

        return when {
            refreshedState != null -> acceptedReadbackResult(
                value = value,
                committedRevision = committedRevision,
                state = refreshedState,
                persistedMutation = persistedMutation
            )
            currentState != null -> acceptedReadbackResult(
                value = value,
                committedRevision = committedRevision,
                state = currentState,
                persistedMutation = persistedMutation
            )
            persistedMutation -> DeviceDosingV1MutationResult.Committed(committedRevision)
            refreshed is DeviceDosingV1RefreshResult.Malformed -> DeviceDosingV1MutationResult.Malformed
            refreshed is DeviceDosingV1RefreshResult.Failed ||
                refreshed == DeviceDosingV1RefreshResult.RejectedStale ->
                DeviceDosingV1MutationResult.RejectedStale
            else -> error("Successful refresh was handled above")
        }
    }

    private suspend fun <T> mutationBaseline(
        address: DeviceDosingV1Address,
        mutation: DosingMutationDefinition<T>
    ): DosingMutationBaseline? =
        stateAccess.currentState(address)?.let { current ->
            DosingMutationBaseline(
                state = current,
                source = DosingMutationBaselineSource.AUTHORITATIVE
            )
        } ?: if (mutation.persistedMutation && mutation.assignmentSatisfied != null) {
            stateAccess.committedMutationContinuation(address)?.let { committed ->
                DosingMutationBaseline(
                    state = committed,
                    source = DosingMutationBaselineSource.COMMITTED_MUTATION
                )
            }
        } else {
            null
        } ?: when (val refreshed = refreshCoordinator.refreshWithinGate(address)) {
            is DeviceDosingV1RefreshResult.Success -> DosingMutationBaseline(
                state = refreshed.state,
                source = DosingMutationBaselineSource.AUTHORITATIVE
            )
            else -> null
        }
}

private suspend fun <T> executeDosingMutation(
    address: DeviceDosingV1Address,
    revision: Long,
    baseline: DeviceDosingChannelSnapshot,
    execute: suspend (
        DeviceUid,
        DeviceDosingV1ChannelKey,
        Long,
        DeviceDosingChannelSnapshot
    ) -> DeviceRuntimeCommandOutcome<T>
): DosingExecutionOutcome<T> = try {
    DosingExecutionOutcome.Completed(
        execute(address.deviceUid, address.channelKey, revision, baseline)
    )
} catch (rejection: LocalDosingMutationRejection) {
    DosingExecutionOutcome.Rejected(rejection.reason)
} catch (_: IllegalArgumentException) {
    DosingExecutionOutcome.Rejected(DeviceDosingChannelRejection.INVALID_DRAFT)
}

private fun <T> acceptedReadbackResult(
    value: T,
    committedRevision: Long,
    state: DeviceDosingV1AuthoritativeState,
    persistedMutation: Boolean
): DeviceDosingV1MutationResult<T> = if (state.channel.revision >= committedRevision) {
    DeviceDosingV1MutationResult.Success(value, state)
} else if (persistedMutation) {
    DeviceDosingV1MutationResult.Committed(committedRevision)
} else {
    DeviceDosingV1MutationResult.Malformed
}

private data class AcceptedDosingMutation<T>(
    val address: DeviceDosingV1Address,
    val token: DeviceDosingV1RequestToken,
    val outcome: DeviceRuntimeCommandOutcome.Success<T>,
    val channel: (T) -> DeviceDosingV1ChannelDetail,
    val persistedMutation: Boolean,
    val onAccepted: () -> Unit
)

private data class DosingMutationDefinition<T>(
    val persistedMutation: Boolean,
    val assignmentSatisfied: ((DeviceDosingChannelSnapshot) -> Boolean)? = null,
    val execute: suspend (
        DeviceUid,
        DeviceDosingV1ChannelKey,
        Long,
        DeviceDosingChannelSnapshot
    ) -> DeviceRuntimeCommandOutcome<T>,
    val channel: (T) -> DeviceDosingV1ChannelDetail,
    val onAccepted: () -> Unit = {}
)

private data class DosingMutationBaseline(
    val state: DeviceDosingV1AuthoritativeState,
    val source: DosingMutationBaselineSource
)

private enum class DosingMutationBaselineSource {
    AUTHORITATIVE,
    COMMITTED_MUTATION
}

private sealed interface DosingExecutionOutcome<out T> {
    data class Completed<T>(val outcome: DeviceRuntimeCommandOutcome<T>) : DosingExecutionOutcome<T>
    data class Rejected(val reason: DeviceDosingChannelRejection) : DosingExecutionOutcome<Nothing>
}

private fun DeviceDosingV1MutationResult<*>.isReplayableAssignmentFailure(): Boolean = when (this) {
    DeviceDosingV1MutationResult.Conflict -> true
    is DeviceDosingV1MutationResult.Failed -> when (outcome) {
        is DeviceRuntimeCommandOutcome.SendFailed,
        is DeviceRuntimeCommandOutcome.Timeout,
        is DeviceRuntimeCommandOutcome.Cancelled,
        is DeviceRuntimeCommandOutcome.ProtocolError -> true
        else -> false
    }
    else -> false
}

private const val MAX_REPLAY_SAFE_ASSIGNMENT_ATTEMPTS = 3
