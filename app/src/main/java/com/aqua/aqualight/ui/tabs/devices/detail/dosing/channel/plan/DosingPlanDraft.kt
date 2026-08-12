package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.plan

import android.os.Bundle
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.custom.DeviceDosingCustomPeriod
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.custom.DeviceDosingCustomScheduleContract
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.timer.DeviceDosingTimerDose
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.timer.DeviceDosingTimerScheduleContract

internal data class DosingPlanDraft(
    val distributedDailyDoseMicroliters: Long = DEFAULT_DAILY_DOSE_MICROLITERS,
    val singleDoseStartTimeMs: Long = DEFAULT_SINGLE_DOSE_START_TIME_MS,
    val hourlyStartTimeMs: Long = DEFAULT_HOURLY_START_TIME_MS,
    val customPeriods: List<DeviceDosingCustomPeriod> = emptyList(),
    val timerDoses: List<DeviceDosingTimerDose> = emptyList(),
    val selectedScheduleMode: DosingPlanScheduleMode = DosingPlanScheduleMode.SINGLE,
    val scheduleEnabled: Boolean = DEFAULT_SCHEDULE_ENABLED,
    val recurrenceState: DosingPlanRecurrenceState = DosingPlanRecurrenceState()
) {
    fun displayedDailyDoseMicroliters(): Long =
        if (selectedScheduleMode == DosingPlanScheduleMode.TIMER) {
            DeviceDosingTimerScheduleContract.totalDoseMicroliters(timerDoses)
        } else {
            distributedDailyDoseMicroliters
        }

    fun writeTo(outState: Bundle) {
        outState.putLong(STATE_DAILY_DOSE_MICROLITERS, distributedDailyDoseMicroliters)
        outState.putLong(STATE_SINGLE_DOSE_START_TIME_MS, singleDoseStartTimeMs)
        outState.putLong(STATE_HOURLY_START_TIME_MS, hourlyStartTimeMs)
        outState.putString(
            STATE_CUSTOM_PERIODS_DRAFT,
            DeviceDosingCustomScheduleContract.encodeDraft(customPeriods)
        )
        outState.putString(
            STATE_TIMER_DOSES_DRAFT,
            DeviceDosingTimerScheduleContract.encodeDraft(timerDoses)
        )
        outState.putString(STATE_SELECTED_SCHEDULE_MODE, selectedScheduleMode.name)
        outState.putBoolean(STATE_SCHEDULE_ENABLED, scheduleEnabled)
        outState.putBooleanArray(STATE_SCHEDULE_WEEKDAYS, recurrenceState.toWeekdayFlags())
    }

    companion object {
        fun restore(savedInstanceState: Bundle?): DosingPlanDraft {
            if (savedInstanceState == null) return DosingPlanDraft()
            return DosingPlanDraft(
                distributedDailyDoseMicroliters = savedInstanceState.getLong(
                    STATE_DAILY_DOSE_MICROLITERS,
                    DEFAULT_DAILY_DOSE_MICROLITERS
                ),
                singleDoseStartTimeMs = savedInstanceState.getLong(
                    STATE_SINGLE_DOSE_START_TIME_MS,
                    DEFAULT_SINGLE_DOSE_START_TIME_MS
                ),
                hourlyStartTimeMs = savedInstanceState.getLong(
                    STATE_HOURLY_START_TIME_MS,
                    DEFAULT_HOURLY_START_TIME_MS
                ),
                customPeriods = savedInstanceState.getString(STATE_CUSTOM_PERIODS_DRAFT)
                    ?.let(DeviceDosingCustomScheduleContract::decodeDraft)
                    ?: emptyList(),
                timerDoses = savedInstanceState.getString(STATE_TIMER_DOSES_DRAFT)
                    ?.let(DeviceDosingTimerScheduleContract::decodeDraft)
                    ?: emptyList(),
                selectedScheduleMode = savedInstanceState.getString(STATE_SELECTED_SCHEDULE_MODE)
                    ?.let { savedMode ->
                        DosingPlanScheduleMode.entries.firstOrNull { mode -> mode.name == savedMode }
                    }
                    ?: DosingPlanScheduleMode.SINGLE,
                scheduleEnabled = savedInstanceState.getBoolean(
                    STATE_SCHEDULE_ENABLED,
                    DEFAULT_SCHEDULE_ENABLED
                ),
                recurrenceState = savedInstanceState.getBooleanArray(STATE_SCHEDULE_WEEKDAYS)
                    ?.let(DosingPlanRecurrenceState::fromWeekdayFlags)
                    ?: DosingPlanRecurrenceState()
            )
        }

        private const val STATE_DAILY_DOSE_MICROLITERS = "daily_dose_microliters"
        private const val STATE_SINGLE_DOSE_START_TIME_MS = "single_dose_start_time_ms"
        private const val STATE_HOURLY_START_TIME_MS = "hourly_start_time_ms"
        private const val STATE_CUSTOM_PERIODS_DRAFT = "custom_periods_draft"
        private const val STATE_TIMER_DOSES_DRAFT = "timer_doses_draft"
        private const val STATE_SELECTED_SCHEDULE_MODE = "selected_schedule_mode"
        private const val STATE_SCHEDULE_ENABLED = "schedule_enabled"
        private const val STATE_SCHEDULE_WEEKDAYS = "schedule_weekdays"
        private const val DEFAULT_DAILY_DOSE_MICROLITERS = 0L
        private const val DEFAULT_SINGLE_DOSE_START_TIME_MS = 0L
        private const val DEFAULT_HOURLY_START_TIME_MS = 0L
        private const val DEFAULT_SCHEDULE_ENABLED = true
    }
}
