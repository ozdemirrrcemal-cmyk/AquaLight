package com.aqua.aqualight.application.care

/** Single commercial product contract for user-entered care-task schedules. */
object CareTaskInputLimits {

    const val MIN_REPEAT_INTERVAL_DAYS = 1
    const val MAX_REPEAT_INTERVAL_DAYS = 365

    const val MIN_MISSED_REMINDER_DAYS = 1
    const val MAX_MISSED_REMINDER_DAYS = 30

    fun isValidRepeatIntervalDays(value: Int): Boolean =
        value in MIN_REPEAT_INTERVAL_DAYS..MAX_REPEAT_INTERVAL_DAYS

    fun isValidMissedReminderDays(value: Int): Boolean =
        value in MIN_MISSED_REMINDER_DAYS..MAX_MISSED_REMINDER_DAYS

    fun parseRepeatIntervalDays(rawValue: String): Int? =
        rawValue.trim().toIntOrNull()?.takeIf(::isValidRepeatIntervalDays)

    fun parseMissedReminderDays(rawValue: String): Int? =
        rawValue.trim().toIntOrNull()?.takeIf(::isValidMissedReminderDays)
}
