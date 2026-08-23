package com.aqua.aqualight.application.devices.dosing

/**
 * Revision-origin-aware program mutation capability for long-lived editors.
 *
 * [DeviceDosingProgramMutationOrigin] identifies the snapshot from which the draft originated.
 * The central data adapter owns the actual firmware compare-and-swap transaction: it rebases
 * plan-owned fields on the latest authoritative channel, preserves independently owned fields,
 * and retries only idempotent assignments. A normal unrelated revision advance is therefore not
 * a user-facing error.
 */
data class DeviceDosingProgramMutationOrigin(
    val revision: Long,
    val baseProgram: DeviceDosingProgram?
) {
    init {
        require(revision >= 0L)
    }
}

interface DeviceDosingProgramRevisionOperations {
    suspend fun applyProgramAtOrigin(
        deviceUid: String,
        slotId: String,
        program: DeviceDosingProgram,
        origin: DeviceDosingProgramMutationOrigin
    ): DeviceDosingChannelOperationResult
}

/**
 * Application-semantic optimistic-concurrency entry point for long-lived editors.
 *
 * UI code carries the application-domain origin from which its draft was derived. Wire translation,
 * latest-revision rebasing and reconciliation remain behind the application boundary.
 */
suspend fun DeviceDosingChannelOperations.applyProgramAgainstOrigin(
    deviceUid: String,
    slotId: String,
    program: DeviceDosingProgram,
    origin: DeviceDosingProgramMutationOrigin
): DeviceDosingChannelOperationResult =
    (this as? DeviceDosingProgramRevisionOperations)?.applyProgramAtOrigin(
        deviceUid = deviceUid,
        slotId = slotId,
        program = program,
        origin = origin
    ) ?: DeviceDosingChannelOperationResult.Failed
