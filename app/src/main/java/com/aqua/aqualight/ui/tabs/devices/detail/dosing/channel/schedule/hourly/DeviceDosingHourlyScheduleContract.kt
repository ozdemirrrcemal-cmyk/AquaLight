package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.hourly

/** Typed UI boundary for firmware Hourly24: exactly one minute shared by all 24 local hours. */
internal object DeviceDosingHourlyScheduleContract {
    const val RESULT_REQUEST_KEY = "dosing_hourly_schedule_result"
    const val RESULT_KEY = "dosing_hourly_schedule_result_state"
    const val RESULT_MINUTE_OF_HOUR = "dosing_hourly_schedule_minute_of_hour"
    const val RESULT_SLOT_ID = "dosing_hourly_schedule_slot_id"
    const val RESULT_SAVED = "saved"

    const val DOSES_PER_DAY = 24
    const val MINUTES_PER_HOUR = 60

    fun isValidMinuteOfHour(minuteOfHour: Int): Boolean =
        minuteOfHour in 0 until MINUTES_PER_HOUR

    fun firstDoseMinutesOfDay(minuteOfHour: Int): Int {
        require(isValidMinuteOfHour(minuteOfHour)) {
            "Hourly minuteOfHour must be between 0 and 59."
        }
        return minuteOfHour
    }

    fun lastDoseMinutesOfDay(minuteOfHour: Int): Int {
        require(isValidMinuteOfHour(minuteOfHour)) {
            "Hourly minuteOfHour must be between 0 and 59."
        }
        return (DOSES_PER_DAY - 1) * MINUTES_PER_HOUR + minuteOfHour
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
