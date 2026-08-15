package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelRejection
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes mutations per channel and reconciles their authoritative refresh. */
internal class DeviceDosingV1MutationCoordinator(
    private val stateOwner: DeviceDosingV1StateOwner,
    private val stateAccess: DeviceDosingV1StateAccess,
    private val refreshCoordinator: DeviceDosingV1RefreshCoordinator
) {
    private val mutationLocks = ConcurrentHashMap<DeviceDosingV1Address, Mutex>()

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
        requiresRevision = true,
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
        requiresRevision = false,
        execute = execute,
        channel = channel
    )

    private suspend fun <T> mutateSerialized(
        address: DeviceDosingV1Address,
        requiresRevision: Boolean,
        execute: suspend (
            DeviceUid,
            DeviceDosingV1ChannelKey,
            Long,
            DeviceDosingChannelSnapshot
        ) -> DeviceRuntimeCommandOutcome<T>,
        channel: (T) -> DeviceDosingV1ChannelDetail,
        onAccepted: () -> Unit = {}
    ): DeviceDosingV1MutationResult<T> = mutationLock(address).withLock {
        val baseline = authoritativeBaseline(address)
            ?: return@withLock DeviceDosingV1MutationResult.Malformed
        val revision = if (requiresRevision) {
            stateAccess.authoritativeRevision(address)
                ?: return@withLock DeviceDosingV1MutationResult.Malformed
        } else {
            baseline.channel.revision
        }
        val token = stateOwner.beginRequest(address.deviceUid, address.channelKey)
        when (val execution = executeMutation(address, revision, baseline.channel, execute)) {
            is DosingExecutionOutcome.Rejected ->
                DeviceDosingV1MutationResult.LocallyRejected(execution.reason)
            is DosingExecutionOutcome.Completed -> when (val outcome = execution.outcome) {
                is DeviceRuntimeCommandOutcome.Success ->
                    commitMutation(address, token, outcome, channel, onAccepted)
                else -> failedMutation(address, outcome)
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
        address: DeviceDosingV1Address,
        token: DeviceDosingV1RequestToken,
        outcome: DeviceRuntimeCommandOutcome.Success<T>,
        channel: (T) -> DeviceDosingV1ChannelDetail,
        onAccepted: () -> Unit
    ): DeviceDosingV1MutationResult<T> = runCatching { channel(outcome.value) }.fold(
        onSuccess = { detail -> recordMutation(address, token, outcome, detail, onAccepted) },
        onFailure = { DeviceDosingV1MutationResult.Malformed }
    )

    private suspend fun <T> recordMutation(
        address: DeviceDosingV1Address,
        token: DeviceDosingV1RequestToken,
        outcome: DeviceRuntimeCommandOutcome.Success<T>,
        detail: DeviceDosingV1ChannelDetail,
        onAccepted: () -> Unit
    ): DeviceDosingV1MutationResult<T> = runCatching {
        stateOwner.recordMutation(token, outcome.generation, detail)
    }.fold(
        onSuccess = { disposition ->
            reconcileMutation(address, outcome.value, disposition, onAccepted)
        },
        onFailure = { DeviceDosingV1MutationResult.Malformed }
    )

    private suspend fun <T> reconcileMutation(
        address: DeviceDosingV1Address,
        value: T,
        disposition: DeviceDosingV1CommitDisposition,
        onAccepted: () -> Unit
    ): DeviceDosingV1MutationResult<T> = when (disposition) {
        DeviceDosingV1CommitDisposition.STALE_CONNECTION -> {
            refreshCoordinator.refresh(address)
            DeviceDosingV1MutationResult.RejectedStale
        }
        DeviceDosingV1CommitDisposition.MALFORMED -> DeviceDosingV1MutationResult.Malformed
        else -> {
            onAccepted()
            refreshAccepted(address, value)
        }
    }

    private suspend fun <T> refreshAccepted(
        address: DeviceDosingV1Address,
        value: T
    ): DeviceDosingV1MutationResult<T> = when (val refreshed = refreshCoordinator.refresh(address)) {
        is DeviceDosingV1RefreshResult.Success -> DeviceDosingV1MutationResult.Success(
            value = value,
            state = refreshed.state
        )
        DeviceDosingV1RefreshResult.Malformed -> DeviceDosingV1MutationResult.Malformed
        is DeviceDosingV1RefreshResult.Failed,
        DeviceDosingV1RefreshResult.RejectedStale -> stateAccess.currentState(address)?.let { state ->
            DeviceDosingV1MutationResult.Success(value, state)
        } ?: DeviceDosingV1MutationResult.RejectedStale
    }

    private suspend fun authoritativeBaseline(
        address: DeviceDosingV1Address
    ): DeviceDosingV1AuthoritativeState? = stateAccess.currentState(address) ?: when (
        val refreshed = refreshCoordinator.refresh(address)
    ) {
        is DeviceDosingV1RefreshResult.Success -> refreshed.state
        else -> null
    }

    private suspend fun <T> failedMutation(
        address: DeviceDosingV1Address,
        outcome: DeviceRuntimeCommandOutcome<T>
    ): DeviceDosingV1MutationResult<T> = if (outcome.isRevisionConflict()) {
        outcome.connectionGenerationOrNull()?.let { generation ->
            stateOwner.invalidate(address.deviceUid, address.channelKey, generation, null)
        }
        refreshCoordinator.refresh(address)
        DeviceDosingV1MutationResult.Conflict
    } else {
        DeviceDosingV1MutationResult.Failed(outcome)
    }

    private fun mutationLock(address: DeviceDosingV1Address): Mutex =
        mutationLocks.computeIfAbsent(address) { Mutex() }
}

private sealed interface DosingExecutionOutcome<out T> {
    data class Completed<T>(
        val outcome: DeviceRuntimeCommandOutcome<T>
    ) : DosingExecutionOutcome<T>

    data class Rejected(
        val reason: DeviceDosingChannelRejection
    ) : DosingExecutionOutcome<Nothing>
}

private fun DeviceRuntimeCommandOutcome<*>.isRevisionConflict(): Boolean =
    this is DeviceRuntimeCommandOutcome.FirmwareError && hasStaleRevisionError()

private fun DeviceRuntimeCommandOutcome.FirmwareError.hasStaleRevisionError(): Boolean =
    code == "INVALID_VALUE" &&
        field == "expectedRevision" &&
        message == "stale dosing channel revision"

private fun DeviceRuntimeCommandOutcome<*>.connectionGenerationOrNull():
    DeviceRuntimeConnectionGeneration? = when (this) {
        is DeviceRuntimeCommandOutcome.Success<*> -> generation
        is DeviceRuntimeCommandOutcome.NotAuthenticated -> generation
        is DeviceRuntimeCommandOutcome.SendFailed -> generation
        is DeviceRuntimeCommandOutcome.Timeout -> generation
        is DeviceRuntimeCommandOutcome.FirmwareError -> generation
        is DeviceRuntimeCommandOutcome.ProtocolError -> generation
        is DeviceRuntimeCommandOutcome.Cancelled -> generation
        is DeviceRuntimeCommandOutcome.NotConnected,
        is DeviceRuntimeCommandOutcome.UnsupportedByDevice -> null
    }
