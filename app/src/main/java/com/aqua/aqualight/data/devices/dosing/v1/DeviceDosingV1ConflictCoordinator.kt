package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.application.devices.dosing.DeviceDosingDiagnosticTrace
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration

/** Reconciles mutation failures against authoritative device state. */
internal class DeviceDosingV1ConflictCoordinator(
    private val stateOwner: DeviceDosingV1StateOwner,
    private val refreshCoordinator: DeviceDosingV1RefreshCoordinator
) {
    suspend fun <T> reconcile(
        address: DeviceDosingV1Address,
        outcome: DeviceRuntimeCommandOutcome<T>,
        diagnosticId: Long? = null
    ): DeviceDosingV1MutationResult<T> = if (outcome.isRevisionConflict()) {
        traceConflict(address, diagnosticId, "RECONCILE", "revision conflict -> refresh")
        outcome.connectionGenerationOrNull()?.let { generation ->
            stateOwner.invalidate(address.deviceUid, address.channelKey, generation, null)
        }
        val refreshed = refreshCoordinator.refreshWithinGate(address, diagnosticId)
        traceConflict(address, diagnosticId, "RECONCILE", "conflict refresh=$refreshed")
        DeviceDosingV1MutationResult.Conflict
    } else {
        // A failed command can still coincide with device-side state changes (for example BUSY).
        // Reconcile centrally so every observer sees the latest authoritative snapshot; the UI
        // never invents or preserves a parallel runtime state after a failed mutation.
        traceConflict(
            address,
            diagnosticId,
            "RECONCILE",
            "command failed (${outcome.dosingDiagnosticSummary()}) -> refresh device state"
        )
        val refreshed = refreshCoordinator.refreshWithinGate(address, diagnosticId)
        traceConflict(address, diagnosticId, "RECONCILE", "failure refresh=$refreshed")
        DeviceDosingV1MutationResult.Failed(outcome)
    }
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

private fun traceConflict(
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
