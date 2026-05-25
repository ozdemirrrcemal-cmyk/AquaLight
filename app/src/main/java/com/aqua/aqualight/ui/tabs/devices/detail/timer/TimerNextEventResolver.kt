package com.aqua.aqualight.ui.tabs.devices.detail.timer

import java.util.Calendar
import kotlin.math.ceil

object TimerNextEventResolver {

    data class NextTimerEvent(
        val rule: TimerDeviceRepository.TimerRuleData,
        val outlet: TimerDeviceRepository.TimerOutletData?,
        val outletName: String,
        val dayIndex: Int,
        val dayOffset: Int,
        val minutesUntil: Int
    ) {
        fun shortText(): String {
            val prefix = when (dayOffset) {
                0 -> ""
                1 -> "Tomorrow "
                else -> "${dayNameShort(dayIndex)} "
            }

            return "$prefix${rule.timeStart} · $outletName"
        }

        fun detailText(): String {
            val dayText = when (dayOffset) {
                0 -> "today"
                1 -> "tomorrow"
                else -> "on ${dayNameLong(dayIndex)}"
            }

            return "$outletName will run $dayText at ${rule.timeStart} for ${rule.durationText()}."
        }

        fun rowTimeText(): String {
            return when (dayOffset) {
                0 -> "Today ${rule.timeStart}"
                1 -> "Tomorrow ${rule.timeStart}"
                else -> "${dayNameShort(dayIndex)} ${rule.timeStart}"
            }
        }

        fun rowDurationText(): String {
            return rule.durationText()
        }
    }

    fun resolve(
        data: TimerDeviceRepository.TimerDashboardData,
        now: Calendar = Calendar.getInstance()
    ): NextTimerEvent? {
        return resolveUpcoming(
            data = data,
            limit = 1,
            now = now
        ).firstOrNull()
    }

    fun resolveUpcoming(
        data: TimerDeviceRepository.TimerDashboardData,
        limit: Int = 3,
        now: Calendar = Calendar.getInstance()
    ): List<NextTimerEvent> {
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
            .flatMap { rule ->
                buildOccurrencesForRule(
                    data = data,
                    rule = rule,
                    nowDayIndex = nowDayIndex,
                    nowMinuteOfDay = nowMinuteOfDay
                )
            }
            .sortedWith(
                compareBy<TimerNextEvent> {
                    it.minutesUntil
                }.thenBy {
                    it.rule.index
                }
            )
            .take(
                limit.coerceAtLeast(
                    1
                )
            )
    }

    private fun buildOccurrencesForRule(
        data: TimerDeviceRepository.TimerDashboardData,
        rule: TimerDeviceRepository.TimerRuleData,
        nowDayIndex: Int,
        nowMinuteOfDay: Int
    ): List<NextTimerEvent> {
        val occurrenceMinutes = occurrenceMinutesForRule(
            rule = rule
        )

        if (occurrenceMinutes.isEmpty()) {
            return emptyList()
        }

        val outlet = data.outlets.firstOrNull { item ->
            item.gpioPwm.trim().equals(
                rule.gpioPwm.trim(),
                ignoreCase = true
            )
        }

        val outletName = outlet?.name?.ifBlank {
            rule.name
        } ?: rule.name

        val events = mutableListOf<NextTimerEvent>()

        for (dayOffset in 0..6) {
            val candidateDayIndex = (nowDayIndex + dayOffset) % 7

            if (!isAllowedDay(
                    rule = rule,
                    dayIndex = candidateDayIndex
                )
            ) {
                continue
            }

            occurrenceMinutes.forEach { occurrenceMinute ->
                val minutesUntil =
                    dayOffset * MINUTES_PER_DAY +
                        occurrenceMinute -
                        nowMinuteOfDay

                if (minutesUntil >= 0) {
                    events.add(
                        NextTimerEvent(
                            rule = rule,
                            outlet = outlet,
                            outletName = outletName,
                            dayIndex = candidateDayIndex,
                            dayOffset = dayOffset,
                            minutesUntil = minutesUntil
                        )
                    )
                }
            }
        }

        return events
    }

    private fun occurrenceMinutesForRule(
        rule: TimerDeviceRepository.TimerRuleData
    ): List<Int> {
        val startMinute = parseMinuteOfDay(
            value = rule.timeStart
        ) ?: return emptyList()

        val count = rule.count.coerceAtLeast(
            1
        )

        val onMinutes = parseDurationToMinutes(
            value = rule.intervalOn
        )

        val offMinutes = parseDurationToMinutes(
            value = rule.intervalOff
        )

        val cycleMinutes = onMinutes + offMinutes

        if (
            count <= 1 ||
            cycleMinutes <= 0
        ) {
            return listOf(
                startMinute
            )
        }

        return (0 until count)
            .map { index ->
                startMinute + index * cycleMinutes
            }
            .filter { minute ->
                minute in 0 until MINUTES_PER_DAY
            }
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

        if (
            hour !in 0..23 ||
            minute !in 0..59
        ) {
            return null
        }

        return hour * 60 + minute
    }

    private fun parseDurationToMinutes(
        value: String
    ): Int {
        val parts = value.trim()
            .split(":")
            .mapNotNull { part ->
                part.toIntOrNull()
            }

        if (parts.isEmpty()) {
            return 0
        }

        val totalSeconds = when (parts.size) {
            3 -> {
                parts[0] * 3600 +
                    parts[1] * 60 +
                    parts[2]
            }

            2 -> {
                parts[0] * 60 +
                    parts[1]
            }

            else -> {
                parts[0]
            }
        }

        if (totalSeconds <= 0) {
            return 0
        }

        return ceil(
            totalSeconds / 60.0
        ).toInt()
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
        dayIndex: Int
    ): String {
        return when (dayIndex) {
            0 -> "Sun"
            1 -> "Mon"
            2 -> "Tue"
            3 -> "Wed"
            4 -> "Thu"
            5 -> "Fri"
            6 -> "Sat"
            else -> "Next"
        }
    }

    private fun dayNameLong(
        dayIndex: Int
    ): String {
        return when (dayIndex) {
            0 -> "Sunday"
            1 -> "Monday"
            2 -> "Tuesday"
            3 -> "Wednesday"
            4 -> "Thursday"
            5 -> "Friday"
            6 -> "Saturday"
            else -> "the next active day"
        }
    }

    private const val MINUTES_PER_DAY = 24 * 60
}