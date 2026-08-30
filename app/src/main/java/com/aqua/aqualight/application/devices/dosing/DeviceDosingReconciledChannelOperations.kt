package com.aqua.aqualight.application.devices.dosing

/**
 * Production-facing Dosing mutation boundary that does not report a durable firmware ACK as a
 * completed user operation until the same channel has been read back authoritatively.
 *
 * The delegate remains the only state-owning runtime boundary. This decorator owns no snapshot,
 * revision, transport or retry state: when a persisted mutation returns [DeviceDosingChannelCommittedResult],
 * it performs one targeted [DeviceDosingChannelOperations.refresh] for the same device/slot and
 * exposes success only after the committed revision and requested assignment are visible in that
 * authoritative snapshot. Device-wide refresh remains reserved for bootstrap/reconnect/time-wide
 * invalidation paths.
 */
internal class DeviceDosingReconciledChannelOperations(
    private val delegate: DeviceDosingChannelOperations,
    private val programRevisionOperations: DeviceDosingProgramRevisionOperations =
        requireNotNull(delegate as? DeviceDosingProgramRevisionOperations),
    private val reservoirRevisionOperations: DeviceDosingReservoirRevisionOperations =
        requireNotNull(delegate as? DeviceDosingReservoirRevisionOperations)
) : DeviceDosingChannelOperations by delegate,
    DeviceDosingProgramRevisionOperations by programRevisionOperations,
    DeviceDosingReservoirRevisionOperations by reservoirRevisionOperations {

    override suspend fun applyProgram(
        deviceUid: String,
        slotId: String,
        program: DeviceDosingProgram
    ): DeviceDosingChannelOperationResult = reconcileCommitted(
        deviceUid = deviceUid,
        slotId = slotId,
        result = delegate.applyProgram(deviceUid, slotId, program),
        assignmentSatisfied = { snapshot -> snapshot.program == program }
    )

    override suspend fun applyProgramAtRevision(
        deviceUid: String,
        slotId: String,
        program: DeviceDosingProgram,
        expectedRevision: Long
    ): DeviceDosingChannelOperationResult = reconcileCommitted(
        deviceUid = deviceUid,
        slotId = slotId,
        result = programRevisionOperations.applyProgramAtRevision(
            deviceUid = deviceUid,
            slotId = slotId,
            program = program,
            expectedRevision = expectedRevision
        ),
        assignmentSatisfied = { snapshot -> snapshot.program.hasSamePlanAssignment(program) }
    )

    override suspend fun setMissedDoseRecoveryEnabled(
        deviceUid: String,
        slotId: String,
        enabled: Boolean
    ): DeviceDosingChannelOperationResult = reconcileCommitted(
        deviceUid = deviceUid,
        slotId = slotId,
        result = delegate.setMissedDoseRecoveryEnabled(deviceUid, slotId, enabled),
        assignmentSatisfied = { snapshot ->
            snapshot.program?.missedDoseRecoveryEnabled == enabled
        }
    )

    override suspend fun applyReservoirSettings(
        deviceUid: String,
        slotId: String,
        settings: DeviceDosingReservoirSettings
    ): DeviceDosingChannelOperationResult = reconcileCommitted(
        deviceUid = deviceUid,
        slotId = slotId,
        result = delegate.applyReservoirSettings(deviceUid, slotId, settings),
        assignmentSatisfied = { snapshot -> snapshot.reservoir.matches(settings) }
    )

    override suspend fun applyReservoirSettingsAtRevision(
        deviceUid: String,
        slotId: String,
        settings: DeviceDosingReservoirSettings,
        expectedRevision: Long
    ): DeviceDosingChannelOperationResult = reconcileCommitted(
        deviceUid = deviceUid,
        slotId = slotId,
        result = reservoirRevisionOperations.applyReservoirSettingsAtRevision(
            deviceUid = deviceUid,
            slotId = slotId,
            settings = settings,
            expectedRevision = expectedRevision
        ),
        assignmentSatisfied = { snapshot -> snapshot.reservoir.matches(settings) }
    )

    override suspend fun reset(
        deviceUid: String,
        slotId: String
    ): DeviceDosingChannelOperationResult = reconcileCommitted(
        deviceUid = deviceUid,
        slotId = slotId,
        result = delegate.reset(deviceUid, slotId),
        assignmentSatisfied = { snapshot -> !snapshot.calibrated && snapshot.program == null }
    )

    private suspend fun reconcileCommitted(
        deviceUid: String,
        slotId: String,
        result: DeviceDosingChannelOperationResult,
        assignmentSatisfied: (DeviceDosingChannelSnapshot) -> Boolean
    ): DeviceDosingChannelOperationResult {
        val committed = result as? DeviceDosingChannelCommittedResult ?: return result
        return when (val readback = delegate.refresh(deviceUid, slotId)) {
            is DeviceDosingChannelOperationResult.Success -> readback.takeIf { success ->
                success.snapshot.revision >= committed.revision &&
                    assignmentSatisfied(success.snapshot)
            } ?: DeviceDosingChannelOperationResult.Failed
            else -> readback
        }
    }
}

private fun DeviceDosingProgram?.hasSamePlanAssignment(
    desired: DeviceDosingProgram
): Boolean = this?.copy(missedDoseRecoveryEnabled = false) ==
    desired.copy(missedDoseRecoveryEnabled = false)

private fun DeviceDosingReservoirSnapshot.matches(
    settings: DeviceDosingReservoirSettings
): Boolean = trackingEnabled == settings.trackingEnabled &&
    (!settings.trackingEnabled || capacityMicroliters == settings.capacityMicroliters) &&
    lowLevelAlertEnabled == settings.lowLevelAlertEnabled
