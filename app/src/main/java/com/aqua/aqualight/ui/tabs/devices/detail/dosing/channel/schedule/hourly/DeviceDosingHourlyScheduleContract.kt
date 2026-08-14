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
    const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR
    const val MILLIS_PER_MINUTE = 60_000L
    const val MILLIS_PER_HOUR = MINUTES_PER_HOUR * MILLIS_PER_MINUTE
    const val MILLIS_PER_DAY = MINUTES_PER_DAY * MILLIS_PER_MINUTE
    const val LAST_MILLISECOND_OF_DAY = MILLIS_PER_DAY - 1L

    fun isValidStartTime(startTimeMs: Long): Boolean =
        startTimeMs in 0L..LAST_MILLISECOND_OF_DAY

    fun minutesOfDay(startTimeMs: Long): Int {
        require(isValidStartTime(startTimeMs)) {
            "Hourly startTimeMs must stay inside one local day."
        }
        return (startTimeMs / MILLIS_PER_MINUTE).toInt()
    }

    fun startTimeMs(minutesOfDay: Int): Long {
        require(minutesOfDay in 0 until MINUTES_PER_DAY) {
            "minutesOfDay must stay inside one local day."
        }
        return minutesOfDay * MILLIS_PER_MINUTE
    }

    fun lastDoseTimeMs(startTimeMs: Long): Long {
        require(isValidStartTime(startTimeMs)) {
            "Hourly startTimeMs must stay inside one local day."
        }
        return (startTimeMs + (DOSES_PER_DAY - 1L) * MILLIS_PER_HOUR) % MILLIS_PER_DAY
    }

    fun lastDoseFallsOnNextDay(startTimeMs: Long): Boolean {
        require(isValidStartTime(startTimeMs))
        return startTimeMs + (DOSES_PER_DAY - 1L) * MILLIS_PER_HOUR >= MILLIS_PER_DAY
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
