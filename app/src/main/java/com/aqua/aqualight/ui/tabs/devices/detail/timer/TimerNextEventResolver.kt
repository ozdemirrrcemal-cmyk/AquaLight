package com.aqua.aqualight.ui.tabs.devices.detail.timer

import java.util.Calendar

object TimerNextEventResolver {

    data class NextTimerEvent(
        val rule: TimerDeviceRepository.TimerRuleData,
        val outlet: TimerDeviceRepository.TimerOutletData?,
        val outletName: String,
        val dayIndex: Int,
        val dayOffset: Int,
        val minutesUntil: Int,
        val eventTime: String
    ) {

        fun topCardText(): String {
            val firstLine = when (dayOffset) {
                0 -> {
                    eventTime
                }

                1 -> {
                    "Tomorrow $eventTime"
                }

                else -> {
                    "${dayNameShort(dayIndex)} $eventTime"
                }
            }

            return "$firstLine\n$outletName"
        }

        fun shortText(): String {
            val prefix = when (dayOffset) {
                0 -> {
                    ""
                }

                1 -> {
                    "Tomorrow "
                }

                else -> {
                    "${dayNameShort(dayIndex)} "
                }
            }

            return "$prefix$eventTime · $outletName"
        }

        fun detailText(): String {
            val dayText = when (dayOffset) {
                0 -> {
                    "today"
                }

                1 -> {
                    "tomorrow"
                }

                else -> {
                    "on ${dayNameLong(dayIndex)}"
                }
            }

            val repeatText = if (rule.count > 1) {
                " It repeats ${rule.count} times."
            } else {
                ""
            }

            return "$outletName will run $dayText at $eventTime for ${rule.durationText()}.$repeatText"
        }

        fun rowTimeText(): String {
            val dayText = when (dayOffset) {
                0 -> {
                    "Today"
                }

                1 -> {
                    "Tomorrow"
                }

                else -> {
                    dayNameShort(dayIndex)
                }
            }

            val repeatText = if (rule.count > 1) {
                " · x${rule.count}"
            } else {
                ""
            }

            return "$dayText $eventTime$repeatText"
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
            .mapNotNull { rule ->
                buildNextStartEventForRule(
                    data = data,
                    rule = rule,
                    nowDayIndex = nowDayIndex,
                    nowMinuteOfDay = nowMinuteOfDay
                )
            }
            .sortedWith(
                compareBy<NextTimerEvent> { event ->
                    event.minutesUntil
                }.thenBy { event ->
                    event.rule.index
                }
            )
            .take(
                limit.coerceAtLeast(
                    1
                )
            )
    }

    private fun buildNextStartEventForRule(
        data: TimerDeviceRepository.TimerDashboardData,
        rule: TimerDeviceRepository.TimerRuleData,
        nowDayIndex: Int,
        nowMinuteOfDay: Int
    ): NextTimerEvent? {
        val startMinute = parseMinuteOfDay(
            value = rule.timeStart
        ) ?: return null

        val outlet = data.outlets.firstOrNull { item ->
            item.gpioPwm.trim().equals(
                rule.gpioPwm.trim(),
                ignoreCase = true
            )
        }

        val outletName = outlet?.name?.ifBlank {
            rule.name
        } ?: rule.name

        for (dayOffset in 0..6) {
            val candidateDayIndex =
                (nowDayIndex + dayOffset) % 7

            if (
                !isAllowedDay(
                    rule = rule,
                    dayIndex = candidateDayIndex
                )
            ) {
                continue
            }

            val minutesUntil =
                dayOffset * MINUTES_PER_DAY +
                    startMinute -
                    nowMinuteOfDay

            if (minutesUntil >= 0) {
                return NextTimerEvent(
                    rule = rule,
                    outlet = outlet,
                    outletName = outletName,
                    dayIndex = candidateDayIndex,
                    dayOffset = dayOffset,
                    minutesUntil = minutesUntil,
                    eventTime = rule.timeStart
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

        if (
            hour !in 0..23 ||
            minute !in 0..59
        ) {
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