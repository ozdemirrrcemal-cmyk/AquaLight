package com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.card

import java.time.LocalDate

/** Pure presentation estimate; firmware remains the source of remaining liquid and recurrence. */
internal object DosingReservoirProjection {

    fun estimateRemainingDays(
        remainingMicroliters: Long,
        dailyDoseMicroliters: Long,
        remainingScheduledTodayMicroliters: Long,
        selectedWeekdays: List<Boolean>,
        today: LocalDate
    ): Int? {
        val inputsValid = listOf(
            remainingMicroliters >= 0L,
            dailyDoseMicroliters > 0L,
            selectedWeekdays.size == DAYS_PER_WEEK,
            selectedWeekdays.any { it }
        ).all { valid -> valid }
        if (!inputsValid) return null

        var available = remainingMicroliters
        val exhaustedOnDay = (0..MAX_PROJECTION_DAYS).firstOrNull { dayOffset ->
            val plannedAmount = when {
                dayOffset == 0 -> remainingScheduledTodayMicroliters.coerceAtLeast(0L)
                selectedWeekdays[today.plusDays(dayOffset.toLong()).dayOfWeek.value - 1] ->
                    dailyDoseMicroliters
                else -> 0L
            }
            val exhausted = plannedAmount > available
            if (!exhausted) available -= plannedAmount
            exhausted
        }
        return exhaustedOnDay ?: MAX_PROJECTION_DAYS
    }

    private const val DAYS_PER_WEEK = 7
    private const val MAX_PROJECTION_DAYS = 3_650
}
