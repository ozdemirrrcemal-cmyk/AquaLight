package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration

/** Reconciles optimistic-revision conflicts against authoritative device state. */
internal class DeviceDosingV1ConflictCoordinator(
    private val stateOwner: DeviceDosingV1StateOwner,
    private val refreshCoordinator: DeviceDosingV1RefreshCoordinator
) {
    suspend fun <T> reconcile(
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
