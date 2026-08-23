package com.aqua.aqualight.application.devices.dosing

/**
 * Capability for consumers that need a coherent current-session snapshot before routing.
 *
 * Implementations join the central per-channel reconciliation domain instead of starting UI-owned
 * retry/readback loops. Falling back to [DeviceDosingChannelOperations.refresh] keeps test and
 * non-production adapters compatible without weakening the production contract.
 */
interface DeviceDosingAuthoritativeReconciliationOperations {
    suspend fun awaitAuthoritative(
        deviceUid: String,
        slotId: String
    ): DeviceDosingChannelOperationResult
}

suspend fun DeviceDosingChannelOperations.awaitAuthoritative(
    deviceUid: String,
    slotId: String
): DeviceDosingChannelOperationResult =
    (this as? DeviceDosingAuthoritativeReconciliationOperations)?.awaitAuthoritative(
        deviceUid = deviceUid,
        slotId = slotId
    ) ?: refresh(deviceUid, slotId)
