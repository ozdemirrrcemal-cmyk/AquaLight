package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperationResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgram
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgramMode
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgramSchedule
import com.aqua.aqualight.application.devices.dosing.DeviceDosingSchedulingPolicy
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.DosingWeekday
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
    val editable: Boolean = false,
    val operationInProgress: Boolean = false,
    val initialized: Boolean = false
) {
    val canSave: Boolean
        get() = editable && !operationInProgress &&
            draft.toApplicationProgram(missedDoseRecoveryEnabled = false)
                .isValidFor(scheduling)
}

internal sealed interface DeviceDosingPlanEvent {
    data object Saved : DeviceDosingPlanEvent
    data object SaveFailed : DeviceDosingPlanEvent
}

/** Firmware-independent plan editor backed by the channel application boundary. */
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
    private var latestSnapshot: DeviceDosingChannelSnapshot? = null
    private var observeJob: Job? = null
    private var refreshJob: Job? = null
    private var saveJob: Job? = null

    fun bind(
        deviceUidText: String,
        slotIdText: String,
        restoredDraft: DosingPlanDraft?
    ) {
        val deviceUid = deviceUidText.trim()
        val slotId = slotIdText.trim()
        if (deviceUid.isBlank() || slotId.isBlank()) {
            clearBinding()
            return
        }
        if (boundDeviceUid == deviceUid && boundSlotId == slotId) return

        observeJob?.cancel()
        refreshJob?.cancel()
        saveJob?.cancel()
        boundDeviceUid = deviceUid
        boundSlotId = slotId
        this.restoredDraft = restoredDraft
        latestSnapshot = null
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

    fun currentDraft(): DosingPlanDraft = mutableEditorState.value.draft

    fun currentEditorState(): DeviceDosingPlanEditorState = mutableEditorState.value

    fun setDailyDoseMicroliters(microliters: Long) {
        if (microliters <= 0L) return
        updateDraft { state -> state.copy(distributedDailyDoseMicroliters = microliters) }
    }

    fun applyScheduleUpdate(scheduleUpdate: DosingPlanScheduleUpdate) = updateDraft { state ->
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
        val missedDoseRecoveryEnabled = latestSnapshot
            ?.program
            ?.missedDoseRecoveryEnabled
            ?: false
        val program = state.draft.toApplicationProgram(missedDoseRecoveryEnabled)
        if (
            deviceUid.isBlank() ||
            slotId.isBlank() ||
            !state.editable ||
            state.operationInProgress ||
            !program.isValidFor(state.scheduling)
        ) {
            return
        }

        mutableEditorState.value = state.copy(operationInProgress = true)
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            val result = runCatching { operations.applyProgram(deviceUid, slotId, program) }
                .getOrElse { DeviceDosingChannelOperationResult.Failed }
            if (boundDeviceUid != deviceUid || boundSlotId != slotId) return@launch
            when (result) {
                is DeviceDosingChannelOperationResult.Success -> {
                    latestSnapshot = result.snapshot
                    mutableEditorState.value = mutableEditorState.value.copy(
                        operationInProgress = false
                    )
                    eventChannel.send(DeviceDosingPlanEvent.Saved)
                }
                else -> {
                    mutableEditorState.value = mutableEditorState.value.copy(
                        operationInProgress = false
                    )
                    eventChannel.send(DeviceDosingPlanEvent.SaveFailed)
                }
            }
        }
    }

    private fun applySnapshot(snapshot: DeviceDosingChannelSnapshot) {
        latestSnapshot = snapshot
        val current = mutableEditorState.value
        val initialDraft = if (current.initialized) {
            current.draft
        } else {
            restoredDraft ?: snapshot.program?.toPlanDraft() ?: defaultDraft()
        }
        mutableEditorState.value = current.copy(
            draft = initialDraft,
            scheduling = snapshot.scheduling,
            supportedModes = snapshot.scheduling.supportedModes.mapTo(linkedSetOf()) { mode ->
                mode.toPlanMode()
            },
            editable = snapshot.calibrated && snapshot.controls.programEditable,
            initialized = true
        )
    }

    private inline fun updateDraft(transform: (DosingPlanDraft) -> DosingPlanDraft) {
        val current = mutableEditorState.value
        if (!current.editable || current.operationInProgress) return
        mutableEditorState.value = current.copy(draft = transform(current.draft))
    }

    private fun clearBinding() {
        observeJob?.cancel()
        refreshJob?.cancel()
        saveJob?.cancel()
        boundDeviceUid = ""
        boundSlotId = ""
        restoredDraft = null
        latestSnapshot = null
        mutableEditorState.value = DeviceDosingPlanEditorState()
    }
}

private fun DosingPlanDraft.toApplicationProgram(
    missedDoseRecoveryEnabled: Boolean
): DeviceDosingProgram = DeviceDosingProgram(
    enabled = scheduleEnabled,
    weekdays = DOSING_PLAN_WEEKDAYS.map { weekday ->
        weekday in recurrenceState.selectedDays
    },
    schedule = when (selectedScheduleMode) {
        DosingPlanScheduleMode.SINGLE -> DeviceDosingProgramSchedule.Single(
            dailyDoseMicroliters = distributedDailyDoseMicroliters,
            startTimeMillis = singleDoseStartTimeMs
        )
        DosingPlanScheduleMode.HOURLY -> DeviceDosingProgramSchedule.Hourly24(
            dailyDoseMicroliters = distributedDailyDoseMicroliters,
            startTimeMillis = hourlyStartTimeMs
        )
        DosingPlanScheduleMode.CUSTOM -> DeviceDosingProgramSchedule.CustomPeriods(
            dailyDoseMicroliters = distributedDailyDoseMicroliters,
            periods = customPeriods
        )
        DosingPlanScheduleMode.TIMER -> DeviceDosingProgramSchedule.Timer(timerDoses)
    },
    missedDoseRecoveryEnabled = missedDoseRecoveryEnabled
)

private fun DeviceDosingProgram.toPlanDraft(): DosingPlanDraft {
    val recurrence = DosingPlanRecurrenceState.fromWeekdayFlags(weekdays.toBooleanArray())
        ?: DosingPlanRecurrenceState()
    return when (val value = schedule) {
        is DeviceDosingProgramSchedule.Single -> DosingPlanDraft(
            distributedDailyDoseMicroliters = value.dailyDoseMicroliters,
            singleDoseStartTimeMs = value.startTimeMillis,
            selectedScheduleMode = DosingPlanScheduleMode.SINGLE,
            scheduleEnabled = enabled,
            recurrenceState = recurrence
        )
        is DeviceDosingProgramSchedule.Hourly24 -> DosingPlanDraft(
            distributedDailyDoseMicroliters = value.dailyDoseMicroliters,
            hourlyStartTimeMs = value.startTimeMillis,
            selectedScheduleMode = DosingPlanScheduleMode.HOURLY,
            scheduleEnabled = enabled,
            recurrenceState = recurrence
        )
        is DeviceDosingProgramSchedule.CustomPeriods -> DosingPlanDraft(
            distributedDailyDoseMicroliters = value.dailyDoseMicroliters,
            customPeriods = value.periods,
            selectedScheduleMode = DosingPlanScheduleMode.CUSTOM,
            scheduleEnabled = enabled,
            recurrenceState = recurrence
        )
        is DeviceDosingProgramSchedule.Timer -> DosingPlanDraft(
            timerDoses = value.doses,
            selectedScheduleMode = DosingPlanScheduleMode.TIMER,
            scheduleEnabled = enabled,
            recurrenceState = recurrence
        )
    }
}

private fun DeviceDosingProgramMode.toPlanMode(): DosingPlanScheduleMode = when (this) {
    DeviceDosingProgramMode.SINGLE -> DosingPlanScheduleMode.SINGLE
    DeviceDosingProgramMode.HOURLY_24 -> DosingPlanScheduleMode.HOURLY
    DeviceDosingProgramMode.CUSTOM_PERIODS -> DosingPlanScheduleMode.CUSTOM
    DeviceDosingProgramMode.TIMER -> DosingPlanScheduleMode.TIMER
}

private fun defaultDraft() = DosingPlanDraft(
    distributedDailyDoseMicroliters = DEFAULT_DAILY_DOSE_MICROLITERS,
    singleDoseStartTimeMs = DEFAULT_START_TIME_MILLIS
)

private const val DEFAULT_DAILY_DOSE_MICROLITERS = 3_000L
private const val DEFAULT_START_TIME_MILLIS = 8 * 60 * 60 * 1_000L
