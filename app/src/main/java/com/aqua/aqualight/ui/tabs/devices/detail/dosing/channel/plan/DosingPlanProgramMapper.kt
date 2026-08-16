package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.plan

import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgram
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgramMode
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgramSchedule

/** Plan-specific presentation mapping over the central application program contract. */
internal fun DosingPlanDraft.toApplicationProgram(
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

internal fun DeviceDosingProgram.toPlanDraft(): DosingPlanDraft {
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

internal fun DeviceDosingProgramMode.toPlanMode(): DosingPlanScheduleMode = when (this) {
    DeviceDosingProgramMode.SINGLE -> DosingPlanScheduleMode.SINGLE
    DeviceDosingProgramMode.HOURLY_24 -> DosingPlanScheduleMode.HOURLY
    DeviceDosingProgramMode.CUSTOM_PERIODS -> DosingPlanScheduleMode.CUSTOM
    DeviceDosingProgramMode.TIMER -> DosingPlanScheduleMode.TIMER
}
