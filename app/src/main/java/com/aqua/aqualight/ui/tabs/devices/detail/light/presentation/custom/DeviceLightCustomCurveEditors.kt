package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.custom

import kotlin.math.roundToLong

internal class DeviceLightCustomDayEditor(
    private val currentState: () -> DeviceLightCustomCurveUiState,
    private val setDraft: (DeviceLightCustomDraft, Long?) -> Unit
) {
    fun selectEveryDay() = mutateDraft { draft -> draft.copy(weekdaysMask = EVERY_DAY_MASK) }

    fun toggleWeekday(dayIndex: Int) {
        require(dayIndex in FIRST_WEEKDAY_INDEX..LAST_WEEKDAY_INDEX)
        mutateDraft { draft ->
            val bit = 1 shl dayIndex
            val changed = draft.weekdaysMask xor bit
            draft.copy(weekdaysMask = changed.takeIf { mask -> mask != 0 } ?: draft.weekdaysMask)
        }
    }

    private fun mutateDraft(change: (DeviceLightCustomDraft) -> DeviceLightCustomDraft) {
        val state = currentState()
        if (state.contentEnabled && !state.operationInProgress) {
            setDraft(change(state.draft), state.selectedTimeMs)
        }
    }
}

internal class DeviceLightCustomPointEditor(
    private val currentState: () -> DeviceLightCustomCurveUiState,
    private val updateState: ((DeviceLightCustomCurveUiState) -> DeviceLightCustomCurveUiState) -> Unit,
    private val setDraft: (DeviceLightCustomDraft, Long?) -> Unit,
    private val emit: (DeviceLightCustomCurveEffect) -> Unit
) {
    fun requestAddPoint() {
        val state = currentState()
        when {
            !state.contentEnabled || state.operationInProgress -> Unit
            state.draft.points.size >= state.maxPoints -> emitPointLimit(state.maxPoints)
            else -> emit(DeviceLightCustomCurveEffect.OpenTimePicker(null))
        }
    }

    fun requestEditSelectedTime() {
        val state = currentState()
        if (!state.operationInProgress) {
            state.selectedTimeMs?.let { time ->
                emit(DeviceLightCustomCurveEffect.OpenTimePicker(time))
            }
        }
    }

    fun selectOrAddGraphTime(timeMs: Long) {
        val state = currentState()
        if (state.contentEnabled && !state.operationInProgress) {
            val aligned = timeMs.alignedTime()
            val existing = state.draft.points.singleOrNull { point -> point.timeMs == aligned }
            when {
                existing != null -> updateState { current ->
                    current.copy(selectedTimeMs = existing.timeMs)
                }
                state.draft.points.size >= state.maxPoints -> emitPointLimit(state.maxPoints)
                else -> addOrMovePoint(originalTimeMs = null, targetTimeMs = aligned)
            }
        }
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
                emitPointLimit(state.maxPoints)
            else -> setDraft(
                state.draft.copy(
                    points = state.changedPoints(originalTimeMs, aligned)
                ),
                aligned
            )
        }
    }

    fun duplicateSelectedPoint() {
        val state = currentState()
        val selected = state.selectedPoint
        when {
            state.operationInProgress || selected == null -> Unit
            state.draft.points.size >= state.maxPoints -> emitPointLimit(state.maxPoints)
            else -> duplicatePointAtAvailableTime(state, selected)
        }
    }

    fun deleteSelectedPoint() {
        val state = currentState()
        val selected = state.selectedPoint
        if (!state.operationInProgress && selected != null) {
            val points = state.draft.points.filterNot { point -> point.timeMs == selected.timeMs }
            val nextSelection = points.minByOrNull { point ->
                kotlin.math.abs(point.timeMs - selected.timeMs)
            }?.timeMs
            setDraft(state.draft.copy(points = points), nextSelection)
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

    fun stepSelectedChannel(channel: DeviceLightCustomChannelId, delta: Int) {
        currentState().selectedPoint?.channels?.get(channel)?.let { value ->
            updateSelectedChannel(channel, value + delta)
        }
    }

    fun updatePreviewTime(timeMs: Long) {
        updateState { state -> state.copy(previewTimeMs = timeMs.alignedTime()) }
    }

    private fun duplicatePointAtAvailableTime(
        state: DeviceLightCustomCurveUiState,
        selected: DeviceLightCustomPointUiState
    ) {
        val target = state.availableDuplicateTime(selected.timeMs)
        if (target != null) {
            setDraft(
                state.draft.copy(
                    points = (state.draft.points + selected.copy(timeMs = target))
                        .sortedBy { point -> point.timeMs }
                ),
                target
            )
        }
    }

    private fun emitPointLimit(maxPoints: Int) {
        emit(DeviceLightCustomCurveEffect.ShowPointLimit(maxPoints))
    }
}

private fun DeviceLightCustomCurveUiState.changedPoints(
    originalTimeMs: Long?,
    alignedTimeMs: Long
): List<DeviceLightCustomPointUiState> {
    val changed = if (originalTimeMs == null) {
        draft.points + DeviceLightCustomPointUiState(
            timeMs = alignedTimeMs,
            channels = interpolatedChannels(alignedTimeMs)
        )
    } else {
        draft.points.map { point ->
            if (point.timeMs == originalTimeMs) point.copy(timeMs = alignedTimeMs) else point
        }
    }
    return changed.sortedBy { point -> point.timeMs }
}

private fun DeviceLightCustomCurveUiState.interpolatedChannels(
    timeMs: Long
): Map<DeviceLightCustomChannelId, Int> {
    val availableChannels = draft.points.firstOrNull()?.channels?.keys ?: channels
    val before = draft.points.lastOrNull { point -> point.timeMs < timeMs }
    val after = draft.points.firstOrNull { point -> point.timeMs > timeMs }
    return availableChannels.associateWith { channel ->
        interpolatedPercent(before, after, channel, timeMs)
    }
}

private fun interpolatedPercent(
    before: DeviceLightCustomPointUiState?,
    after: DeviceLightCustomPointUiState?,
    channel: DeviceLightCustomChannelId,
    timeMs: Long
): Int = when {
    before == null && after == null -> MIN_LIGHT_CHANNEL_PERCENT
    before == null -> after?.channels?.get(channel) ?: MIN_LIGHT_CHANNEL_PERCENT
    after == null -> before.channels[channel] ?: MIN_LIGHT_CHANNEL_PERCENT
    else -> {
        val fraction = (timeMs - before.timeMs).toDouble() /
            (after.timeMs - before.timeMs).toDouble()
        val start = before.channels[channel] ?: MIN_LIGHT_CHANNEL_PERCENT
        val end = after.channels[channel] ?: MIN_LIGHT_CHANNEL_PERCENT
        (start + (end - start) * fraction).roundToLong().toInt()
    }
}

private fun DeviceLightCustomCurveUiState.availableDuplicateTime(selectedTimeMs: Long): Long? {
    val occupied = draft.points.mapTo(mutableSetOf()) { point -> point.timeMs }
    val later = generateSequence(selectedTimeMs + timeStepMs) { value -> value + timeStepMs }
        .takeWhile { value -> value < MILLIS_PER_DAY }
        .firstOrNull { value -> value !in occupied }
    return later ?: generateSequence(selectedTimeMs - timeStepMs) { value -> value - timeStepMs }
        .takeWhile { value -> value >= 0L }
        .firstOrNull { value -> value !in occupied }
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
