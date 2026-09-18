package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.custom

internal class DeviceLightCustomDayEditor(
    private val currentState: () -> DeviceLightCustomCurveUiState,
    private val setDraft: (DeviceLightCustomDraft, Long?) -> Unit
) {
    fun toggleWeekday(dayIndex: Int) {
        require(dayIndex in FIRST_WEEKDAY_INDEX..LAST_WEEKDAY_INDEX)
        val state = currentState()
        if (!state.contentEnabled || state.operationInProgress) return
        val bit = customWeekdayMask(dayIndex)
        val changed = state.draft.weekdaysMask xor bit
        val mask = changed.takeIf { value -> value != 0 } ?: state.draft.weekdaysMask
        setDraft(state.draft.copy(weekdaysMask = mask), state.selectedTimeMs)
    }
}

internal class DeviceLightCustomPointEditor(
    private val currentState: () -> DeviceLightCustomCurveUiState,
    private val updateState: ((DeviceLightCustomCurveUiState) -> DeviceLightCustomCurveUiState) -> Unit,
    private val setDraft: (DeviceLightCustomDraft, Long?) -> Unit,
    private val emit: (DeviceLightCustomCurveEffect) -> Unit
) {
    fun updatePlayhead(timeMs: Long) {
        val state = currentState()
        if (!state.contentEnabled || state.operationInProgress) return
        updateState { current -> current.withEditPlayhead(timeMs) }
    }

    fun requestPlayheadTime(editSelected: Boolean) {
        val state = currentState()
        if (!state.contentEnabled || state.operationInProgress) return
        val selectedPoint = state.selectedPoint?.takeIf { point ->
            editSelected && point.timeMs == state.previewTimeMs
        }
        val existingPoint = state.draft.points.singleOrNull { point ->
            point.timeMs == state.previewTimeMs
        }
        when {
            selectedPoint != null -> requestEditSelectedTime()
            existingPoint != null -> selectGraphPoint(existingPoint.timeMs)
            else -> requestAddPoint(state.previewTimeMs)
        }
    }

    private fun requestAddPoint(preferredTimeMs: Long) {
        val state = currentState()
        when {
            !state.contentEnabled || state.operationInProgress -> Unit
            state.draft.points.size >= state.maxPoints -> {
                emit(DeviceLightCustomCurveEffect.ShowPointLimit(state.maxPoints))
                state.selectedTimeMs?.let { selectedTimeMs ->
                    updateState { current -> current.withEditPlayhead(selectedTimeMs) }
                }
            }
            else -> emit(
                DeviceLightCustomCurveEffect.OpenTimePicker(
                    DeviceLightCustomTimePickerPurpose.Add(preferredTimeMs.alignedTime())
                )
            )
        }
    }

    fun requestEditSelectedTime() {
        val state = currentState()
        if (!state.operationInProgress) {
            state.selectedTimeMs?.let { time ->
                emit(
                    DeviceLightCustomCurveEffect.OpenTimePicker(
                        DeviceLightCustomTimePickerPurpose.Move(time)
                    )
                )
            }
        }
    }

    fun selectGraphPoint(timeMs: Long) {
        val state = currentState()
        if (state.contentEnabled && !state.operationInProgress) {
            state.draft.points.singleOrNull { point -> point.timeMs == timeMs }?.let { point ->
                updateState { current ->
                    current.copy(
                        selectedTimeMs = point.timeMs,
                        previewTimeMs = point.timeMs,
                        playheadMode = DeviceLightCustomPlayheadMode.EDIT
                    )
                }
            }
        }
    }

    fun requestPointActions(timeMs: Long) {
        val state = currentState()
        if (state.contentEnabled && !state.operationInProgress &&
            state.draft.points.any { point -> point.timeMs == timeMs }
        ) {
            selectGraphPoint(timeMs)
            emit(DeviceLightCustomCurveEffect.OpenPointActions(timeMs))
        }
    }

    fun cancelTimeSelection(purpose: DeviceLightCustomTimePickerPurpose) {
        val state = currentState()
        val restoredTimeMs = when (purpose) {
            is DeviceLightCustomTimePickerPurpose.Add ->
                state.selectedTimeMs ?: purpose.preferredTimeMs
            is DeviceLightCustomTimePickerPurpose.Move -> purpose.originalTimeMs
        }
        updateState { current -> current.withEditPlayhead(restoredTimeMs) }
    }

    fun addOrMovePoint(originalTimeMs: Long?, targetTimeMs: Long) {
        val state = currentState()
        val aligned = targetTimeMs.alignedTime()
        val timeOccupied = state.draft.points.any { point ->
            point.timeMs == aligned && point.timeMs != originalTimeMs
        }
        when {
            !state.contentEnabled || state.operationInProgress || timeOccupied -> Unit
            originalTimeMs == null && state.draft.points.size >= state.maxPoints ->
                emit(DeviceLightCustomCurveEffect.ShowPointLimit(state.maxPoints))
            else -> {
                setDraft(
                    state.draft.copy(
                        points = state.changedPoints(originalTimeMs, aligned)
                    ),
                    aligned
                )
                updateState { current -> current.withEditPlayhead(aligned) }
            }
        }
    }

    fun deletePoint(timeMs: Long) {
        val state = currentState()
        val point = state.draft.points.singleOrNull { candidate -> candidate.timeMs == timeMs }
        if (state.canDeleteSelectedPoint && point != null && state.selectedTimeMs == timeMs) {
            val points = state.draft.points.filterNot { candidate -> candidate.timeMs == timeMs }
            val nextSelection = points.minByOrNull { point ->
                kotlin.math.abs(point.timeMs - timeMs)
            }?.timeMs
            setDraft(state.draft.copy(points = points), nextSelection)
            nextSelection?.let { nextTime ->
                updateState { current -> current.withEditPlayhead(nextTime) }
            }
        }
    }

    fun updateSelectedChannel(channel: DeviceLightCustomChannelId, percent: Int) {
        val state = currentState()
        val selected = state.selectedPoint
        if (!state.operationInProgress && selected != null && channel in selected.channels) {
            val points = state.draft.points.map { point ->
                point.updateChannelIfSelected(selected.timeMs, channel, percent)
            }
            setDraft(state.draft.copy(points = points), state.selectedTimeMs)
        }
    }
}

private fun DeviceLightCustomCurveUiState.withEditPlayhead(
    timeMs: Long
): DeviceLightCustomCurveUiState = copy(
    previewTimeMs = timeMs.alignedTime(),
    playheadMode = DeviceLightCustomPlayheadMode.EDIT
)

private fun DeviceLightCustomCurveUiState.changedPoints(
    originalTimeMs: Long?,
    alignedTimeMs: Long
): List<DeviceLightCustomPointUiState> {
    val changed = if (originalTimeMs == null) {
        draft.points + DeviceLightCustomPointUiState(
            timeMs = alignedTimeMs,
            channels = draft.points.interpolatedChannelsAt(alignedTimeMs, channels)
        )
    } else {
        draft.points.map { point ->
            if (point.timeMs == originalTimeMs) point.copy(timeMs = alignedTimeMs) else point
        }
    }
    return changed.sortedBy { point -> point.timeMs }
}

private fun DeviceLightCustomPointUiState.updateChannelIfSelected(
    selectedTimeMs: Long,
    channel: DeviceLightCustomChannelId,
    percent: Int
): DeviceLightCustomPointUiState = if (timeMs == selectedTimeMs) {
    copy(
        channels = channels + (
            channel to percent.coerceIn(
                MIN_LIGHT_CHANNEL_PERCENT,
                MAX_LIGHT_CHANNEL_PERCENT
            )
        )
    )
} else {
    this
}

internal fun Long.alignedTime(): Long = coerceIn(0L, MILLIS_PER_DAY - MILLIS_PER_MINUTE)
    .div(MILLIS_PER_MINUTE)
    .times(MILLIS_PER_MINUTE)

internal const val FIRST_WEEKDAY_INDEX = 0
internal const val LAST_WEEKDAY_INDEX = 6
