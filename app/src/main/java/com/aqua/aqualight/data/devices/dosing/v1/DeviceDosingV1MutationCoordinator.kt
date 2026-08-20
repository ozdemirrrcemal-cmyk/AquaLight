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

/** Serializes mutations per channel and reconciles their authoritative refresh. */
internal class DeviceDosingV1MutationCoordinator(
    private val stateOwner: DeviceDosingV1StateOwner,
    private val stateAccess: DeviceDosingV1StateAccess,
    private val refreshCoordinator: DeviceDosingV1RefreshCoordinator,
    private val operationGate: DeviceDosingV1ChannelOperationGate = DeviceDosingV1ChannelOperationGate()
) {
    private val conflictCoordinator = DeviceDosingV1ConflictCoordinator(stateOwner, refreshCoordinator)

    suspend fun <T> mutatePersisted(
        deviceUid: String,
        slotId: String,
        execute: suspend (
            DeviceUid,
            DeviceDosingV1ChannelKey,
            Long,
            DeviceDosingChannelSnapshot
        ) -> DeviceRuntimeCommandOutcome<T>,
        channel: (T) -> DeviceDosingV1ChannelDetail,
        onAccepted: () -> Unit = {}
    ): DeviceDosingV1MutationResult<T> = mutateSerialized(
        address = stateAccess.address(deviceUid, slotId),
        persistedMutation = true,
        execute = execute,
        channel = channel,
        onAccepted = onAccepted
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
        address = stateAccess.address(deviceUid, slotId),
        persistedMutation = false,
        execute = execute,
        channel = channel
    )

    private suspend fun <T> mutateSerialized(
        address: DeviceDosingV1Address,
        persistedMutation: Boolean,
        execute: suspend (
            DeviceUid,
            DeviceDosingV1ChannelKey,
            Long,
            DeviceDosingChannelSnapshot
        ) -> DeviceRuntimeCommandOutcome<T>,
        channel: (T) -> DeviceDosingV1ChannelDetail,
        onAccepted: () -> Unit = {}
    ): DeviceDosingV1MutationResult<T> = operationGate.withChannel(address) {
        val baseline = authoritativeBaseline(address)
            ?: return@withChannel DeviceDosingV1MutationResult.Malformed
        val revision = if (persistedMutation) {
            stateAccess.authoritativeRevision(address)
                ?: return@withChannel DeviceDosingV1MutationResult.Malformed
        } else {
            baseline.channel.revision
        }
        val token = stateOwner.beginRequest(address.deviceUid, address.channelKey)
        when (val execution = executeMutation(address, revision, baseline.channel, execute)) {
            is DosingExecutionOutcome.Rejected ->
                DeviceDosingV1MutationResult.LocallyRejected(execution.reason)
            is DosingExecutionOutcome.Completed -> when (val outcome = execution.outcome) {
                is DeviceRuntimeCommandOutcome.Success -> commitMutation(
                    AcceptedDosingMutation(
                        address = address,
                        token = token,
                        outcome = outcome,
                        channel = channel,
                        persistedMutation = persistedMutation,
                        onAccepted = onAccepted
                    )
                )
                else -> conflictCoordinator.reconcile(address, outcome)
            }
        }
    }

    private suspend fun <T> executeMutation(
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
                reconcileMutation(
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

    private suspend fun <T> reconcileMutation(
        address: DeviceDosingV1Address,
        value: T,
        disposition: DeviceDosingV1CommitDisposition,
        persistedMutation: Boolean,
        committedRevision: Long
    ): DeviceDosingV1MutationResult<T> = when (disposition) {
        DeviceDosingV1CommitDisposition.MALFORMED -> DeviceDosingV1MutationResult.Malformed
        DeviceDosingV1CommitDisposition.STALE_CONNECTION -> resolveAcceptedReadback(
            address = address,
            value = value,
            persistedMutation = persistedMutation,
            committedRevision = committedRevision,
            refreshed = refreshCoordinator.refreshWithinGate(address)
        )
        else -> refreshAccepted(
            address = address,
            value = value,
            persistedMutation = persistedMutation,
            committedRevision = committedRevision
        )
    }

    private suspend fun <T> refreshAccepted(
        address: DeviceDosingV1Address,
        value: T,
        persistedMutation: Boolean,
        committedRevision: Long
    ): DeviceDosingV1MutationResult<T> = resolveAcceptedReadback(
        address = address,
        value = value,
        persistedMutation = persistedMutation,
        committedRevision = committedRevision,
        refreshed = refreshCoordinator.refreshWithinGate(address)
    )

    /**
     * A successful firmware response is the commit boundary for persisted writes. Readback is a
     * separate synchronization step: losing transport after the ACK must never replay or misreport
     * a durable write. Runtime mutations keep their stricter existing readback requirement.
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
                state = refreshedState
            )
            currentState != null -> acceptedReadbackResult(
                value = value,
                committedRevision = committedRevision,
                state = currentState
            )
            persistedMutation -> DeviceDosingV1MutationResult.Committed(value, committedRevision)
            refreshed is DeviceDosingV1RefreshResult.Malformed -> DeviceDosingV1MutationResult.Malformed
            refreshed is DeviceDosingV1RefreshResult.Failed ||
                refreshed == DeviceDosingV1RefreshResult.RejectedStale ->
                DeviceDosingV1MutationResult.RejectedStale
            else -> error("Successful refresh was handled above")
        }
    }

    private suspend fun authoritativeBaseline(
        address: DeviceDosingV1Address
    ): DeviceDosingV1AuthoritativeState? = stateAccess.currentState(address) ?: when (
        val refreshed = refreshCoordinator.refreshWithinGate(address)
    ) {
        is DeviceDosingV1RefreshResult.Success -> refreshed.state
        else -> null
    }
}

private fun <T> acceptedReadbackResult(
    value: T,
    committedRevision: Long,
    state: DeviceDosingV1AuthoritativeState
): DeviceDosingV1MutationResult<T> = if (state.channel.revision >= committedRevision) {
    DeviceDosingV1MutationResult.Success(value, state)
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

private sealed interface DosingExecutionOutcome<out T> {
    data class Completed<T>(val outcome: DeviceRuntimeCommandOutcome<T>) : DosingExecutionOutcome<T>
    data class Rejected(val reason: DeviceDosingChannelRejection) : DosingExecutionOutcome<Nothing>
}
