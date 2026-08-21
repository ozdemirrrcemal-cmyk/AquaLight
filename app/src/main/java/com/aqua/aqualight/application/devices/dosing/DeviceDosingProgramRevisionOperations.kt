package com.aqua.aqualight.application.devices.dosing

/**
 * Revision-origin-aware program mutation capability for long-lived editors.
 *
 * [expectedRevision] identifies the snapshot from which the plan draft originated. The central
 * data adapter owns the actual firmware compare-and-swap transaction: it rebases plan-owned fields
 * on the latest authoritative channel, preserves independently owned fields, and retries only
 * idempotent assignments. A normal stale editor revision is therefore not a user-facing error.
 */
interface DeviceDosingProgramRevisionOperations {
    suspend fun applyProgramAtRevision(
        deviceUid: String,
        slotId: String,
        program: DeviceDosingProgram,
        expectedRevision: Long
    ): DeviceDosingChannelOperationResult
}

/**
 * Application-semantic optimistic-concurrency entry point for long-lived editors.
 *
 * UI code owns only the revision of the application snapshot from which its draft was derived.
 * Translation and reconciliation remain behind the application boundary.
 */
suspend fun DeviceDosingChannelOperations.applyProgramAgainstBaseRevision(
    deviceUid: String,
    slotId: String,
    program: DeviceDosingProgram,
    baseRevision: Long
): DeviceDosingChannelOperationResult =
    (this as? DeviceDosingProgramRevisionOperations)?.applyProgramAtRevision(
        deviceUid = deviceUid,
        slotId = slotId,
        program = program,
        expectedRevision = baseRevision
    ) ?: DeviceDosingChannelOperationResult.Failed
