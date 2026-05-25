package com.aqua.aqualight.ui.tabs.devices.detail.timer

import java.util.Calendar

object TimerNextEventResolver {

    data class NextTimerEvent(
        val rule: TimerDeviceRepository.TimerRuleData,
        val outlet: TimerDeviceRepository.TimerOutletData?,
        val outletName: String,
        val dayOffset: Int,
        val minutesUntil: Int
    ) {
        fun shortText(): String {
            val prefix = when (dayOffset) {
                0 -> ""
                1 -> "Tomorrow "
                else -> "${dayNameShort(dayOffset)} "
            }

            return "$prefix${rule.timeStart} · $outletName"
        }

        fun detailText(): String {
            val dayText = when (dayOffset) {
                0 -> "today"
                1 -> "tomorrow"
                else -> "on ${dayNameLong(dayOffset)}"
            }

            return "$outletName will run $dayText at ${rule.timeStart} for ${rule.durationText()}."
        }
    }

    fun resolve(
        data: TimerDeviceRepository.TimerDashboardData,
        now: Calendar = Calendar.getInstance()
    ): NextTimerEvent? {
        val nowDayIndex = currentDayIndex(
            now = now
        )

        val nowMinuteOfDay =
            now.get(Calendar.HOUR_OF_DAY) * 60 +
                now.get(Calendar.MINUTE)

        return data.timerRules
            .filter { rule ->
                rule.isUsable() &&
                    parseMinuteOfDay(
                        value = rule.timeStart
                    ) != null
            }
            .mapNotNull { rule ->
                val ruleMinuteOfDay = parseMinuteOfDay(
                    value = rule.timeStart
                ) ?: return@mapNotNull null

                val outlet = data.outlets.firstOrNull { item ->
                    item.gpioPwm.trim().equals(
                        rule.gpioPwm.trim(),
                        ignoreCase = true
                    )
                }

                val outletName = outlet?.name?.ifBlank {
                    rule.name
                } ?: rule.name

                findNextOccurrence(
                    rule = rule,
                    ruleMinuteOfDay = ruleMinuteOfDay,
                    nowDayIndex = nowDayIndex,
                    nowMinuteOfDay = nowMinuteOfDay
                )?.let { occurrence ->
                    NextTimerEvent(
                        rule = rule,
                        outlet = outlet,
                        outletName = outletName,
                        dayOffset = occurrence.dayOffset,
                        minutesUntil = occurrence.minutesUntil
                    )
                }
            }
            .minByOrNull { event ->
                event.minutesUntil
            }
    }

    private data class Occurrence(
        val dayOffset: Int,
        val minutesUntil: Int
    )

    private fun findNextOccurrence(
        rule: TimerDeviceRepository.TimerRuleData,
        ruleMinuteOfDay: Int,
        nowDayIndex: Int,
        nowMinuteOfDay: Int
    ): Occurrence? {
        for (dayOffset in 0..6) {
            val candidateDayIndex = (nowDayIndex + dayOffset) % 7

            if (!isAllowedDay(rule, candidateDayIndex)) {
                continue
            }

            val minutesUntil =
                dayOffset * MINUTES_PER_DAY +
                    ruleMinuteOfDay -
                    nowMinuteOfDay

            if (minutesUntil > 0) {
                return Occurrence(
                    dayOffset = dayOffset,
                    minutesUntil = minutesUntil
                )
            }
        }

        return null
    }

    private fun parseMinuteOfDay(
        value: String
    ): Int? {
        val parts = value.trim()
            .split(":")
            .mapNotNull { part ->
                part.toIntOrNull()
            }

        if (parts.size < 2) {
            return null
        }

        val hour = parts[0]
        val minute = parts[1]

        if (hour !in 0..23 || minute !in 0..59) {
            return null
        }

        return hour * 60 + minute
    }

    private fun isAllowedDay(
        rule: TimerDeviceRepository.TimerRuleData,
        dayIndex: Int
    ): Boolean {
        val days = rule.weekDays

        if (days.isEmpty()) {
            return true
        }

        if (days.none { enabled ->
                enabled
            }
        ) {
            return true
        }

        return days.getOrNull(
            index = dayIndex
        ) == true
    }

    private fun currentDayIndex(
        now: Calendar
    ): Int {
        return when (now.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SUNDAY -> 0
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            else -> 0
        }
    }

    private fun dayNameShort(
        dayOffset: Int
    ): String {
        return when (dayOffset % 7) {
            1 -> "Tomorrow"
            2 -> "Later"
            else -> "Next"
        }
    }

    private fun dayNameLong(
        dayOffset: Int
    ): String {
        return when (dayOffset % 7) {
            1 -> "tomorrow"
            2 -> "the next active day"
            else -> "the next active day"
        }
    }

    private const val MINUTES_PER_DAY = 24 * 60
}