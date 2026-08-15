package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.plan

import androidx.compose.runtime.Immutable
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.model.DosingWeekday

/** Process-safe recurrence draft shaped like the firmware's seven weekday flags. */
@Immutable
internal data class DosingPlanRecurrenceState(
    val selectedDays: Set<DosingWeekday> = DOSING_PLAN_WEEKDAYS.toSet()
) {
    val isEveryDay: Boolean
        get() = selectedDays.size == DOSING_PLAN_WEEKDAYS.size &&
            selectedDays.containsAll(DOSING_PLAN_WEEKDAYS)

    fun selectEveryDay(): DosingPlanRecurrenceState =
        if (isEveryDay) this else copy(selectedDays = DOSING_PLAN_WEEKDAYS.toSet())

    fun withDaySelection(
        weekday: DosingWeekday,
        selected: Boolean
    ): DosingPlanRecurrenceState = copy(
        selectedDays = if (selected) selectedDays + weekday else selectedDays - weekday
    )

    fun toWeekdayFlags(): BooleanArray = DOSING_PLAN_WEEKDAYS
        .map { weekday -> weekday in selectedDays }
        .toBooleanArray()

    companion object {
        fun fromWeekdayFlags(flags: BooleanArray): DosingPlanRecurrenceState? {
            if (flags.size != DOSING_PLAN_WEEKDAYS.size) return null
            return DosingPlanRecurrenceState(
                selectedDays = DOSING_PLAN_WEEKDAYS
                    .filterIndexed { index, _ -> flags[index] }
                    .toSet()
            )
        }
    }
}

internal val DOSING_PLAN_WEEKDAYS = listOf(
    DosingWeekday.MONDAY,
    DosingWeekday.TUESDAY,
    DosingWeekday.WEDNESDAY,
    DosingWeekday.THURSDAY,
    DosingWeekday.FRIDAY,
    DosingWeekday.SATURDAY,
    DosingWeekday.SUNDAY
)

internal val DOSING_PLAN_WEEKDAY_LABELS = DOSING_PLAN_WEEKDAYS.map(DosingWeekday::shortLabelRes)
