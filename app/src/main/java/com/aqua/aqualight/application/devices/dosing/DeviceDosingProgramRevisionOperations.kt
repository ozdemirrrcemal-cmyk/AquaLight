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

/**
 * Application-semantic optimistic-concurrency entry point for long-lived editors.
 *
 * UI code owns only the revision of the application snapshot from which its draft was derived.
 * Translation of that base revision into the firmware mutation contract remains behind the
 * application boundary and is implemented by the revision-aware data adapter.
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
