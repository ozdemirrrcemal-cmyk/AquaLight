package com.aqua.aqualight.ui.tabs.devices.detail.timer.program

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.application.devices.timer.DeviceTimerChannelSnapshot
import com.aqua.aqualight.application.devices.timer.DeviceTimerControlFailure
import com.aqua.aqualight.application.devices.timer.DeviceTimerControlOperations
import com.aqua.aqualight.application.devices.timer.DeviceTimerControlResult
import com.aqua.aqualight.application.devices.timer.DeviceTimerScheduleDraft
import com.aqua.aqualight.application.devices.timer.DeviceTimerScheduleSnapshot
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@Suppress("TooManyFunctions")
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
                loadState = DeviceTimerProgramLoadState.FAILED,
                failure = DeviceTimerControlFailure.Unsupported
            )
            return
        }
        if (_uiState.value.deviceUid == deviceUid && _uiState.value.slotId == slotId) return
        _uiState.value = DeviceTimerProgramUiState(
            deviceUid = deviceUid,
            slotId = slotId,
            loadState = DeviceTimerProgramLoadState.LOADING
        )
        viewModelScope.launch {
            val result = runCatching { operations.refreshChannel(deviceUid, slotId) }
                .getOrElse { DeviceTimerControlResult.Failed(DeviceTimerControlFailure.Unavailable) }
            applyLoadResult(deviceUid, slotId, result)
        }
    }

    fun addSchedule(defaultName: String) = editSchedules { current ->
        val slotId = (MIN_SCHEDULE_SLOT_ID..MAX_SCHEDULE_SLOT_ID)
            .firstOrNull { candidate -> current.none { draft -> draft.slotId == candidate } }
            ?: return@editSchedules current
        current + DeviceTimerProgramDraft(
            slotId = slotId,
            enabled = true,
            name = defaultName.trim().ifBlank { return@editSchedules current },
            weekdays = List(WEEKDAY_COUNT) { true },
            startMinutesOfDay = DEFAULT_START_MINUTES,
            endMinutesOfDay = DEFAULT_END_MINUTES
        )
    }

    fun deleteSchedule(slotId: Int) = editSchedules { current ->
        current.filterNot { draft -> draft.slotId == slotId }
    }

    fun updateName(slotId: Int, name: String) = editSchedule(slotId) { draft ->
        draft.copy(name = name.trim())
    }

    fun toggleEnabled(slotId: Int) = editSchedule(slotId) { draft ->
        draft.copy(enabled = !draft.enabled)
    }

    fun toggleWeekday(slotId: Int, weekdayIndex: Int) = editSchedule(slotId) { draft ->
        if (weekdayIndex !in draft.weekdays.indices) return@editSchedule draft
        draft.copy(
            weekdays = draft.weekdays.toMutableList().apply {
                this[weekdayIndex] = !this[weekdayIndex]
            }
        )
    }

    fun updateStartTime(slotId: Int, minutesOfDay: Int) = editSchedule(slotId) { draft ->
        draft.copy(startMinutesOfDay = minutesOfDay)
    }

    fun updateEndTime(slotId: Int, minutesOfDay: Int) = editSchedule(slotId) { draft ->
        draft.copy(endMinutesOfDay = minutesOfDay)
    }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        _uiState.value = state.copy(saving = true, failure = null)
        viewModelScope.launch {
            val drafts = state.schedules.map(DeviceTimerProgramDraft::toApplicationDraft)
            val result = runCatching {
                operations.replaceSchedules(state.deviceUid, state.slotId, drafts)
            }.getOrElse {
                DeviceTimerControlResult.Failed(DeviceTimerControlFailure.Unavailable)
            }
            when (result) {
                is DeviceTimerControlResult.Available -> {
                    originalSchedules = state.schedules
                    _uiState.value = _uiState.value.copy(
                        saving = false,
                        schedules = state.schedules,
                        dirty = false,
                        failure = null
                    )
                    eventChannel.send(DeviceTimerProgramEvent.Saved)
                }
                is DeviceTimerControlResult.Failed -> {
                    _uiState.value = _uiState.value.copy(saving = false, failure = result.failure)
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
                        deviceUid = deviceUid,
                        slotId = slotId,
                        channelTitle = channel.displayTitle(),
                        loadState = DeviceTimerProgramLoadState.CONTENT,
                        maxSchedules = result.snapshot.maxSchedulesPerChannel,
                        spansMidnightSupported = result.snapshot.capabilities.supportsSpansMidnight,
                        editable = !result.snapshot.capabilities.readOnly &&
                            result.snapshot.capabilities.supportsConfigApply &&
                            result.snapshot.capabilities.supportsSchedules &&
                            !result.snapshot.lockLoop,
                        schedules = schedules
                    )
                }
            }
            is DeviceTimerControlResult.Failed -> applyLoadFailure(result.failure)
        }
    }

    private fun applyLoadFailure(failure: DeviceTimerControlFailure) {
        _uiState.value = _uiState.value.copy(
            loadState = DeviceTimerProgramLoadState.FAILED,
            failure = failure
        )
    }

    private fun editSchedule(
        slotId: Int,
        transform: (DeviceTimerProgramDraft) -> DeviceTimerProgramDraft
    ) = editSchedules { schedules ->
        schedules.map { draft -> if (draft.slotId == slotId) transform(draft) else draft }
    }

    private fun editSchedules(
        transform: (List<DeviceTimerProgramDraft>) -> List<DeviceTimerProgramDraft>
    ) {
        val state = _uiState.value
        if (!state.editable || state.saving) return
        val schedules = transform(state.schedules).take(state.maxSchedules)
        _uiState.value = state.copy(
            schedules = schedules,
            dirty = schedules != originalSchedules,
            failure = null
        )
    }
}

@Suppress("LongParameterList")
data class DeviceTimerProgramUiState(
    val deviceUid: String = "",
    val slotId: String = "",
    val channelTitle: String = "",
    val loadState: DeviceTimerProgramLoadState = DeviceTimerProgramLoadState.IDLE,
    val maxSchedules: Int = 0,
    val spansMidnightSupported: Boolean = false,
    val editable: Boolean = false,
    val schedules: List<DeviceTimerProgramDraft> = emptyList(),
    val dirty: Boolean = false,
    val saving: Boolean = false,
    val failure: DeviceTimerControlFailure? = null
) {
    val valid: Boolean
        get() = schedules.isValidTimerProgram(maxSchedules, spansMidnightSupported)
    val canSave: Boolean
        get() = loadState == DeviceTimerProgramLoadState.CONTENT &&
            editable && dirty && valid && !saving
    val canAdd: Boolean
        get() = editable && !saving && schedules.size < maxSchedules && nextScheduleSlotId != null
    val nextScheduleSlotId: Int?
        get() = (MIN_SCHEDULE_SLOT_ID..MAX_SCHEDULE_SLOT_ID)
            .firstOrNull { candidate -> schedules.none { draft -> draft.slotId == candidate } }
}

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

sealed interface DeviceTimerProgramEvent {
    data object Saved : DeviceTimerProgramEvent
    data class Failed(val failure: DeviceTimerControlFailure) : DeviceTimerProgramEvent
}

@Suppress("ComplexCondition")
private fun List<DeviceTimerProgramDraft>.isValidTimerProgram(
    maxSchedules: Int,
    spansMidnightSupported: Boolean
): Boolean = size <= maxSchedules &&
    map { it.slotId }.distinct().size == size &&
    all { draft ->
        draft.slotId in MIN_SCHEDULE_SLOT_ID..MAX_SCHEDULE_SLOT_ID &&
            draft.name.isNotBlank() &&
            draft.name.toByteArray(Charsets.UTF_8).size <= MAX_NAME_UTF8_BYTES &&
            draft.weekdays.size == WEEKDAY_COUNT &&
            (!draft.enabled || draft.weekdays.any { it }) &&
            draft.startMinutesOfDay in 0 until MINUTES_PER_DAY &&
            draft.endMinutesOfDay in 0 until MINUTES_PER_DAY &&
            draft.startMinutesOfDay != draft.endMinutesOfDay &&
            (spansMidnightSupported || !draft.spansMidnight)
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
private const val MILLIS_PER_MINUTE = 60_000L
private const val DEFAULT_START_MINUTES = 8 * 60
private const val DEFAULT_END_MINUTES = 18 * 60
