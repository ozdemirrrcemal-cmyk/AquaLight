package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.hourly

/** Typed UI boundary for the firmware's 24-dose hourly schedule mode. */
internal object DeviceDosingHourlyScheduleContract {
    const val RESULT_REQUEST_KEY = "dosing_hourly_schedule_result"
    const val RESULT_KEY = "dosing_hourly_schedule_result_state"
    const val RESULT_START_TIME_MS = "dosing_hourly_schedule_start_time_ms"
    const val RESULT_SLOT_ID = "dosing_hourly_schedule_slot_id"
    const val RESULT_SAVED = "saved"

    const val DOSES_PER_DAY = 24
    const val MINUTES_PER_HOUR = 60
    const val MILLIS_PER_MINUTE = 60_000L
    const val LAST_MILLISECOND_OF_HOUR = 3_599_999L

    fun isValidStartTime(startTimeMs: Long): Boolean =
        startTimeMs in 0L..LAST_MILLISECOND_OF_HOUR

    fun minuteAlignedStartTime(startTimeMs: Long): Long {
        require(isValidStartTime(startTimeMs)) {
            "Hourly startTimeMs must stay inside the first hour."
        }
        return startTimeMs / MILLIS_PER_MINUTE * MILLIS_PER_MINUTE
    }

    fun minuteOfHour(startTimeMs: Long): Int =
        (minuteAlignedStartTime(startTimeMs) / MILLIS_PER_MINUTE).toInt()

    fun startTimeMs(minuteOfHour: Int): Long {
        require(minuteOfHour in 0 until MINUTES_PER_HOUR) {
            "minuteOfHour must be between 0 and 59."
        }
        return minuteOfHour * MILLIS_PER_MINUTE
    }

    fun dailyDoseMl(dailyDoseMicroliters: Long): Double {
        require(dailyDoseMicroliters >= 0L) {
            "dailyDoseMicroliters must not be negative."
        }
        return dailyDoseMicroliters.toDouble() / MICROLITERS_PER_MILLILITER
    }

    fun averageDoseMl(dailyDoseMicroliters: Long): Double =
        dailyDoseMl(dailyDoseMicroliters) / DOSES_PER_DAY

    private const val MICROLITERS_PER_MILLILITER = 1_000.0
}
