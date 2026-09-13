package com.aqua.aqualight.ui.tabs.devices.detail.timer.presentation.program

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerChannelSnapshot
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerControlFailure
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerControlOperations
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerControlResult
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerScheduleDraft
import com.aqua.aqualight.application.devices.timer.control.DeviceTimerScheduleSnapshot
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class DeviceTimerProgramViewModel(
    private val operations: DeviceTimerControlOperations
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceTimerProgramUiState())
    val uiState: StateFlow<DeviceTimerProgramUiState> = _uiState.asStateFlow()
    private val eventChannel = Channel<DeviceTimerProgramEvent>(Channel.BUFFERED)
    val events: Flow<DeviceTimerProgramEvent> = eventChannel.receiveAsFlow()
    private var originalSchedules = emptyList<DeviceTimerProgramDraft>()

    fun bind(deviceUidText: String, slotIdText: String) {
        val deviceUid = deviceUidText.trim()
        val slotId = slotIdText.trim()
        if (deviceUid.isBlank() || slotId.isBlank()) {
            _uiState.value = DeviceTimerProgramUiState(
                authority = DeviceTimerProgramAuthorityUiState(
                    loadState = DeviceTimerProgramLoadState.FAILED
                ),
                editor = DeviceTimerProgramEditorUiState(
                    failure = DeviceTimerControlFailure.Unsupported
                )
            )
            return
        }
        if (_uiState.value.deviceUid == deviceUid && _uiState.value.slotId == slotId) return
        _uiState.value = DeviceTimerProgramUiState(
            context = DeviceTimerProgramContextUiState(deviceUid = deviceUid, slotId = slotId),
            authority = DeviceTimerProgramAuthorityUiState(
                loadState = DeviceTimerProgramLoadState.LOADING
            )
        )
        viewModelScope.launch {
            val result = runCatching { operations.refreshChannel(deviceUid, slotId) }
                .getOrElse { DeviceTimerControlResult.Failed(DeviceTimerControlFailure.Unavailable) }
            applyLoadResult(deviceUid, slotId, result)
        }
    }

    fun edit(action: DeviceTimerProgramEdit) = editSchedules { current ->
        action.apply(current)
    }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        _uiState.value = state.copy(editor = state.editor.copy(saving = true, failure = null))
        viewModelScope.launch {
            val drafts = state.schedules.map(DeviceTimerProgramDraft::toApplicationDraft)
            val result = runCatching {
                operations.replaceSchedules(
                    deviceUid = state.deviceUid,
                    slotId = state.slotId,
                    expectedRevision = state.expectedRevision,
                    schedules = drafts
                )
            }.getOrElse {
                DeviceTimerControlResult.Failed(DeviceTimerControlFailure.Unavailable)
            }
            when (result) {
                is DeviceTimerControlResult.Available -> {
                    originalSchedules = state.schedules
                    _uiState.value = _uiState.value.copy(
                        authority = _uiState.value.authority.copy(
                            expectedRevision = result.snapshot.revision
                        ),
                        editor = _uiState.value.editor.copy(
                            saving = false,
                            schedules = state.schedules,
                            dirty = false,
                            failure = null
                        )
                    )
                    eventChannel.send(DeviceTimerProgramEvent.Saved)
                }
                is DeviceTimerControlResult.Failed -> {
                    _uiState.value = _uiState.value.copy(
                        editor = _uiState.value.editor.copy(
                            saving = false,
                            failure = result.failure
                        )
                    )
                    eventChannel.send(DeviceTimerProgramEvent.Failed(result.failure))
                }
            }
        }
    }

    private fun applyLoadResult(
        deviceUid: String,
        slotId: String,
        result: DeviceTimerControlResult
    ) {
        if (_uiState.value.deviceUid != deviceUid || _uiState.value.slotId != slotId) return
        when (result) {
            is DeviceTimerControlResult.Available -> {
                val channel = result.snapshot.channels.singleOrNull { it.slotId == slotId }
                val schedules = channel?.schedules?.map(DeviceTimerScheduleSnapshot::toDraft)
                if (channel == null || schedules == null) {
                    applyLoadFailure(DeviceTimerControlFailure.InvalidData)
                } else {
                    originalSchedules = schedules
                    _uiState.value = DeviceTimerProgramUiState(
                        context = DeviceTimerProgramContextUiState(
                            deviceUid = deviceUid,
                            slotId = slotId,
                            channelTitle = channel.displayTitle()
                        ),
                        authority = DeviceTimerProgramAuthorityUiState(
                            loadState = DeviceTimerProgramLoadState.CONTENT,
                            maxSchedules = result.snapshot.maxSchedulesPerChannel,
                            expectedRevision = result.snapshot.revision
                        ),
                        editor = DeviceTimerProgramEditorUiState(
                            spansMidnightSupported =
                                result.snapshot.capabilities.supportsSpansMidnight,
                            editable = !result.snapshot.capabilities.readOnly &&
                                result.snapshot.capabilities.supportsConfigApply &&
                                result.snapshot.capabilities.supportsSchedules &&
                                !result.snapshot.lockLoop,
                            schedules = schedules
                        )
                    )
                }
            }
            is DeviceTimerControlResult.Failed -> applyLoadFailure(result.failure)
        }
    }

    private fun applyLoadFailure(failure: DeviceTimerControlFailure) {
        _uiState.value = _uiState.value.copy(
            authority = _uiState.value.authority.copy(
                loadState = DeviceTimerProgramLoadState.FAILED
            ),
            editor = _uiState.value.editor.copy(failure = failure)
        )
    }

    private fun editSchedules(
        transform: (List<DeviceTimerProgramDraft>) -> List<DeviceTimerProgramDraft>
    ) {
        val state = _uiState.value
        if (!state.editable || state.saving) return
        val schedules = transform(state.schedules).take(state.maxSchedules)
        _uiState.value = state.copy(
            editor = state.editor.copy(
                schedules = schedules,
                dirty = schedules != originalSchedules,
                failure = null
            )
        )
    }
}

data class DeviceTimerProgramUiState(
    val context: DeviceTimerProgramContextUiState = DeviceTimerProgramContextUiState(),
    val authority: DeviceTimerProgramAuthorityUiState = DeviceTimerProgramAuthorityUiState(),
    val editor: DeviceTimerProgramEditorUiState = DeviceTimerProgramEditorUiState()
) {
    val deviceUid: String get() = context.deviceUid
    val slotId: String get() = context.slotId
    val channelTitle: String get() = context.channelTitle
    val loadState: DeviceTimerProgramLoadState get() = authority.loadState
    val maxSchedules: Int get() = authority.maxSchedules
    val expectedRevision: Long get() = authority.expectedRevision
    val spansMidnightSupported: Boolean get() = editor.spansMidnightSupported
    val editable: Boolean get() = editor.editable
    val schedules: List<DeviceTimerProgramDraft> get() = editor.schedules
    val dirty: Boolean get() = editor.dirty
    val saving: Boolean get() = editor.saving
    val failure: DeviceTimerControlFailure? get() = editor.failure

    val validationIssue: DeviceTimerProgramValidationIssue?
        get() = schedules.timerProgramValidationIssue(maxSchedules, spansMidnightSupported)
    val valid: Boolean get() = validationIssue == null
    val canSave: Boolean
        get() = loadState == DeviceTimerProgramLoadState.CONTENT &&
            editable && dirty && valid && !saving
    val canAdd: Boolean
        get() = editable && !saving && schedules.size < maxSchedules && nextScheduleSlotId != null
    val nextScheduleSlotId: Int?
        get() = (MIN_SCHEDULE_SLOT_ID..MAX_SCHEDULE_SLOT_ID)
            .firstOrNull { candidate -> schedules.none { draft -> draft.slotId == candidate } }
}

data class DeviceTimerProgramContextUiState(
    val deviceUid: String = "",
    val slotId: String = "",
    val channelTitle: String = ""
)

data class DeviceTimerProgramAuthorityUiState(
    val loadState: DeviceTimerProgramLoadState = DeviceTimerProgramLoadState.IDLE,
    val maxSchedules: Int = 0,
    val expectedRevision: Long = 0L
)

data class DeviceTimerProgramEditorUiState(
    val spansMidnightSupported: Boolean = false,
    val editable: Boolean = false,
    val schedules: List<DeviceTimerProgramDraft> = emptyList(),
    val dirty: Boolean = false,
    val saving: Boolean = false,
    val failure: DeviceTimerControlFailure? = null
)

data class DeviceTimerProgramDraft(
    val slotId: Int,
    val enabled: Boolean,
    val name: String,
    val weekdays: List<Boolean>,
    val startMinutesOfDay: Int,
    val endMinutesOfDay: Int
) {
    val spansMidnight: Boolean get() = endMinutesOfDay < startMinutesOfDay
}

enum class DeviceTimerProgramLoadState { IDLE, LOADING, CONTENT, FAILED }

enum class DeviceTimerProgramValidationIssue {
    INVALID_FIELD,
    OVERLAPPING_PROGRAMS
}

sealed interface DeviceTimerProgramEvent {
    data object Saved : DeviceTimerProgramEvent
    data class Failed(val failure: DeviceTimerControlFailure) : DeviceTimerProgramEvent
}

sealed interface DeviceTimerProgramEdit {
    data class Add(val defaultName: String) : DeviceTimerProgramEdit
    data class Delete(val slotId: Int) : DeviceTimerProgramEdit
    data class UpdateName(val slotId: Int, val name: String) : DeviceTimerProgramEdit
    data class ToggleEnabled(val slotId: Int) : DeviceTimerProgramEdit
    data class ToggleWeekday(val slotId: Int, val weekdayIndex: Int) : DeviceTimerProgramEdit
    data class UpdateStartTime(val slotId: Int, val minutesOfDay: Int) : DeviceTimerProgramEdit
    data class UpdateEndTime(val slotId: Int, val minutesOfDay: Int) : DeviceTimerProgramEdit
}

private fun DeviceTimerProgramEdit.apply(
    schedules: List<DeviceTimerProgramDraft>
): List<DeviceTimerProgramDraft> = when (this) {
    is DeviceTimerProgramEdit.Add -> schedules.addTimerSchedule(defaultName)
    is DeviceTimerProgramEdit.Delete -> schedules.filterNot { draft -> draft.slotId == slotId }
    is DeviceTimerProgramEdit.UpdateName -> schedules.editTimerSchedule(slotId) { draft ->
        draft.copy(name = name.trim())
    }
    is DeviceTimerProgramEdit.ToggleEnabled -> schedules.editTimerSchedule(slotId) { draft ->
        draft.copy(enabled = !draft.enabled)
    }
    is DeviceTimerProgramEdit.ToggleWeekday -> schedules.editTimerSchedule(slotId) { draft ->
        if (weekdayIndex !in draft.weekdays.indices) {
            draft
        } else {
            draft.copy(
                weekdays = draft.weekdays.toMutableList().apply {
                    this[weekdayIndex] = !this[weekdayIndex]
                }
            )
        }
    }
    is DeviceTimerProgramEdit.UpdateStartTime -> schedules.editTimerSchedule(slotId) { draft ->
        draft.copy(startMinutesOfDay = minutesOfDay)
    }
    is DeviceTimerProgramEdit.UpdateEndTime -> schedules.editTimerSchedule(slotId) { draft ->
        draft.copy(endMinutesOfDay = minutesOfDay)
    }
}

private fun List<DeviceTimerProgramDraft>.addTimerSchedule(
    defaultName: String
): List<DeviceTimerProgramDraft> {
    val slotId = (MIN_SCHEDULE_SLOT_ID..MAX_SCHEDULE_SLOT_ID)
        .firstOrNull { candidate -> none { draft -> draft.slotId == candidate } }
    val name = defaultName.trim()
    return if (slotId == null || name.isBlank()) {
        this
    } else {
        this + DeviceTimerProgramDraft(
            slotId = slotId,
            enabled = true,
            name = name,
            weekdays = List(WEEKDAY_COUNT) { true },
            startMinutesOfDay = DEFAULT_START_MINUTES,
            endMinutesOfDay = DEFAULT_END_MINUTES
        )
    }
}

private fun List<DeviceTimerProgramDraft>.editTimerSchedule(
    slotId: Int,
    transform: (DeviceTimerProgramDraft) -> DeviceTimerProgramDraft
): List<DeviceTimerProgramDraft> = map { draft ->
    if (draft.slotId == slotId) transform(draft) else draft
}

internal fun List<DeviceTimerProgramDraft>.timerProgramValidationIssue(
    maxSchedules: Int,
    spansMidnightSupported: Boolean
): DeviceTimerProgramValidationIssue? {
    val fieldsValid = size <= maxSchedules &&
        map { it.slotId }.distinct().size == size &&
        all { draft -> draft.hasValidTimerFields(spansMidnightSupported) }
    val programsOverlap = fieldsValid && timerIntervals().hasOverlappingTimerPrograms()
    return when {
        !fieldsValid -> DeviceTimerProgramValidationIssue.INVALID_FIELD
        programsOverlap -> DeviceTimerProgramValidationIssue.OVERLAPPING_PROGRAMS
        else -> null
    }
}

private fun DeviceTimerProgramDraft.hasValidTimerFields(
    spansMidnightSupported: Boolean
): Boolean {
    val identityValid = slotId in MIN_SCHEDULE_SLOT_ID..MAX_SCHEDULE_SLOT_ID
    val nameValid = name.isNotBlank() &&
        name.none { character -> character.isISOControl() } &&
        name.toByteArray(Charsets.UTF_8).size <= MAX_NAME_UTF8_BYTES
    val activationValid = weekdays.size == WEEKDAY_COUNT &&
        (!enabled || weekdays.any { it })
    val timeRangeValid = startMinutesOfDay in 0 until MINUTES_PER_DAY &&
        endMinutesOfDay in 0 until MINUTES_PER_DAY &&
        startMinutesOfDay != endMinutesOfDay
    val midnightValid = spansMidnightSupported || !spansMidnight
    return identityValid && nameValid && activationValid && timeRangeValid && midnightValid
}

private data class TimerWeekInterval(
    val slotId: Int,
    val start: Int,
    val end: Int
)

private fun List<DeviceTimerProgramDraft>.timerIntervals(): List<TimerWeekInterval> =
    buildList {
        this@timerIntervals.forEach { schedule ->
            schedule.weekdays.forEachIndexed { dayIndex, selected ->
                if (!selected) return@forEachIndexed
                val start = dayIndex * MINUTES_PER_DAY + schedule.startMinutesOfDay
                val end = dayIndex * MINUTES_PER_DAY + schedule.endMinutesOfDay +
                    if (schedule.spansMidnight) MINUTES_PER_DAY else 0
                add(TimerWeekInterval(schedule.slotId, start, end))
                add(TimerWeekInterval(schedule.slotId, start + MINUTES_PER_WEEK, end + MINUTES_PER_WEEK))
            }
        }
    }

private fun List<TimerWeekInterval>.hasOverlappingTimerPrograms(): Boolean =
    indices.any { firstIndex ->
        val first = this[firstIndex]
        ((firstIndex + 1) until size).any { secondIndex ->
            val second = this[secondIndex]
            first.slotId != second.slotId && first.start < second.end && second.start < first.end
        }
    }

private fun DeviceTimerScheduleSnapshot.toDraft() = DeviceTimerProgramDraft(
    slotId = slotId,
    enabled = enabled,
    name = name,
    weekdays = weekdays,
    startMinutesOfDay = (startTimeMillis / MILLIS_PER_MINUTE).toInt(),
    endMinutesOfDay = (endTimeMillis / MILLIS_PER_MINUTE).toInt()
)

private fun DeviceTimerProgramDraft.toApplicationDraft() = DeviceTimerScheduleDraft(
    slotId = slotId,
    enabled = enabled,
    name = name,
    weekdays = weekdays,
    startTimeMillis = startMinutesOfDay * MILLIS_PER_MINUTE,
    endTimeMillis = endMinutesOfDay * MILLIS_PER_MINUTE
)

private fun DeviceTimerChannelSnapshot.displayTitle(): String =
    displayName.ifBlank { defaultName }

private const val MIN_SCHEDULE_SLOT_ID = 1
private const val MAX_SCHEDULE_SLOT_ID = 8
private const val MAX_NAME_UTF8_BYTES = 48
private const val WEEKDAY_COUNT = 7
private const val MINUTES_PER_DAY = 24 * 60
private const val MINUTES_PER_WEEK = 7 * MINUTES_PER_DAY
private const val MILLIS_PER_MINUTE = 60_000L
private const val DEFAULT_START_MINUTES = 8 * 60
private const val DEFAULT_END_MINUTES = 18 * 60
