package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelRejection
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingDiagnosticTrace
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
    ): DeviceDosingV1MutationResult<T> {
        val address = stateAccess.address(deviceUid, slotId)
        val diagnosticId = DeviceDosingDiagnosticTrace.beginPersistedMutation(
            deviceUid = address.deviceUid.value,
            slotId = DeviceDosingV1SlotKeyMapper.slotId(address.channelKey)
        )
        traceDiagnostic(address, diagnosticId, "GATE", "waiting for channel serialization")
        return mutateSerialized(
            address = address,
            requiresRevision = true,
            execute = execute,
            channel = channel,
            onAccepted = onAccepted,
            diagnosticId = diagnosticId
        )
    }

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
        onAccepted: () -> Unit = {},
        diagnosticId: Long? = null
    ): DeviceDosingV1MutationResult<T> = operationGate.withChannel(address) {
        traceDiagnostic(address, diagnosticId, "GATE", "entered channel serialization")
        val baseline = authoritativeBaseline(address, diagnosticId)
            ?: return@withChannel DeviceDosingV1MutationResult.Malformed.also {
                traceDiagnostic(address, diagnosticId, "BASELINE", "FAILED no authoritative state")
            }
        traceDiagnostic(
            address,
            diagnosticId,
            "BASELINE",
            "authoritativeRev=${baseline.channel.revision} requiresRevision=$requiresRevision"
        )
        val revision = if (requiresRevision) {
            stateAccess.authoritativeRevision(address)
                ?: return@withChannel DeviceDosingV1MutationResult.Malformed.also {
                    traceDiagnostic(address, diagnosticId, "REVISION", "FAILED authoritative revision missing")
                }
        } else {
            baseline.channel.revision
        }
        traceDiagnostic(address, diagnosticId, "REVISION", "selected expectedRev=$revision")
        val token = stateOwner.beginRequest(address.deviceUid, address.channelKey)
        traceDiagnostic(address, diagnosticId, "TOKEN", "requestGeneration=${token.requestGeneration}")
        when (val execution = executeMutation(address, revision, baseline.channel, execute)) {
            is DosingExecutionOutcome.Rejected -> {
                traceDiagnostic(address, diagnosticId, "COMMAND", "LOCAL_REJECT reason=${execution.reason}")
                DeviceDosingV1MutationResult.LocallyRejected(execution.reason)
            }
            is DosingExecutionOutcome.Completed -> {
                val outcome = execution.outcome
                traceDiagnostic(address, diagnosticId, "COMMAND", outcome.mutationDiagnosticSummary())
                when (outcome) {
                    is DeviceRuntimeCommandOutcome.Success ->
                        commitMutation(address, token, outcome, channel, onAccepted, diagnosticId)
                    else -> conflictCoordinator.reconcile(address, outcome, diagnosticId)
                }
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
        onAccepted: () -> Unit,
        diagnosticId: Long?
    ): DeviceDosingV1MutationResult<T> {
        val detail = runCatching { channel(outcome.value) }.getOrElse { error ->
            traceDiagnostic(
                address,
                diagnosticId,
                "PARSE",
                "FAILED ${error::class.simpleName ?: "unknown"}: ${error.message.orEmpty().take(100)}"
            )
            return DeviceDosingV1MutationResult.Malformed
        }
        traceDiagnostic(address, diagnosticId, "PARSE", "responseRev=${detail.revision}")
        return recordMutation(address, token, outcome, detail, onAccepted, diagnosticId)
    }

    private suspend fun <T> recordMutation(
        address: DeviceDosingV1Address,
        token: DeviceDosingV1RequestToken,
        outcome: DeviceRuntimeCommandOutcome.Success<T>,
        detail: DeviceDosingV1ChannelDetail,
        onAccepted: () -> Unit,
        diagnosticId: Long?
    ): DeviceDosingV1MutationResult<T> = runCatching {
        stateOwner.recordMutation(token, outcome.generation, detail)
    }.fold(
        onSuccess = { disposition ->
            traceDiagnostic(address, diagnosticId, "OWNER", "recordMutation=$disposition")
            reconcileMutation(address, outcome.value, disposition, onAccepted, diagnosticId)
        },
        onFailure = { error ->
            traceDiagnostic(
                address,
                diagnosticId,
                "OWNER",
                "recordMutation THREW ${error::class.simpleName ?: "unknown"}"
            )
            DeviceDosingV1MutationResult.Malformed
        }
    )

    private suspend fun <T> reconcileMutation(
        address: DeviceDosingV1Address,
        value: T,
        disposition: DeviceDosingV1CommitDisposition,
        onAccepted: () -> Unit,
        diagnosticId: Long?
    ): DeviceDosingV1MutationResult<T> = when (disposition) {
        DeviceDosingV1CommitDisposition.STALE_CONNECTION -> {
            traceDiagnostic(address, diagnosticId, "OWNER", "STALE_CONNECTION -> refresh")
            refreshCoordinator.refreshWithinGate(address, diagnosticId)
            DeviceDosingV1MutationResult.RejectedStale
        }
        DeviceDosingV1CommitDisposition.MALFORMED -> {
            traceDiagnostic(address, diagnosticId, "OWNER", "MALFORMED")
            DeviceDosingV1MutationResult.Malformed
        }
        else -> {
            onAccepted()
            refreshAccepted(address, value, diagnosticId)
        }
    }

    private suspend fun <T> refreshAccepted(
        address: DeviceDosingV1Address,
        value: T,
        diagnosticId: Long?
    ): DeviceDosingV1MutationResult<T> = when (
        val refreshed = refreshCoordinator.refreshWithinGate(address, diagnosticId)
    ) {
        is DeviceDosingV1RefreshResult.Success -> {
            traceDiagnostic(
                address,
                diagnosticId,
                "RESULT",
                "SUCCESS authoritativeRev=${refreshed.state.channel.revision}"
            )
            DeviceDosingV1MutationResult.Success(value, refreshed.state)
        }
        DeviceDosingV1RefreshResult.Malformed -> {
            traceDiagnostic(address, diagnosticId, "RESULT", "MALFORMED after accepted mutation")
            DeviceDosingV1MutationResult.Malformed
        }
        is DeviceDosingV1RefreshResult.Failed,
        DeviceDosingV1RefreshResult.RejectedStale -> stateAccess.currentState(address)?.let { state ->
            traceDiagnostic(
                address,
                diagnosticId,
                "RESULT",
                "SUCCESS via retained authoritative state rev=${state.channel.revision} refresh=$refreshed"
            )
            DeviceDosingV1MutationResult.Success(value, state)
        } ?: DeviceDosingV1MutationResult.RejectedStale.also {
            traceDiagnostic(address, diagnosticId, "RESULT", "REJECTED_STALE refresh=$refreshed")
        }
    }

    private suspend fun authoritativeBaseline(
        address: DeviceDosingV1Address,
        diagnosticId: Long?
    ): DeviceDosingV1AuthoritativeState? = stateAccess.currentState(address)?.also { state ->
        traceDiagnostic(address, diagnosticId, "BASELINE", "cache hit rev=${state.channel.revision}")
    } ?: run {
        traceDiagnostic(address, diagnosticId, "BASELINE", "cache miss -> authoritative refresh")
        when (val refreshed = refreshCoordinator.refreshWithinGate(address, diagnosticId)) {
            is DeviceDosingV1RefreshResult.Success -> refreshed.state
            else -> null.also {
                traceDiagnostic(address, diagnosticId, "BASELINE", "refresh failed: $refreshed")
            }
        }
    }
}

private sealed interface DosingExecutionOutcome<out T> {
    data class Completed<T>(val outcome: DeviceRuntimeCommandOutcome<T>) : DosingExecutionOutcome<T>
    data class Rejected(val reason: DeviceDosingChannelRejection) : DosingExecutionOutcome<Nothing>
}

private fun DeviceRuntimeCommandOutcome<*>.mutationDiagnosticSummary(): String {
    val base = dosingDiagnosticSummary()
    val responseRevision = (this as? DeviceRuntimeCommandOutcome.Success<*>)
        ?.value
        ?.let { value -> value as? DeviceDosingV1SavedMutationResult }
        ?.channel
        ?.revision
    return if (responseRevision == null) base else "$base responseRev=$responseRevision"
}

private fun traceDiagnostic(
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
