package com.aqua.aqualight.application.devices.dosing

/**
 * Revision-aware program mutation capability for long-lived editors.
 *
 * The normal channel boundary remains the public application contract. Editors that keep a draft
 * across authoritative state updates use this capability so a stale draft can never be persisted
 * against a newer firmware revision without an explicit conflict round-trip.
 */
interface DeviceDosingProgramRevisionOperations {
    suspend fun applyProgramAtRevision(
        deviceUid: String,
        slotId: String,
        program: DeviceDosingProgram,
        expectedRevision: Long
    ): DeviceDosingChannelOperationResult
}

/** Fail closed when a backing implementation cannot guarantee revision-aware program mutation. */
suspend fun DeviceDosingChannelOperations.applyProgramAtRevision(
    deviceUid: String,
    slotId: String,
    program: DeviceDosingProgram,
    expectedRevision: Long
): DeviceDosingChannelOperationResult =
    (this as? DeviceDosingProgramRevisionOperations)?.applyProgramAtRevision(
        deviceUid = deviceUid,
        slotId = slotId,
        program = program,
        expectedRevision = expectedRevision
    ) ?: DeviceDosingChannelOperationResult.Failed
