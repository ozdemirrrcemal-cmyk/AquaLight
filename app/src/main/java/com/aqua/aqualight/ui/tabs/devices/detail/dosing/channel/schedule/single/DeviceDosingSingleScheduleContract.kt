package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.single

internal object DeviceDosingSingleScheduleContract {
    const val RESULT_REQUEST_KEY = "dosing_single_schedule_result"
    const val RESULT_KEY = "dosing_single_schedule_result_state"
    const val RESULT_START_TIME_MS = "dosing_single_schedule_start_time_ms"
    const val RESULT_SLOT_ID = "dosing_single_schedule_slot_id"
    const val RESULT_SAVED = "saved"

    const val MILLIS_PER_MINUTE = 60_000L
    const val MINUTES_PER_DAY = 24 * 60
    const val LAST_MILLISECOND_OF_DAY = 86_399_999L

    fun isValidStartTime(startTimeMs: Long): Boolean =
        startTimeMs in 0L..LAST_MILLISECOND_OF_DAY

    fun minuteAlignedStartTime(startTimeMs: Long): Long {
        require(isValidStartTime(startTimeMs)) { "startTimeMs must be inside one day." }
        return startTimeMs / MILLIS_PER_MINUTE * MILLIS_PER_MINUTE
    }

    fun minutesOfDay(startTimeMs: Long): Int =
        (minuteAlignedStartTime(startTimeMs) / MILLIS_PER_MINUTE).toInt()

    fun startTimeMs(minutesOfDay: Int): Long {
        require(minutesOfDay in 0 until MINUTES_PER_DAY) {
            "minutesOfDay must be inside one day."
        }
        return minutesOfDay * MILLIS_PER_MINUTE
    }

    fun dailyDoseMl(dailyDoseMicroliters: Long): Double {
        require(dailyDoseMicroliters >= 0L) { "dailyDoseMicroliters must not be negative." }
        return dailyDoseMicroliters.toDouble() / MICROLITERS_PER_MILLILITER
    }

    private const val MICROLITERS_PER_MILLILITER = 1_000.0
}
