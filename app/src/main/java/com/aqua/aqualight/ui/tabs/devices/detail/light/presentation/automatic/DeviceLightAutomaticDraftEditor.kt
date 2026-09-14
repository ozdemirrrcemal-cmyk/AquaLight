package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic

import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticChannel
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticWeekday

internal class DeviceLightAutomaticDraftEditor(
    private val currentState: () -> DeviceLightAutomaticProgramEditorUiState,
    private val updateDraft: ((DeviceLightAutomaticEditorDraft) -> DeviceLightAutomaticEditorDraft) -> Unit,
    private val emit: (DeviceLightAutomaticProgramEditorEffect) -> Unit
) {
    fun selectEveryDay() = selectDays(EVERY_DAY_MASK)

    fun selectWeekdays() = selectDays(WEEKDAYS_MASK)

    fun selectWeekend() = selectDays(WEEKEND_MASK)

    fun toggleDay(day: DeviceLightAutomaticWeekday) {
        if (!currentState().contentEnabled) return
        updateDraft { draft ->
            val updated = draft.weekdaysMask xor day.mask
            draft.copy(weekdaysMask = updated)
        }
    }

    fun requestTime(field: DeviceLightAutomaticTimeField) {
        if (!currentState().contentEnabled) return
        val draft = currentState().draft
        val currentValue = when (field) {
            DeviceLightAutomaticTimeField.START -> draft.startTimeMs
            DeviceLightAutomaticTimeField.END -> draft.endTimeMs
        }
        emit(DeviceLightAutomaticProgramEditorEffect.OpenTimePicker(field, currentValue))
    }

    fun updateTime(field: DeviceLightAutomaticTimeField, timeMs: Long) {
        if (!currentState().contentEnabled) return
        updateDraft { draft ->
            when (field) {
                DeviceLightAutomaticTimeField.START -> draft.copy(startTimeMs = timeMs)
                DeviceLightAutomaticTimeField.END -> draft.copy(endTimeMs = timeMs)
            }
        }
    }

    fun selectRamp(durationMs: Long) {
        val state = currentState()
        val source = state.source ?: return
        if (!state.contentEnabled || durationMs !in source.policy.rampDurationsMs) return
        updateDraft { draft -> draft.copy(rampDurationMs = durationMs) }
    }

    fun updateChannel(channel: DeviceLightAutomaticChannel, percent: Int) {
        val state = currentState()
        val source = state.source ?: return
        if (!state.contentEnabled || channel !in source.channels) return
        updateDraft { draft ->
            draft.copy(channels = draft.channels + (channel to percent.coerceIn(MIN_PERCENT, MAX_PERCENT)))
        }
    }

    private fun selectDays(mask: Int) {
        if (!currentState().contentEnabled) return
        updateDraft { draft -> draft.copy(weekdaysMask = mask) }
    }
}

internal val DEVICE_LIGHT_AUTOMATIC_EVERY_DAY_MASK: Int =
    DeviceLightAutomaticWeekday.entries.sumOf(DeviceLightAutomaticWeekday::mask)

private val EVERY_DAY_MASK = DEVICE_LIGHT_AUTOMATIC_EVERY_DAY_MASK
private val WEEKDAYS_MASK = DeviceLightAutomaticWeekday.entries
    .take(WEEKDAY_COUNT)
    .sumOf(DeviceLightAutomaticWeekday::mask)
private val WEEKEND_MASK = DeviceLightAutomaticWeekday.entries
    .drop(WEEKDAY_COUNT)
    .sumOf(DeviceLightAutomaticWeekday::mask)
private const val WEEKDAY_COUNT = 5
private const val MIN_PERCENT = 0
private const val MAX_PERCENT = 100
