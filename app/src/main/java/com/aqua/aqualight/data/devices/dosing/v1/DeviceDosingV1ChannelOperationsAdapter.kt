package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelCommittedResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperationResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelRejection
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgram
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgramRevisionOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingReservoirRevisionOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingReservoirSettings
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import kotlinx.coroutines.flow.Flow

/** Application channel boundary backed exclusively by the central v1 state adapter. */
@Suppress("TooManyFunctions") // This class implements the complete application boundary verbatim.
internal class DeviceDosingV1ChannelOperationsAdapter(
    private val adapter: DeviceDosingV1StateAdapter
) : DeviceDosingChannelOperations,
    DeviceDosingProgramRevisionOperations,
    DeviceDosingReservoirRevisionOperations {

    private val missedDoseRecoveryIntents =
        DeviceDosingV1MissedDoseRecoveryIntentCoordinator(
            scope = adapter.reconciliationScope,
            execute = ::persistMissedDoseRecoveryEnabled
        )

    override fun current(deviceUid: String, slotId: String): DeviceDosingChannelSnapshot? =
        adapter.stateAccess.currentChannel(deviceUid, slotId)

    override fun observe(
        deviceUid: String,
        slotId: String
    ): Flow<DeviceDosingChannelSnapshot?> = adapter.stateAccess.observeChannel(deviceUid, slotId)

    override fun observeAll(deviceUid: String): Flow<List<DeviceDosingChannelSnapshot>> =
        adapter.stateAccess.observeAll(deviceUid)

    override fun currentAll(deviceUid: String): List<DeviceDosingChannelSnapshot> =
        adapter.stateAccess.currentAll(deviceUid)

    override suspend fun refresh(
        deviceUid: String,
        slotId: String
    ): DeviceDosingChannelOperationResult =
        adapter.refreshCoordinator.refresh(deviceUid, slotId).toChannelResult()

    override suspend fun refreshAll(deviceUid: String): Boolean =
        adapter.refreshCoordinator.refreshAll(deviceUid)

    override suspend fun applyProgram(
        deviceUid: String,
        slotId: String,
        program: DeviceDosingProgram
    ): DeviceDosingChannelOperationResult = applyProgramInternal(
        deviceUid = deviceUid,
        slotId = slotId,
        program = program,
        expectedRevision = null
    )

    override suspend fun applyProgramAtRevision(
        deviceUid: String,
        slotId: String,
        program: DeviceDosingProgram,
        expectedRevision: Long
    ): DeviceDosingChannelOperationResult = if (expectedRevision < 0L) {
        DeviceDosingChannelOperationResult.Failed
    } else {
        applyProgramInternal(deviceUid, slotId, program, expectedRevision)
    }

    private suspend fun applyProgramInternal(
        deviceUid: String,
        slotId: String,
        program: DeviceDosingProgram,
        expectedRevision: Long?
    ): DeviceDosingChannelOperationResult = adapter.mutationCoordinator.mutatePersisted(
        deviceUid = deviceUid,
        slotId = slotId,
        mutation = DeviceDosingV1PersistedMutation(
            assignmentSatisfied = { snapshot ->
                if (expectedRevision != null) {
                    snapshot.program.hasSamePlanAssignment(program)
                } else {
                    snapshot.program == program
                }
            },
            execute = { uid, channelKey, revision, baseline ->
                requireMutation(
                    baseline.controls.programEditable,
                    DeviceDosingChannelRejection.NOT_EDITABLE
                )
                val effectiveProgram = if (expectedRevision != null) {
                    program.copy(
                        missedDoseRecoveryEnabled = baseline.program
                            ?.missedDoseRecoveryEnabled
                            ?: program.missedDoseRecoveryEnabled
                    )
                } else {
                    program
                }
                requireMutation(
                    effectiveProgram.isValidFor(baseline.scheduling),
                    DeviceDosingChannelRejection.INVALID_DRAFT
                )
                repositoryProgramApply(uid, channelKey, revision, effectiveProgram)
            },
            channel = DeviceDosingV1SavedMutationResult::channel
        )
    ).toChannelResult()

    override suspend fun setMissedDoseRecoveryEnabled(
        deviceUid: String,
        slotId: String,
        enabled: Boolean
    ): DeviceDosingChannelOperationResult = missedDoseRecoveryIntents.submit(
        deviceUid = deviceUid,
        slotId = slotId,
        enabled = enabled
    )

    private suspend fun persistMissedDoseRecoveryEnabled(
        deviceUid: String,
        slotId: String,
        enabled: Boolean
    ): DeviceDosingChannelOperationResult = adapter.mutationCoordinator.mutatePersisted(
        deviceUid = deviceUid,
        slotId = slotId,
        mutation = DeviceDosingV1PersistedMutation(
            assignmentSatisfied = { snapshot ->
                snapshot.program?.missedDoseRecoveryEnabled == enabled
            },
            execute = { uid, channelKey, revision, baseline ->
                requireMutation(
                    baseline.controls.programEditable,
                    DeviceDosingChannelRejection.NOT_EDITABLE
                )
                requireMutation(
                    baseline.scheduling.supportsMissedDoseRecovery,
                    DeviceDosingChannelRejection.NOT_EDITABLE
                )
                val current = baseline.program
                    ?: reject(DeviceDosingChannelRejection.INVALID_DRAFT)
                val updated = current.copy(missedDoseRecoveryEnabled = enabled)
                requireMutation(
                    updated.isValidFor(baseline.scheduling),
                    DeviceDosingChannelRejection.INVALID_DRAFT
                )
                repositoryProgramApply(uid, channelKey, revision, updated)
            },
            channel = DeviceDosingV1SavedMutationResult::channel
        )
    ).toChannelResult()

    override suspend fun applyReservoirSettings(
        deviceUid: String,
        slotId: String,
        settings: DeviceDosingReservoirSettings
    ): DeviceDosingChannelOperationResult = applyReservoirSettingsInternal(
        deviceUid = deviceUid,
        slotId = slotId,
        settings = settings
    )

    override suspend fun applyReservoirSettingsAtRevision(
        deviceUid: String,
        slotId: String,
        settings: DeviceDosingReservoirSettings,
        expectedRevision: Long
    ): DeviceDosingChannelOperationResult = if (expectedRevision < 0L) {
        DeviceDosingChannelOperationResult.Failed
    } else {
        applyReservoirSettingsInternal(deviceUid, slotId, settings)
    }

    private suspend fun applyReservoirSettingsInternal(
        deviceUid: String,
        slotId: String,
        settings: DeviceDosingReservoirSettings
    ): DeviceDosingChannelOperationResult = adapter.mutationCoordinator.mutatePersisted(
        deviceUid = deviceUid,
        slotId = slotId,
        mutation = DeviceDosingV1PersistedMutation(
            assignmentSatisfied = { snapshot ->
                snapshot.reservoir.trackingEnabled == settings.trackingEnabled &&
                    (!settings.trackingEnabled ||
                        snapshot.reservoir.capacityMicroliters == settings.capacityMicroliters)
            },
            execute = { uid, channelKey, revision, baseline ->
                requireMutation(
                    baseline.controls.reservoirEditable,
                    DeviceDosingChannelRejection.NOT_EDITABLE
                )
                repositoryConfigApply(
                    uid,
                    DeviceDosingV1ConfigApplyRequest(
                        channelKey = channelKey,
                        expectedRevision = revision,
                        reservoir = DeviceDosingV1ReservoirUpdate(
                            trackingEnabled = settings.trackingEnabled,
                            capacity = settings.capacityMicroliters?.toWireAmount()
                        )
                    )
                )
            },
            channel = DeviceDosingV1SavedMutationResult::channel,
            onAccepted = {
                adapter.stateAccess.setLowLevelAlertIntent(
                    deviceUid,
                    slotId,
                    settings.lowLevelAlertEnabled
                )
            }
        )
    ).toChannelResult()

    override suspend fun setReservoirLowLevelAlertEnabled(
        deviceUid: String,
        slotId: String,
        enabled: Boolean
    ): DeviceDosingChannelOperationResult {
        adapter.stateAccess.setLowLevelAlertIntent(deviceUid, slotId, enabled)
        return adapter.stateAccess.currentChannel(deviceUid, slotId)
            ?.let { snapshot -> DeviceDosingChannelOperationResult.Success(snapshot) }
            ?: DeviceDosingChannelOperationResult.Unavailable
    }

    override suspend fun refillReservoir(
        deviceUid: String,
        slotId: String
    ): DeviceDosingChannelOperationResult = adapter.mutationCoordinator.mutateRuntime(
        deviceUid = deviceUid,
        slotId = slotId,
        execute = { uid, channelKey, _, baseline ->
            requireMutation(
                baseline.controls.refillSupported && baseline.reservoir.trackingEnabled,
                DeviceDosingChannelRejection.NOT_EDITABLE
            )
            repositoryReservoirRefill(uid, channelKey)
        },
        channel = DeviceDosingV1ReservoirRefillResult::channel
    ).toChannelResult()

    override suspend fun doseNow(
        deviceUid: String,
        slotId: String,
        amountMicroliters: Long
    ): DeviceDosingChannelOperationResult = adapter.mutationCoordinator.mutateRuntime(
        deviceUid = deviceUid,
        slotId = slotId,
        execute = { uid, channelKey, _, baseline ->
            requireMutation(
                baseline.controls.manualDoseSupported,
                DeviceDosingChannelRejection.NOT_EDITABLE
            )
            requireMutation(baseline.calibrated, DeviceDosingChannelRejection.NOT_CALIBRATED)
            requireMutation(!baseline.activeRun.active, DeviceDosingChannelRejection.BUSY)
            requireMutation(
                baseline.scheduling.acceptsManualDose(amountMicroliters),
                DeviceDosingChannelRejection.INVALID_DRAFT
            )
            repositoryDoseNow(
                uid,
                DeviceDosingV1DoseNowRequest(
                    channelKey = channelKey,
                    amount = amountMicroliters.toWireAmount()
                )
            )
        },
        channel = DeviceDosingV1DoseNowResult::channel
    ).toChannelResult()

    override suspend fun doseStop(
        deviceUid: String,
        slotId: String
    ): DeviceDosingChannelOperationResult = adapter.mutationCoordinator.mutateRuntime(
        deviceUid = deviceUid,
        slotId = slotId,
        execute = { uid, channelKey, _, baseline ->
            requireMutation(
                baseline.controls.stopDoseSupported,
                DeviceDosingChannelRejection.NOT_EDITABLE
            )
            repositoryDoseStop(uid, channelKey)
        },
        channel = DeviceDosingV1SimpleStopResult::channel
    ).toChannelResult()

    override suspend fun reset(
        deviceUid: String,
        slotId: String
    ): DeviceDosingChannelOperationResult = adapter.mutationCoordinator.mutatePersisted(
        deviceUid = deviceUid,
        slotId = slotId,
        mutation = DeviceDosingV1PersistedMutation(
            execute = { uid, channelKey, revision, baseline ->
                requireMutation(
                    baseline.controls.resetSupported,
                    DeviceDosingChannelRejection.NOT_EDITABLE
                )
                repositoryChannelReset(
                    uid,
                    DeviceDosingV1ChannelResetRequest(channelKey, revision)
                )
            },
            channel = DeviceDosingV1SavedMutationResult::channel
        )
    ).toChannelResult()

    private suspend fun repositoryProgramApply(
        uid: com.aqua.aqualight.data.devices.model.DeviceUid,
        channelKey: DeviceDosingV1ChannelKey,
        revision: Long,
        program: DeviceDosingProgram
    ): DeviceRuntimeCommandOutcome<DeviceDosingV1SavedMutationResult> = adapter.repository.applyProgram(
        uid,
        DeviceDosingV1ProgramApplyRequest(
            channelKey = channelKey,
            expectedRevision = revision,
            program = DeviceDosingV1ProgramSnapshotMapper.toWireProgram(program)
        )
    )

    private suspend fun repositoryConfigApply(
        uid: com.aqua.aqualight.data.devices.model.DeviceUid,
        request: DeviceDosingV1ConfigApplyRequest
    ) = adapter.repository.applyConfig(uid, request)

    private suspend fun repositoryReservoirRefill(
        uid: com.aqua.aqualight.data.devices.model.DeviceUid,
        channelKey: DeviceDosingV1ChannelKey
    ) = adapter.repository.refillReservoir(uid, channelKey)

    private suspend fun repositoryDoseNow(
        uid: com.aqua.aqualight.data.devices.model.DeviceUid,
        request: DeviceDosingV1DoseNowRequest
    ) = adapter.repository.doseNow(uid, request)

    private suspend fun repositoryDoseStop(
        uid: com.aqua.aqualight.data.devices.model.DeviceUid,
        channelKey: DeviceDosingV1ChannelKey
    ) = adapter.repository.stopDose(uid, channelKey)

    private suspend fun repositoryChannelReset(
        uid: com.aqua.aqualight.data.devices.model.DeviceUid,
        request: DeviceDosingV1ChannelResetRequest
    ) = adapter.repository.resetChannel(uid, request)
}

private fun DeviceDosingV1RefreshResult.toChannelResult(): DeviceDosingChannelOperationResult =
    when (this) {
        is DeviceDosingV1RefreshResult.Success ->
            DeviceDosingChannelOperationResult.Success(state.channel)
        is DeviceDosingV1RefreshResult.Failed -> DeviceDosingChannelFailureMapper.map(outcome)
        DeviceDosingV1RefreshResult.Malformed,
        DeviceDosingV1RefreshResult.RejectedStale -> DeviceDosingChannelOperationResult.Failed
    }

private fun DeviceDosingV1MutationResult<*>.toChannelResult(): DeviceDosingChannelOperationResult =
    when (this) {
        is DeviceDosingV1MutationResult.Success -> DeviceDosingChannelOperationResult.Success(state.channel)
        is DeviceDosingV1MutationResult.Reconciled ->
            DeviceDosingChannelOperationResult.Success(state.channel)
        is DeviceDosingV1MutationResult.Committed -> DeviceDosingChannelCommittedResult(revision)
        is DeviceDosingV1MutationResult.Failed -> DeviceDosingChannelFailureMapper.map(outcome)
        is DeviceDosingV1MutationResult.LocallyRejected ->
            DeviceDosingChannelOperationResult.Rejected(reason)
        DeviceDosingV1MutationResult.Conflict -> DeviceDosingChannelOperationResult.Rejected(
            DeviceDosingChannelRejection.CONFLICT
        )
        DeviceDosingV1MutationResult.Malformed,
        DeviceDosingV1MutationResult.RejectedStale -> DeviceDosingChannelOperationResult.Failed
    }

private fun requireMutation(condition: Boolean, reason: DeviceDosingChannelRejection) {
    if (!condition) reject(reason)
}

private fun reject(reason: DeviceDosingChannelRejection): Nothing =
    throw LocalDosingMutationRejection(reason)

private fun Long.toWireAmount(): DeviceDosingV1Amount =
    DeviceDosingV1Amount.fromMilliliters(
        toDouble() / DeviceDosingV1Contract.Limit.AMOUNT_QUANTA_PER_ML
    )

private fun DeviceDosingProgram?.hasSamePlanAssignment(
    desired: DeviceDosingProgram
): Boolean = this?.copy(missedDoseRecoveryEnabled = false) ==
    desired.copy(missedDoseRecoveryEnabled = false)
