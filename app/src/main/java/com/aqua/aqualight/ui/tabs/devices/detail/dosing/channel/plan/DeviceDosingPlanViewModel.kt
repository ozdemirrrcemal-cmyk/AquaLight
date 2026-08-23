package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelCommittedResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperationResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelRejection
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingMutationReconciliation
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgram
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgramSchedule
import com.aqua.aqualight.application.devices.dosing.DeviceDosingSchedulingPolicy
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.model.DosingWeekday
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

internal data class DeviceDosingPlanEditorState(
    val draft: DosingPlanDraft = DosingPlanDraft(),
    val scheduling: DeviceDosingSchedulingPolicy = DeviceDosingSchedulingPolicy(),
    val supportedModes: Set<DosingPlanScheduleMode> = emptySet(),
    val missedDoseRecoveryEnabled: Boolean = false,
    val editable: Boolean = false,
    val operationInProgress: Boolean = false,
    val initialized: Boolean = false,
    val baseRevision: Long? = null,
    val baseProgram: DeviceDosingProgram? = null,
    val baseProgramKnown: Boolean = false,
    val draftDirty: Boolean = false
) {
    val programIntent: DeviceDosingProgram
        get() = draft.toApplicationProgram(missedDoseRecoveryEnabled)

    /**
     * An owner-scoped central latest-intent queue accepts a newer Save while an older one is still
     * synchronizing. operationInProgress is therefore presentation state, never input backpressure.
     */
    val canSave: Boolean
        get() = editable && baseRevision != null && baseProgramKnown
}

internal enum class DosingPlanValidationIssue {
    DOSE_LIMIT,
    EVENT_LIMIT,
    NO_DAYS,
    UNSUPPORTED_MODE,
    RECOVERY_UNSUPPORTED,
    INVALID_SCHEDULE
}

internal sealed interface DeviceDosingPlanEvent {
    data object Saved : DeviceDosingPlanEvent
    data class InvalidDraft(val issue: DosingPlanValidationIssue) : DeviceDosingPlanEvent
    data class SaveRejected(val reason: DeviceDosingChannelRejection) : DeviceDosingPlanEvent
    data object SaveUnavailable : DeviceDosingPlanEvent
    data object SaveFailed : DeviceDosingPlanEvent
}

/** Firmware-independent plan editor backed by the central channel application boundary. */
internal class DeviceDosingPlanViewModel(
    private val operations: DeviceDosingChannelOperations
) : ViewModel() {
    private val mutableEditorState = MutableStateFlow(DeviceDosingPlanEditorState())
    val editorState: StateFlow<DeviceDosingPlanEditorState> = mutableEditorState.asStateFlow()

    private val eventChannel = Channel<DeviceDosingPlanEvent>(Channel.BUFFERED)
    val events: Flow<DeviceDosingPlanEvent> = eventChannel.receiveAsFlow()

    private var boundChannel: DosingPlanChannelBinding? = null
    private var restoreState = DosingPlanRestoreState()
    private val snapshotBinding = DosingPlanSnapshotBinding(operations, viewModelScope)
    private var saveJob: Job? = null

    val currentEditorState: DeviceDosingPlanEditorState
        get() = mutableEditorState.value

    fun bind(
        deviceUidText: String,
        slotIdText: String,
        restoredState: DosingPlanRestoreState = DosingPlanRestoreState()
    ) {
        val binding = DosingPlanChannelBinding.from(deviceUidText, slotIdText)
        if (binding == null) {
            boundChannel = null
            snapshotBinding.clear()
            saveJob?.cancel()
            restoreState = DosingPlanRestoreState()
            mutableEditorState.value = DeviceDosingPlanEditorState()
            return
        }
        if (boundChannel == binding) return

        saveJob?.cancel()
        boundChannel = binding
        restoreState = restoredState
        mutableEditorState.value = DeviceDosingPlanEditorState()
        snapshotBinding.replace(binding) { snapshot ->
            if (boundChannel == binding) {
                mutableEditorState.value = reduceDosingPlanSnapshot(
                    current = mutableEditorState.value,
                    snapshot = snapshot,
                    restoredState = restoreState
                )
            }
        }
    }

    fun setDailyDoseMicroliters(microliters: Long) {
        if (microliters <= 0L) return
        updateDraft { state -> state.copy(distributedDailyDoseMicroliters = microliters) }
        emitConstraintWarningIfNeeded(mutableEditorState.value, eventChannel)
    }

    fun applyScheduleUpdate(scheduleUpdate: DosingPlanScheduleUpdate) {
        updateDraft { state ->
            when (scheduleUpdate) {
                is DosingPlanScheduleUpdate.Single -> state.copy(
                    singleDoseStartTimeMs = scheduleUpdate.startTimeMs,
                    selectedScheduleMode = DosingPlanScheduleMode.SINGLE
                )
                is DosingPlanScheduleUpdate.Hourly -> state.copy(
                    hourlyStartTimeMs = scheduleUpdate.startTimeMs,
                    selectedScheduleMode = DosingPlanScheduleMode.HOURLY
                )
                is DosingPlanScheduleUpdate.Custom -> state.copy(
                    customPeriods = scheduleUpdate.periods,
                    selectedScheduleMode = DosingPlanScheduleMode.CUSTOM
                )
                is DosingPlanScheduleUpdate.Timer -> state.copy(
                    timerDoses = scheduleUpdate.doses,
                    selectedScheduleMode = DosingPlanScheduleMode.TIMER
                )
            }
        }
        emitConstraintWarningIfNeeded(mutableEditorState.value, eventChannel)
    }

    fun setScheduleEnabled(enabled: Boolean) = updateDraft { state ->
        state.copy(scheduleEnabled = enabled)
    }

    fun selectEveryDay() = updateDraft { state ->
        state.copy(recurrenceState = state.recurrenceState.selectEveryDay())
    }

    fun setWeekdaySelected(weekday: DosingWeekday, selected: Boolean) = updateDraft { state ->
        state.copy(recurrenceState = state.recurrenceState.withDaySelection(weekday, selected))
    }

    fun save() {
        val currentBinding = boundChannel
        val state = mutableEditorState.value
        val program = state.programIntent
        val baseRevision = state.baseRevision
        val canStartSave = listOf(
            currentBinding != null,
            state.editable,
            baseRevision != null,
            state.baseProgramKnown
        ).all { valid -> valid }
        if (!canStartSave) return

        if (!program.isValidFor(state.scheduling)) {
            eventChannel.trySend(
                DeviceDosingPlanEvent.InvalidDraft(
                    planValidationIssue(program, state.scheduling)
                )
            )
            return
        }

        val binding = checkNotNull(currentBinding)
        mutableEditorState.value = state.copy(operationInProgress = true)
        // Cancels only this screen's obsolete waiter. The accepted data-layer intent is owner-scoped
        // and continues; the replacement Save becomes the latest target of the central queue.
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            val reconciliation = try {
                operations.reconcilePlanSave(
                    DeviceDosingPlanSaveRequest(
                        deviceUid = binding.deviceUid,
                        slotId = binding.slotId,
                        program = program,
                        baseRevision = checkNotNull(baseRevision),
                        baseProgram = state.baseProgram
                    )
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                DeviceDosingMutationReconciliation(DeviceDosingChannelOperationResult.Failed)
            }
            if (boundChannel != binding) return@launch
            handleSaveResult(binding, program, reconciliation)
        }
    }

    private inline fun updateDraft(transform: (DosingPlanDraft) -> DosingPlanDraft) {
        val current = mutableEditorState.value
        if (!current.editable) return
        val updated = transform(current.draft)
        if (updated == current.draft) return
        mutableEditorState.value = current.copy(
            draft = updated,
            draftDirty = true
        )
    }

    private suspend fun handleSaveResult(
        binding: DosingPlanChannelBinding,
        program: DeviceDosingProgram,
        reconciliation: DeviceDosingMutationReconciliation
    ) {
        when (val result = reconciliation.result) {
            is DeviceDosingChannelOperationResult.Success -> {
                val savedState = mutableEditorState.value.copy(
                    draftDirty = false,
                    baseRevision = result.snapshot.revision
                )
                mutableEditorState.value = reduceDosingPlanSnapshot(
                    current = savedState,
                    snapshot = result.snapshot,
                    restoredState = restoreState
                ).copy(operationInProgress = false)
                eventChannel.send(DeviceDosingPlanEvent.Saved)
            }
            is DeviceDosingChannelCommittedResult -> {
                // Firmware has durably committed the exact latest persisted intent. Readback remains
                // centralized and fail-closed, so acknowledge the save without inventing a snapshot.
                mutableEditorState.value = mutableEditorState.value.copy(
                    operationInProgress = false,
                    draftDirty = false,
                    baseRevision = result.revision,
                    baseProgram = program,
                    baseProgramKnown = true
                )
                eventChannel.send(DeviceDosingPlanEvent.Saved)
            }
            is DeviceDosingChannelOperationResult.Rejected -> {
                handleRejectedSave(binding, result, reconciliation.authoritativeSnapshot)
            }
            DeviceDosingChannelOperationResult.Unavailable -> {
                mutableEditorState.value = mutableEditorState.value.copy(
                    operationInProgress = false
                )
                eventChannel.send(DeviceDosingPlanEvent.SaveUnavailable)
            }
            DeviceDosingChannelOperationResult.Failed -> {
                mutableEditorState.value = mutableEditorState.value.copy(
                    operationInProgress = false
                )
                eventChannel.send(DeviceDosingPlanEvent.SaveFailed)
            }
        }
    }

    private suspend fun handleRejectedSave(
        binding: DosingPlanChannelBinding,
        rejection: DeviceDosingChannelOperationResult.Rejected,
        authoritativeSnapshot: DeviceDosingChannelSnapshot?
    ) {
        if (rejection.reason == DeviceDosingChannelRejection.CONFLICT) {
            val refreshed = authoritativeSnapshot
                ?: refreshConflictRevision(operations, binding.deviceUid, binding.slotId)
            if (refreshed != null && boundChannel == binding) {
                mutableEditorState.value = reduceDosingPlanSnapshot(
                    current = mutableEditorState.value.copy(draftDirty = false),
                    snapshot = refreshed,
                    restoredState = restoreState
                )
            }
        }
        mutableEditorState.value = mutableEditorState.value.copy(operationInProgress = false)
        eventChannel.send(DeviceDosingPlanEvent.SaveRejected(rejection.reason))
    }
}

private fun emitConstraintWarningIfNeeded(
    state: DeviceDosingPlanEditorState,
    eventChannel: Channel<DeviceDosingPlanEvent>
) {
    if (!state.initialized) return
    val program = state.programIntent
    if (program.isValidFor(state.scheduling)) return
    val issue = planValidationIssue(program, state.scheduling)
    if (issue == DosingPlanValidationIssue.DOSE_LIMIT ||
        issue == DosingPlanValidationIssue.EVENT_LIMIT
    ) {
        eventChannel.trySend(DeviceDosingPlanEvent.InvalidDraft(issue))
    }
}

private suspend fun refreshConflictRevision(
    operations: DeviceDosingChannelOperations,
    deviceUid: String,
    slotId: String
): DeviceDosingChannelSnapshot? = try {
    (operations.refresh(deviceUid, slotId) as? DeviceDosingChannelOperationResult.Success)?.snapshot
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (_: Exception) {
    null
}

private fun planValidationIssue(
    program: DeviceDosingProgram,
    policy: DeviceDosingSchedulingPolicy
): DosingPlanValidationIssue = when {
    program.schedule.mode !in policy.supportedModes ->
        DosingPlanValidationIssue.UNSUPPORTED_MODE
    program.enabled && policy.supportsWeekdayRecurrence && program.weekdays.none { it } ->
        DosingPlanValidationIssue.NO_DAYS
    program.missedDoseRecoveryEnabled && !policy.supportsMissedDoseRecovery ->
        DosingPlanValidationIssue.RECOVERY_UNSUPPORTED
    else -> scheduleValidationIssue(program.schedule, policy)
}

private fun scheduleValidationIssue(
    schedule: DeviceDosingProgramSchedule,
    policy: DeviceDosingSchedulingPolicy
): DosingPlanValidationIssue = when (schedule) {
    is DeviceDosingProgramSchedule.Single -> when {
        !policy.acceptsScheduledDose(schedule.dailyDoseMicroliters) ->
            DosingPlanValidationIssue.DOSE_LIMIT
        else -> DosingPlanValidationIssue.INVALID_SCHEDULE
    }
    is DeviceDosingProgramSchedule.Hourly24 -> when {
        policy.maxEventsPerChannel < HOURLY_DOSE_COUNT ->
            DosingPlanValidationIssue.EVENT_LIMIT
        !distributedAmountsFit(schedule.dailyDoseMicroliters, HOURLY_DOSE_COUNT, policy) ->
            DosingPlanValidationIssue.DOSE_LIMIT
        else -> DosingPlanValidationIssue.INVALID_SCHEDULE
    }
    is DeviceDosingProgramSchedule.CustomPeriods -> customScheduleValidationIssue(schedule, policy)
    is DeviceDosingProgramSchedule.Timer -> when {
        schedule.doses.size > policy.maxEventsPerChannel ->
            DosingPlanValidationIssue.EVENT_LIMIT
        schedule.doses.any { dose -> !policy.acceptsScheduledDose(dose.amountMicroliters) } ->
            DosingPlanValidationIssue.DOSE_LIMIT
        else -> DosingPlanValidationIssue.INVALID_SCHEDULE
    }
}

private fun customScheduleValidationIssue(
    schedule: DeviceDosingProgramSchedule.CustomPeriods,
    policy: DeviceDosingSchedulingPolicy
): DosingPlanValidationIssue {
    val eventCount = schedule.periods.sumOf { period -> period.doseCount }
    return when {
        schedule.periods.size > policy.maxCustomPeriodsPerChannel ||
            eventCount > policy.maxEventsPerChannel -> DosingPlanValidationIssue.EVENT_LIMIT
        eventCount > 0 &&
            !distributedAmountsFit(schedule.dailyDoseMicroliters, eventCount, policy) ->
            DosingPlanValidationIssue.DOSE_LIMIT
        else -> DosingPlanValidationIssue.INVALID_SCHEDULE
    }
}

private fun distributedAmountsFit(
    totalMicroliters: Long,
    count: Int,
    policy: DeviceDosingSchedulingPolicy
): Boolean {
    if (!policy.acceptsAmount(totalMicroliters) || count <= 0) return false
    val totalQuanta = totalMicroliters / policy.amountResolutionMicroliters
    val baseQuanta = totalQuanta / count
    val remainderQuanta = (totalQuanta % count).toInt()
    return (0 until count).all { index ->
        val quanta = baseQuanta + if (index < remainderQuanta) 1L else 0L
        policy.acceptsScheduledDose(quanta * policy.amountResolutionMicroliters)
    }
}

private const val HOURLY_DOSE_COUNT = 24
