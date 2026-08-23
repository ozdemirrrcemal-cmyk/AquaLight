package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration

/**
 * Reconciles mutation failures against a fresh mutation-critical device readback.
 *
 * Ambiguous assignment outcomes are never replayed blindly. A successful readback records a
 * recovered checkpoint so the latest-intent owner restarts from the newest desired assignment. If
 * the transport is still unavailable, the checkpoint remains pending until authenticated Dosing
 * bootstrap resumes it.
 */
internal class DeviceDosingV1ConflictCoordinator(
    private val stateOwner: DeviceDosingV1StateOwner,
    private val refreshCoordinator: DeviceDosingV1RefreshCoordinator,
    private val recoveryGate: DeviceDosingV1AssignmentRecoveryGate
) {
    suspend fun <T> reconcile(
        address: DeviceDosingV1Address,
        outcome: DeviceRuntimeCommandOutcome<T>
    ): DeviceDosingV1MutationResult<T> {
        revokeAuthority(address, outcome)

        if (outcome.isAmbiguousAssignmentOutcome()) {
            reconcileAmbiguousOutcome(address, outcome)
            return DeviceDosingV1MutationResult.Failed(outcome)
        }

        val refreshed = refreshCoordinator.refreshForMutation(address)
        if (refreshed is DeviceDosingV1RefreshResult.Failed) {
            recordBaselineRecoveryIfRequired(address, refreshed.outcome)
        }
        return if (outcome.isRevisionConflict()) {
            DeviceDosingV1MutationResult.Conflict
        } else {
            DeviceDosingV1MutationResult.Failed(outcome)
        }
    }

    /**
     * A baseline read failed before a write was issued. Only definite connection interruptions are
     * retained for reconnect; a plain read timeout/protocol error remains a visible terminal read
     * failure and cannot create an infinite recovery wait.
     */
    fun recordBaselineRecoveryIfRequired(
        address: DeviceDosingV1Address,
        outcome: DeviceRuntimeCommandOutcome<*>
    ): Boolean {
        if (!outcome.requiresReconnect()) return false
        revokeAuthority(address, outcome)
        recoveryGate.markTransportInterrupted(address.deviceUid)
        return true
    }

    private suspend fun reconcileAmbiguousOutcome(
        address: DeviceDosingV1Address,
        originalOutcome: DeviceRuntimeCommandOutcome<*>
    ) {
        // One immediate authoritative read is safe: if reconnect already completed before the
        // cancelled waiter resumed, it closes the ambiguity without waiting for another lifecycle
        // edge. It never retries the old write.
        when (val refreshed = refreshCoordinator.refreshForMutation(address)) {
            is DeviceDosingV1RefreshResult.Success ->
                recoveryGate.markRecoveredInterruption(address.deviceUid)

            is DeviceDosingV1RefreshResult.Failed -> {
                if (refreshed.outcome.requiresReconnect()) {
                    revokeAuthority(address, refreshed.outcome)
                    recoveryGate.markTransportInterrupted(address.deviceUid)
                }
                Unit
            }

            DeviceDosingV1RefreshResult.RejectedStale -> {
                // A newer central result already won. Restarting the latest assignment is safe and
                // avoids reusing the pre-failure target/revision.
                recoveryGate.markRecoveredInterruption(address.deviceUid)
            }

            DeviceDosingV1RefreshResult.Malformed -> {
                if (originalOutcome.requiresReconnect()) {
                    recoveryGate.markTransportInterrupted(address.deviceUid)
                }
                Unit
            }
        }
    }

    private fun revokeAuthority(
        address: DeviceDosingV1Address,
        outcome: DeviceRuntimeCommandOutcome<*>
    ) {
        val generation = outcome.connectionGenerationOrNull()
        if (generation != null) {
            stateOwner.invalidate(address.deviceUid, address.channelKey, generation, null)
        } else if (outcome is DeviceRuntimeCommandOutcome.NotConnected) {
            stateOwner.invalidateAll(address.deviceUid)
        }
    }
}

private fun DeviceRuntimeCommandOutcome<*>.isAmbiguousAssignmentOutcome(): Boolean = when (this) {
    is DeviceRuntimeCommandOutcome.NotConnected,
    is DeviceRuntimeCommandOutcome.NotAuthenticated,
    is DeviceRuntimeCommandOutcome.SendFailed,
    is DeviceRuntimeCommandOutcome.Timeout,
    is DeviceRuntimeCommandOutcome.ProtocolError -> true
    is DeviceRuntimeCommandOutcome.Cancelled -> isRecoverableTransportCancellation()
    is DeviceRuntimeCommandOutcome.Success<*>,
    is DeviceRuntimeCommandOutcome.UnsupportedByDevice,
    is DeviceRuntimeCommandOutcome.FirmwareError -> false
}

private fun DeviceRuntimeCommandOutcome<*>.requiresReconnect(): Boolean = when (this) {
    is DeviceRuntimeCommandOutcome.NotConnected,
    is DeviceRuntimeCommandOutcome.NotAuthenticated,
    is DeviceRuntimeCommandOutcome.SendFailed -> true
    is DeviceRuntimeCommandOutcome.Cancelled -> isRecoverableTransportCancellation()
    is DeviceRuntimeCommandOutcome.Success<*>,
    is DeviceRuntimeCommandOutcome.UnsupportedByDevice,
    is DeviceRuntimeCommandOutcome.Timeout,
    is DeviceRuntimeCommandOutcome.FirmwareError,
    is DeviceRuntimeCommandOutcome.ProtocolError -> false
}

private fun DeviceRuntimeCommandOutcome.Cancelled.isRecoverableTransportCancellation(): Boolean =
    reason == "runtime connection replaced" ||
        reason == "local network route changed" ||
        reason == "local network unavailable" ||
        reason == "runtime transport unavailable" ||
        reason == "runtime socket closed" ||
        reason == "runtime socket failure"

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
