package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.custom

import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.DeviceDosingScheduleAmountContract

internal data class DeviceDosingCustomPeriod(
    val startTimeMs: Long,
    val endTimeMs: Long,
    val doseCount: Int
)

/** Typed UI boundary for a daily amount distributed across explicit time periods. */
internal object DeviceDosingCustomScheduleContract {
    const val RESULT_REQUEST_KEY = "dosing_custom_schedule_result"
    const val RESULT_KEY = "dosing_custom_schedule_result_state"
    const val RESULT_PERIODS_DRAFT = "dosing_custom_schedule_periods_draft"
    const val RESULT_SLOT_ID = "dosing_custom_schedule_slot_id"
    const val RESULT_SAVED = "saved"

    const val MAX_DOSES_PER_DAY = 24
    const val MILLIS_PER_MINUTE = 60_000L
    const val MINUTES_PER_DAY = 24 * 60
    const val LAST_MINUTE_START_MS = (MINUTES_PER_DAY - 1) * MILLIS_PER_MINUTE

    enum class ValidationError {
        INVALID_PERIOD,
        TOO_MANY_DOSES,
        OVERLAPPING_PERIODS
    }

    fun startTimeMs(minutesOfDay: Int): Long {
        require(minutesOfDay in 0 until MINUTES_PER_DAY) {
            "minutesOfDay must be inside one day."
        }
        return minutesOfDay * MILLIS_PER_MINUTE
    }

    fun minutesOfDay(timeMs: Long): Int {
        require(isValidTime(timeMs)) { "timeMs must be minute-aligned inside one day." }
        return (timeMs / MILLIS_PER_MINUTE).toInt()
    }

    fun isValidTime(timeMs: Long): Boolean =
        timeMs in 0L..LAST_MINUTE_START_MS && timeMs % MILLIS_PER_MINUTE == 0L

    fun validate(periods: List<DeviceDosingCustomPeriod>): ValidationError? = when {
        periods.any { period -> !isValidCustomPeriod(period) } -> ValidationError.INVALID_PERIOD
        totalDoseCount(periods) > MAX_DOSES_PER_DAY -> ValidationError.TOO_MANY_DOSES
        customPeriodsOverlap(periods) -> ValidationError.OVERLAPPING_PERIODS
        else -> null
    }

    fun normalize(periods: List<DeviceDosingCustomPeriod>): List<DeviceDosingCustomPeriod> {
        require(validate(periods) == null) { "Custom periods are invalid." }
        return periods.sortedBy(DeviceDosingCustomPeriod::startTimeMs)
    }

    fun totalDoseCount(periods: List<DeviceDosingCustomPeriod>): Int =
        periods.sumOf(DeviceDosingCustomPeriod::doseCount)

    fun averageDoseMl(
        dailyDoseMicroliters: Long,
        periods: List<DeviceDosingCustomPeriod>
    ): Double {
        require(dailyDoseMicroliters >= 0L) { "dailyDoseMicroliters must not be negative." }
        val count = totalDoseCount(periods)
        return if (count == 0) {
            0.0
        } else {
            DeviceDosingScheduleAmountContract.milliliters(dailyDoseMicroliters) / count
        }
    }

    fun encodeDraft(periods: List<DeviceDosingCustomPeriod>): String =
        normalize(periods).joinToString(PERIOD_SEPARATOR) { period ->
            listOf(period.startTimeMs, period.endTimeMs, period.doseCount)
                .joinToString(FIELD_SEPARATOR)
        }

    fun decodeDraft(encoded: String): List<DeviceDosingCustomPeriod>? = when {
        encoded.isBlank() -> emptyList()
        else -> runCatching { decodeCustomPeriods(encoded) }
            .getOrNull()
            ?.takeIf { periods -> validate(periods) == null }
            ?.let(::normalize)
    }

}

private fun isValidCustomPeriod(period: DeviceDosingCustomPeriod): Boolean =
    hasValidCustomTimeRange(period) &&
        period.doseCount in 1..DeviceDosingCustomScheduleContract.MAX_DOSES_PER_DAY

private fun hasValidCustomTimeRange(period: DeviceDosingCustomPeriod): Boolean =
    DeviceDosingCustomScheduleContract.isValidTime(period.startTimeMs) &&
        DeviceDosingCustomScheduleContract.isValidTime(period.endTimeMs) &&
        period.endTimeMs > period.startTimeMs

private fun customPeriodsOverlap(periods: List<DeviceDosingCustomPeriod>): Boolean = periods
    .sortedBy(DeviceDosingCustomPeriod::startTimeMs)
    .zipWithNext()
    .any { (first, second) -> second.startTimeMs <= first.endTimeMs }

private fun decodeCustomPeriods(encoded: String): List<DeviceDosingCustomPeriod> =
    encoded.split(PERIOD_SEPARATOR).map { entry ->
        val fields = entry.split(FIELD_SEPARATOR)
        require(fields.size == PERIOD_FIELD_COUNT)
        DeviceDosingCustomPeriod(
            startTimeMs = fields[0].toLong(),
            endTimeMs = fields[1].toLong(),
            doseCount = fields[2].toInt()
        )
    }

private const val PERIOD_SEPARATOR = ";"
private const val FIELD_SEPARATOR = ":"
private const val PERIOD_FIELD_COUNT = 3
