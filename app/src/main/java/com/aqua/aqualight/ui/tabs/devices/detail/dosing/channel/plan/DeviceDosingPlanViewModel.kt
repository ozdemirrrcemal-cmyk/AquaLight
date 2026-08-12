package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.plan

import androidx.lifecycle.ViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.DosingWeekday
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.custom.DeviceDosingCustomPeriod
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.timer.DeviceDosingTimerDose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Single presentation-state owner for the firmware-independent Dosing Plan draft. */
internal class DeviceDosingPlanViewModel : ViewModel() {
    private val mutableDraft = MutableStateFlow(DosingPlanDraft())
    val draft: StateFlow<DosingPlanDraft> = mutableDraft.asStateFlow()
    private var initialized = false

    fun bindInitial(initial: DosingPlanDraft) {
        if (initialized) return
        initialized = true
        mutableDraft.value = initial
    }

    fun currentDraft(): DosingPlanDraft = mutableDraft.value

    fun setDailyDoseMicroliters(microliters: Long) {
        if (microliters <= 0L) return
        update { state -> state.copy(distributedDailyDoseMicroliters = microliters) }
    }

    fun selectSingle(startTimeMs: Long) = update { state -> state.copy(
        singleDoseStartTimeMs = startTimeMs,
        selectedScheduleMode = DosingPlanScheduleMode.SINGLE
    ) }

    fun selectHourly(startTimeMs: Long) = update { state -> state.copy(
        hourlyStartTimeMs = startTimeMs,
        selectedScheduleMode = DosingPlanScheduleMode.HOURLY
    ) }

    fun selectCustom(periods: List<DeviceDosingCustomPeriod>) = update { state -> state.copy(
        customPeriods = periods,
        selectedScheduleMode = DosingPlanScheduleMode.CUSTOM
    ) }

    fun selectTimer(doses: List<DeviceDosingTimerDose>) = update { state -> state.copy(
        timerDoses = doses,
        selectedScheduleMode = DosingPlanScheduleMode.TIMER
    ) }

    fun setScheduleEnabled(enabled: Boolean) = update { state ->
        state.copy(scheduleEnabled = enabled)
    }

    fun selectEveryDay() = update { state ->
        state.copy(recurrenceState = state.recurrenceState.selectEveryDay())
    }

    fun setWeekdaySelected(weekday: DosingWeekday, selected: Boolean) = update { state ->
        state.copy(recurrenceState = state.recurrenceState.withDaySelection(weekday, selected))
    }

    private inline fun update(transform: (DosingPlanDraft) -> DosingPlanDraft) {
        mutableDraft.value = transform(mutableDraft.value)
    }
}
