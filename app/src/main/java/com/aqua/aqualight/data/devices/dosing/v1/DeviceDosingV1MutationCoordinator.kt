package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelRejection
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome

/** A replay policy and firmware assignment handled as one serialized persisted mutation. */
internal data class DeviceDosingV1PersistedMutation<T>(
    val assignmentSatisfied: ((DeviceDosingChannelSnapshot) -> Boolean)? = null,
    val origin: DeviceDosingV1PersistedMutationOrigin? = null,
    val acknowledgementVisibility: DeviceDosingV1MutationVisibility =
        DeviceDosingV1MutationVisibility.PERSISTED_ACK,
    val execute: suspend (
        DeviceUid,
        DeviceDosingV1ChannelKey,
        Long,
        DeviceDosingChannelSnapshot
    ) -> DeviceRuntimeCommandOutcome<T>,
    val channel: (T) -> DeviceDosingV1ChannelDetail,
    val onAccepted: () -> Unit = {}
)

/** Application-domain CAS origin for a long-lived editor mutation. */
internal data class DeviceDosingV1PersistedMutationOrigin(
    val revision: Long,
    val domainStillPresent: (DeviceDosingChannelSnapshot) -> Boolean
) {
    init {
        require(revision >= 0L)
    }
}

/**
 * Central mutation transaction coordinator.
 *
 * Exactly one owner-scoped mutation processor owns firmware writes for a channel. Background reads
 * never hold its write lane; mutation-critical readback is performed inside this lane and therefore
 * cannot join an older background flight. Complete persisted ACKs are the durable continuation for
 * assignment mutations and deliberately do not trigger an immediate post-ACK wire read. State
 * acceptance remains exclusively in StateOwner.
 *
 * Transport/timeout ambiguity is reconciled but never replayed inside this transaction. The central
 * latest-intent owner restarts from the newest desired assignment after the recovery checkpoint, so
 * an obsolete in-flight target can never be blindly re-issued after a socket loss.
 */
@Suppress("TooManyFunctions") // One central transaction coordinator owns the complete write flow.
internal class DeviceDosingV1MutationCoordinator(
    private val stateOwner: DeviceDosingV1StateOwner,
    private val stateAccess: DeviceDosingV1StateAccess,
    private val refreshCoordinator: DeviceDosingV1RefreshCoordinator,
    private val mutationProcessor: DeviceDosingV1ChannelMutationProcessor =
        DeviceDosingV1ChannelMutationProcessor(null),
    private val operationAdmission: DeviceDosingV1ChannelOperationAdmission =
        refreshCoordinator.operationAdmission,
    private val recoveryGate: DeviceDosingV1AssignmentRecoveryGate =
        DeviceDosingV1AssignmentRecoveryGate(),
    private val scheduleBackgroundReconciliation: ((DeviceDosingV1Address, Long) -> Unit)? = null
) {
    private val conflictCoordinator = DeviceDosingV1ConflictCoordinator(
        stateOwner = stateOwner,
        refreshCoordinator = refreshCoordinator,
        recoveryGate = recoveryGate
    )

    suspend fun <T> mutatePersisted(
        deviceUid: String,
        slotId: String,
        mutation: DeviceDosingV1PersistedMutation<T>
    ): DeviceDosingV1MutationResult<T> = mutateSerialized(
        address = dosingV1Address(deviceUid, slotId),
        mutation = DosingMutationDefinition(
            persistedMutation = true,
            acknowledgementVisibility = mutation.acknowledgementVisibility,
            assignmentSatisfied = mutation.assignmentSatisfied,
            origin = mutation.origin,
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
            acknowledgementVisibility = DeviceDosingV1MutationVisibility.RUNTIME_ACK,
            execute = execute,
            channel = channel
        )
    )

    private suspend fun <T> mutateSerialized(
        address: DeviceDosingV1Address,
        mutation: DosingMutationDefinition<T>
    ): DeviceDosingV1MutationResult<T> = mutationProcessor.submit(
        address = address,
        mutation = {
            refreshCoordinator.preemptBackgroundForMutation(address)
            operationAdmission.beginMutation(address)
            try {
                mutateSingleWriter(address, mutation)
            } finally {
                operationAdmission.endMutation(address)
            }
        },
        afterResultPublished = { result ->
            if (
                result is DeviceDosingV1MutationResult.Committed &&
                mutation.requiresBackgroundReconciliation()
            ) {
                scheduleBackgroundReconciliation?.invoke(address, result.revision)
            }
        }
    )

    @Suppress("ReturnCount") // Fail-fast transaction exits keep rejected/stale writes unambiguous.
    private suspend fun <T> mutateSingleWriter(
        address: DeviceDosingV1Address,
        mutation: DosingMutationDefinition<T>
    ): DeviceDosingV1MutationResult<T> {
        var baseline = mutationBaseline(address, mutation)
            ?: return DeviceDosingV1MutationResult.Malformed
        if (mutation.assignmentSatisfied?.invoke(baseline.state.channel) == true) {
            return acceptSatisfiedAssignment(address, baseline, mutation)
        }
        if (!mutation.origin.acceptsBaseline(baseline)) {
            return DeviceDosingV1MutationResult.Conflict
        }
        var attempt = 0
        while (attempt < MAX_REPLAY_SAFE_ASSIGNMENT_ATTEMPTS) {
            val result = mutateAgainstBaseline(address, baseline, mutation)
            val replayableFailure = mutation.assignmentSatisfied != null &&
                result.isReplayableAssignmentFailure()
            if (!replayableFailure) return result

            val reconciled = stateAccess.currentState(address) ?: return result
            if (mutation.assignmentSatisfied?.invoke(reconciled.channel) == true) {
                mutation.onAccepted()
                val acceptedState = stateAccess.currentState(address) ?: reconciled
                return DeviceDosingV1MutationResult.Reconciled(acceptedState)
            }
            if (!mutation.origin.accepts(reconciled.channel)) {
                return DeviceDosingV1MutationResult.Conflict
            }
            if (attempt >= MAX_REPLAY_SAFE_ASSIGNMENT_ATTEMPTS - 1) return result
            baseline = DosingMutationBaseline(
                state = reconciled,
                source = DosingMutationBaselineSource.AUTHORITATIVE
            )
            attempt += 1
        }
        error("Replay-safe assignment loop must return within its bounded attempt budget.")
    }

    private fun <T> acceptSatisfiedAssignment(
        address: DeviceDosingV1Address,
        baseline: DosingMutationBaseline,
        mutation: DosingMutationDefinition<T>
    ): DeviceDosingV1MutationResult<T> {
        mutation.onAccepted()
        return if (baseline.source == DosingMutationBaselineSource.COMMITTED_MUTATION) {
            DeviceDosingV1MutationResult.Committed(baseline.state.channel.revision)
        } else {
            DeviceDosingV1MutationResult.Reconciled(
                stateAccess.currentState(address) ?: baseline.state
            )
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
                        acknowledgementVisibility = mutation.acknowledgementVisibility,
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
        stateOwner.recordMutation(
            token = accepted.token,
            connectionGeneration = accepted.outcome.generation,
            channel = detail,
            visibility = accepted.acknowledgementVisibility
        )
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
                )
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
            refreshed = refreshCoordinator.refreshForMutation(address)
        )
    }

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
                value, committedRevision, refreshedState, persistedMutation
            )
            currentState != null -> acceptedReadbackResult(
                value, committedRevision, currentState, persistedMutation
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
    ): DosingMutationBaseline? {
        val cached = stateAccess.currentState(address)?.let { current ->
            DosingMutationBaseline(current, DosingMutationBaselineSource.AUTHORITATIVE)
        } ?: if (mutation.persistedMutation && mutation.assignmentSatisfied != null) {
            stateAccess.committedMutationContinuation(address)?.let { committed ->
                DosingMutationBaseline(committed, DosingMutationBaselineSource.COMMITTED_MUTATION)
            }
        } else null
        if (cached != null && cached.state.channel.revision >= (mutation.origin?.revision ?: 0L)) {
            return cached
        }
        return when (val refreshed = refreshCoordinator.refreshForMutation(address)) {
            is DeviceDosingV1RefreshResult.Success ->
                DosingMutationBaseline(refreshed.state, DosingMutationBaselineSource.AUTHORITATIVE)
            is DeviceDosingV1RefreshResult.Failed -> {
                conflictCoordinator.recordBaselineRecoveryIfRequired(address, refreshed.outcome)
                null
            }
            DeviceDosingV1RefreshResult.Malformed,
            DeviceDosingV1RefreshResult.RejectedStale -> null
        }
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
    val acknowledgementVisibility: DeviceDosingV1MutationVisibility,
    val onAccepted: () -> Unit
)

private data class DosingMutationDefinition<T>(
    val persistedMutation: Boolean,
    val acknowledgementVisibility: DeviceDosingV1MutationVisibility,
    val assignmentSatisfied: ((DeviceDosingChannelSnapshot) -> Boolean)? = null,
    val origin: DeviceDosingV1PersistedMutationOrigin? = null,
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

private enum class DosingMutationBaselineSource { AUTHORITATIVE, COMMITTED_MUTATION }

private sealed interface DosingExecutionOutcome<out T> {
    data class Completed<T>(val outcome: DeviceRuntimeCommandOutcome<T>) : DosingExecutionOutcome<T>
    data class Rejected(val reason: DeviceDosingChannelRejection) : DosingExecutionOutcome<Nothing>
}

/** Only an explicit CAS conflict may replay inside the transaction; transport ambiguity may not. */
private fun DeviceDosingV1MutationResult<*>.isReplayableAssignmentFailure(): Boolean =
    this == DeviceDosingV1MutationResult.Conflict

private fun DosingMutationDefinition<*>.requiresBackgroundReconciliation(): Boolean =
    persistedMutation && acknowledgementVisibility == DeviceDosingV1MutationVisibility.RUNTIME_ACK

private fun DeviceDosingV1PersistedMutationOrigin?.acceptsBaseline(
    baseline: DosingMutationBaseline
): Boolean = when {
    this == null -> true
    baseline.source == DosingMutationBaselineSource.COMMITTED_MUTATION &&
        baseline.state.channel.revision >= revision -> true
    else -> accepts(baseline.state.channel)
}

private fun DeviceDosingV1PersistedMutationOrigin?.accepts(
    snapshot: DeviceDosingChannelSnapshot
): Boolean = this == null || (
    snapshot.revision >= revision && domainStillPresent(snapshot)
)

private const val MAX_REPLAY_SAFE_ASSIGNMENT_ATTEMPTS = 3
