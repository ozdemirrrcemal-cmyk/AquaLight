package com.aqua.aqualight.application.devices.dosing

import com.aqua.aqualight.debug.dosing.DosingDebugTrace

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
    ): DeviceDosingChannelOperationResult = tracedMutation(
        label = "PROGRAM",
        deviceUid = deviceUid,
        slotId = slotId,
        detail = program.traceSummary(),
        execute = { delegate.applyProgram(deviceUid, slotId, program) },
        assignmentSatisfied = { snapshot -> snapshot.program == program }
    )

    override suspend fun applyProgramAtRevision(
        deviceUid: String,
        slotId: String,
        program: DeviceDosingProgram,
        expectedRevision: Long
    ): DeviceDosingChannelOperationResult = tracedMutation(
        label = "PLAN",
        deviceUid = deviceUid,
        slotId = slotId,
        detail = "expectedRev=$expectedRevision ${program.traceSummary()}",
        execute = {
            programRevisionOperations.applyProgramAtRevision(
                deviceUid = deviceUid,
                slotId = slotId,
                program = program,
                expectedRevision = expectedRevision
            )
        },
        assignmentSatisfied = { snapshot -> snapshot.program.hasSamePlanAssignment(program) }
    )

    override suspend fun setMissedDoseRecoveryEnabled(
        deviceUid: String,
        slotId: String,
        enabled: Boolean
    ): DeviceDosingChannelOperationResult = tracedMutation(
        label = "RECOVERY",
        deviceUid = deviceUid,
        slotId = slotId,
        detail = "target=$enabled",
        execute = { delegate.setMissedDoseRecoveryEnabled(deviceUid, slotId, enabled) },
        assignmentSatisfied = { snapshot ->
            snapshot.program?.missedDoseRecoveryEnabled == enabled
        }
    )

    override suspend fun applyReservoirSettings(
        deviceUid: String,
        slotId: String,
        settings: DeviceDosingReservoirSettings
    ): DeviceDosingChannelOperationResult = tracedMutation(
        label = "RESERVOIR",
        deviceUid = deviceUid,
        slotId = slotId,
        detail = settings.traceSummary(),
        execute = { delegate.applyReservoirSettings(deviceUid, slotId, settings) },
        assignmentSatisfied = { snapshot -> snapshot.reservoir.matches(settings) }
    )

    override suspend fun applyReservoirSettingsAtRevision(
        deviceUid: String,
        slotId: String,
        settings: DeviceDosingReservoirSettings,
        expectedRevision: Long
    ): DeviceDosingChannelOperationResult = tracedMutation(
        label = "RESERVOIR",
        deviceUid = deviceUid,
        slotId = slotId,
        detail = "expectedRev=$expectedRevision ${settings.traceSummary()}",
        execute = {
            reservoirRevisionOperations.applyReservoirSettingsAtRevision(
                deviceUid = deviceUid,
                slotId = slotId,
                settings = settings,
                expectedRevision = expectedRevision
            )
        },
        assignmentSatisfied = { snapshot -> snapshot.reservoir.matches(settings) }
    )

    override suspend fun reset(
        deviceUid: String,
        slotId: String
    ): DeviceDosingChannelOperationResult = tracedMutation(
        label = "RESET",
        deviceUid = deviceUid,
        slotId = slotId,
        detail = "channel reset",
        execute = { delegate.reset(deviceUid, slotId) },
        assignmentSatisfied = { snapshot -> !snapshot.calibrated && snapshot.program == null }
    )

    private suspend fun tracedMutation(
        label: String,
        deviceUid: String,
        slotId: String,
        detail: String,
        execute: suspend () -> DeviceDosingChannelOperationResult,
        assignmentSatisfied: (DeviceDosingChannelSnapshot) -> Boolean
    ): DeviceDosingChannelOperationResult {
        val operationId = DosingDebugTrace.nextOperationId(label)
        val address = "device=${DosingDebugTrace.shortDevice(deviceUid)} slot=$slotId"
        DosingDebugTrace.log("OP", "BEGIN $address $detail", operationId)
        val rawResult = try {
            execute()
        } catch (error: Throwable) {
            DosingDebugTrace.log(
                "OP",
                "THROW $address ${error::class.java.simpleName}: " +
                    DosingDebugTrace.compact(
                        error.message.orEmpty(),
                        TRACE_THROW_MESSAGE_CHARS
                    ),
                operationId
            )
            throw error
        }
        DosingDebugTrace.log("OP", "MUTATION ${rawResult.traceSummary()}", operationId)
        return reconcileCommitted(
            deviceUid = deviceUid,
            slotId = slotId,
            result = rawResult,
            operationId = operationId,
            assignmentSatisfied = assignmentSatisfied
        ).also { final ->
            DosingDebugTrace.log("OP", "END ${final.traceSummary()}", operationId)
        }
    }

    private suspend fun reconcileCommitted(
        deviceUid: String,
        slotId: String,
        result: DeviceDosingChannelOperationResult,
        operationId: String,
        assignmentSatisfied: (DeviceDosingChannelSnapshot) -> Boolean
    ): DeviceDosingChannelOperationResult {
        val committed = result as? DeviceDosingChannelCommittedResult ?: return result
        DosingDebugTrace.log(
            "ACK",
            "COMMITTED rev=${committed.revision}; targeted refresh start slot=$slotId",
            operationId
        )
        val readback = delegate.refresh(deviceUid, slotId)
        DosingDebugTrace.log("READBACK", readback.traceSummary(), operationId)
        return when (readback) {
            is DeviceDosingChannelOperationResult.Success -> {
                val revisionSatisfied = readback.snapshot.revision >= committed.revision
                val assignmentMatches = assignmentSatisfied(readback.snapshot)
                DosingDebugTrace.log(
                    "VERIFY",
                    "ackRev=${committed.revision} readbackRev=${readback.snapshot.revision} " +
                        "revisionOk=$revisionSatisfied assignmentOk=$assignmentMatches " +
                        "recovery=${readback.snapshot.program?.missedDoseRecoveryEnabled} " +
                        "executionCurrent=${readback.snapshot.progress.executionCurrent}",
                    operationId
                )
                readback.takeIf { revisionSatisfied && assignmentMatches }
                    ?: DeviceDosingChannelOperationResult.Failed
            }
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

private fun DeviceDosingProgram.traceSummary(): String =
    "enabled=$enabled recovery=$missedDoseRecoveryEnabled " +
        "schedule=${schedule::class.java.simpleName} weekdays=${weekdays.count { it }}"

private fun DeviceDosingReservoirSettings.traceSummary(): String =
    "tracking=$trackingEnabled capacity=$capacityMicroliters alert=$lowLevelAlertEnabled"

private fun DeviceDosingChannelOperationResult.traceSummary(): String = when (this) {
    is DeviceDosingChannelOperationResult.Success ->
        "SUCCESS rev=${snapshot.revision} recovery=${snapshot.program?.missedDoseRecoveryEnabled} " +
            "executionCurrent=${snapshot.progress.executionCurrent}"
    is DeviceDosingChannelCommittedResult -> "COMMITTED rev=$revision"
    is DeviceDosingChannelOperationResult.Rejected -> "REJECTED reason=$reason"
    DeviceDosingChannelOperationResult.Unavailable -> "UNAVAILABLE"
    DeviceDosingChannelOperationResult.Failed -> "FAILED"
}

private const val TRACE_THROW_MESSAGE_CHARS = 300
