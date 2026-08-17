package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperationResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelRejection
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgram
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgramSchedule
import com.aqua.aqualight.application.devices.dosing.DeviceDosingSchedulingPolicy
import com.aqua.aqualight.application.devices.dosing.applyProgramAtRevision
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.model.DosingWeekday
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
    val draftDirty: Boolean = false
) {
    val programIntent: DeviceDosingProgram
        get() = draft.toApplicationProgram(missedDoseRecoveryEnabled)

    /** Save stays actionable while editable so validation feedback can use the central snackbar. */
    val canSave: Boolean
        get() = editable && !operationInProgress
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

    private var boundDeviceUid: String = ""
    private var boundSlotId: String = ""
    private var restoredDraft: DosingPlanDraft? = null
    private var restoredBaseRevision: Long? = null
    private var restoredDraftDirty: Boolean = false
    private var observeJob: Job? = null
    private var refreshJob: Job? = null
    private var saveJob: Job? = null

    fun currentEditorState(): DeviceDosingPlanEditorState = mutableEditorState.value

    fun bind(
        deviceUidText: String,
        slotIdText: String,
        restoredDraft: DosingPlanDraft?,
        restoredBaseRevision: Long? = null,
        restoredDraftDirty: Boolean = false
    ) {
        val deviceUid = deviceUidText.trim()
        val slotId = slotIdText.trim()
        if (deviceUid.isBlank() || slotId.isBlank()) {
            observeJob?.cancel()
            refreshJob?.cancel()
            saveJob?.cancel()
            boundDeviceUid = ""
            boundSlotId = ""
            this.restoredDraft = null
            this.restoredBaseRevision = null
            this.restoredDraftDirty = false
            mutableEditorState.value = DeviceDosingPlanEditorState()
            return
        }
        if (boundDeviceUid == deviceUid && boundSlotId == slotId) return

        observeJob?.cancel()
        refreshJob?.cancel()
        saveJob?.cancel()
        boundDeviceUid = deviceUid
        boundSlotId = slotId
        this.restoredDraft = restoredDraft
        this.restoredBaseRevision = restoredBaseRevision
        this.restoredDraftDirty = restoredDraftDirty
        mutableEditorState.value = DeviceDosingPlanEditorState()
        observeJob = viewModelScope.launch {
            operations.observe(deviceUid, slotId).collect { snapshot ->
                if (boundDeviceUid != deviceUid || boundSlotId != slotId) return@collect
                snapshot?.let(::applySnapshot)
            }
        }
        refreshJob = viewModelScope.launch {
            when (val result = operations.refresh(deviceUid, slotId)) {
                is DeviceDosingChannelOperationResult.Success -> applySnapshot(result.snapshot)
                else -> Unit
            }
        }
    }

    fun setDailyDoseMicroliters(microliters: Long) {
        if (microliters <= 0L) return
        updateDraft { state -> state.copy(distributedDailyDoseMicroliters = microliters) }
        emitConstraintWarningIfNeeded()
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
        emitConstraintWarningIfNeeded()
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
        val deviceUid = boundDeviceUid
        val slotId = boundSlotId
        val state = mutableEditorState.value
        val program = state.programIntent
        val baseRevision = state.baseRevision
        val canStartSave = listOf(
            deviceUid.isNotBlank(),
            slotId.isNotBlank(),
            state.editable,
            !state.operationInProgress,
            baseRevision != null
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

        mutableEditorState.value = state.copy(operationInProgress = true)
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            val result = runCatching {
                operations.applyProgramAtRevision(
                    deviceUid = deviceUid,
                    slotId = slotId,
                    program = program,
                    expectedRevision = checkNotNull(baseRevision)
                )
            }.getOrElse { DeviceDosingChannelOperationResult.Failed }
            if (boundDeviceUid != deviceUid || boundSlotId != slotId) return@launch
            when (result) {
                is DeviceDosingChannelOperationResult.Success -> {
                    mutableEditorState.value = mutableEditorState.value.copy(
                        draftDirty = false,
                        baseRevision = result.snapshot.revision
                    )
                    applySnapshot(result.snapshot)
                    mutableEditorState.value = mutableEditorState.value.copy(
                        operationInProgress = false
                    )
                    eventChannel.send(DeviceDosingPlanEvent.Saved)
                }
                is DeviceDosingChannelOperationResult.Rejected -> {
                    if (result.reason == DeviceDosingChannelRejection.CONFLICT) {
                        reconcileConflictRevision(deviceUid, slotId)
                    }
                    mutableEditorState.value = mutableEditorState.value.copy(
                        operationInProgress = false
                    )
                    eventChannel.send(DeviceDosingPlanEvent.SaveRejected(result.reason))
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
    }

    private suspend fun reconcileConflictRevision(deviceUid: String, slotId: String) {
        val refreshed = runCatching { operations.refresh(deviceUid, slotId) }.getOrNull()
        if (refreshed !is DeviceDosingChannelOperationResult.Success) return
        if (boundDeviceUid != deviceUid || boundSlotId != slotId) return
        applySnapshot(refreshed.snapshot)
        mutableEditorState.value = mutableEditorState.value.copy(
            baseRevision = refreshed.snapshot.revision
        )
    }

    private fun applySnapshot(snapshot: DeviceDosingChannelSnapshot) {
        val current = mutableEditorState.value
        val firstSnapshot = !current.initialized
        val keepDraft = current.initialized && current.draftDirty
        val nextDraft = when {
            firstSnapshot -> restoredDraft ?: snapshot.program?.toPlanDraft() ?: defaultDraft()
            keepDraft -> current.draft
            else -> snapshot.program?.toPlanDraft() ?: defaultDraft()
        }
        val nextBaseRevision = when {
            firstSnapshot -> restoredBaseRevision ?: snapshot.revision
            keepDraft -> current.baseRevision
            else -> snapshot.revision
        }
        val nextDraftDirty = when {
            firstSnapshot -> restoredDraft != null && restoredDraftDirty
            keepDraft -> true
            else -> false
        }
        mutableEditorState.value = current.copy(
            draft = nextDraft,
            scheduling = snapshot.scheduling,
            supportedModes = snapshot.scheduling.supportedModes.mapTo(linkedSetOf()) { mode ->
                mode.toPlanMode()
            },
            missedDoseRecoveryEnabled = snapshot.program?.missedDoseRecoveryEnabled ?: false,
            editable = snapshot.calibrated && snapshot.controls.programEditable,
            initialized = true,
            baseRevision = nextBaseRevision,
            draftDirty = nextDraftDirty
        )
    }

    private inline fun updateDraft(transform: (DosingPlanDraft) -> DosingPlanDraft) {
        val current = mutableEditorState.value
        if (!current.editable || current.operationInProgress) return
        val updated = transform(current.draft)
        if (updated == current.draft) return
        mutableEditorState.value = current.copy(
            draft = updated,
            draftDirty = true
        )
    }

    private fun emitConstraintWarningIfNeeded() {
        val state = mutableEditorState.value
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
}

private fun planValidationIssue(
    program: DeviceDosingProgram,
    policy: DeviceDosingSchedulingPolicy
): DosingPlanValidationIssue {
    if (program.schedule.mode !in policy.supportedModes) {
        return DosingPlanValidationIssue.UNSUPPORTED_MODE
    }
    if (program.enabled && policy.supportsWeekdayRecurrence && program.weekdays.none { it }) {
        return DosingPlanValidationIssue.NO_DAYS
    }
    if (program.missedDoseRecoveryEnabled && !policy.supportsMissedDoseRecovery) {
        return DosingPlanValidationIssue.RECOVERY_UNSUPPORTED
    }
    return when (val schedule = program.schedule) {
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
        is DeviceDosingProgramSchedule.CustomPeriods -> {
            val eventCount = schedule.periods.sumOf { period -> period.doseCount }
            when {
                schedule.periods.size > policy.maxCustomPeriodsPerChannel ||
                    eventCount > policy.maxEventsPerChannel ->
                    DosingPlanValidationIssue.EVENT_LIMIT
                eventCount > 0 &&
                    !distributedAmountsFit(schedule.dailyDoseMicroliters, eventCount, policy) ->
                    DosingPlanValidationIssue.DOSE_LIMIT
                else -> DosingPlanValidationIssue.INVALID_SCHEDULE
            }
        }
        is DeviceDosingProgramSchedule.Timer -> when {
            schedule.doses.size > policy.maxEventsPerChannel ->
                DosingPlanValidationIssue.EVENT_LIMIT
            schedule.doses.any { dose -> !policy.acceptsScheduledDose(dose.amountMicroliters) } ->
                DosingPlanValidationIssue.DOSE_LIMIT
            else -> DosingPlanValidationIssue.INVALID_SCHEDULE
        }
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

private fun defaultDraft() = DosingPlanDraft(
    distributedDailyDoseMicroliters = DEFAULT_DAILY_DOSE_MICROLITERS,
    singleDoseStartTimeMs = DEFAULT_START_TIME_MILLIS
)

private const val DEFAULT_DAILY_DOSE_MICROLITERS = 3_000L
private const val DEFAULT_START_TIME_MILLIS = 8 * 60 * 60 * 1_000L
private const val HOURLY_DOSE_COUNT = 24
