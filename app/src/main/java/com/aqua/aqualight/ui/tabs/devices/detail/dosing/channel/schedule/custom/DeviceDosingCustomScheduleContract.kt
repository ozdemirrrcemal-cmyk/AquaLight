package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.custom

import com.aqua.aqualight.application.devices.DeviceDosingCustomPeriodDraft
import com.aqua.aqualight.application.devices.DeviceDosingCustomScheduleDraftPolicy
import com.aqua.aqualight.application.devices.DeviceDosingCustomScheduleValidationError
import com.aqua.aqualight.application.devices.DeviceDosingScheduleDraftLimits
import com.aqua.aqualight.application.devices.DeviceDosingScheduleTimeDraftPolicy

internal typealias DeviceDosingCustomPeriod = DeviceDosingCustomPeriodDraft

/** UI transport boundary; schedule validity is owned by application policy. */
internal object DeviceDosingCustomScheduleContract {
    const val RESULT_REQUEST_KEY = "dosing_custom_schedule_result"
    const val RESULT_KEY = "dosing_custom_schedule_result_state"
    const val RESULT_PERIODS_DRAFT = "dosing_custom_schedule_periods_draft"
    const val RESULT_SLOT_ID = "dosing_custom_schedule_slot_id"
    const val RESULT_SAVED = "saved"

    const val MAX_DOSES_PER_DAY = DeviceDosingScheduleDraftLimits.MAX_DOSES_PER_DAY
    const val MILLIS_PER_MINUTE = DeviceDosingScheduleDraftLimits.MILLIS_PER_MINUTE
    const val MINUTES_PER_DAY = DeviceDosingScheduleDraftLimits.MINUTES_PER_DAY
    const val LAST_MINUTE_START_MS = DeviceDosingScheduleDraftLimits.LAST_MINUTE_START_MS

    enum class ValidationError { INVALID_PERIOD, TOO_MANY_DOSES, OVERLAPPING_PERIODS }

    fun startTimeMs(minutesOfDay: Int): Long =
        DeviceDosingScheduleTimeDraftPolicy.startTimeMs(minutesOfDay)

    fun minutesOfDay(timeMs: Long): Int =
        DeviceDosingScheduleTimeDraftPolicy.minutesOfDay(timeMs)

    fun isValidTime(timeMs: Long): Boolean =
        DeviceDosingScheduleTimeDraftPolicy.isValidTime(timeMs)

    fun validate(periods: List<DeviceDosingCustomPeriod>): ValidationError? =
        DeviceDosingCustomScheduleDraftPolicy.validate(periods)?.toUiError()

    fun normalize(periods: List<DeviceDosingCustomPeriod>): List<DeviceDosingCustomPeriod> =
        DeviceDosingCustomScheduleDraftPolicy.normalize(periods)

    fun totalDoseCount(periods: List<DeviceDosingCustomPeriod>): Int =
        DeviceDosingCustomScheduleDraftPolicy.totalDoseCount(periods)

    fun averageDoseMl(dailyDoseMicroliters: Long, periods: List<DeviceDosingCustomPeriod>): Double =
        DeviceDosingCustomScheduleDraftPolicy.averageDoseMl(dailyDoseMicroliters, periods)

    fun encodeDraft(periods: List<DeviceDosingCustomPeriod>): String =
        normalize(periods).joinToString(PERIOD_SEPARATOR) { period ->
            listOf(period.startTimeMs, period.endTimeMs, period.doseCount).joinToString(FIELD_SEPARATOR)
        }

    fun decodeDraft(encoded: String): List<DeviceDosingCustomPeriod>? = when {
        encoded.isBlank() -> emptyList()
        else -> runCatching { decodeCustomPeriods(encoded) }.getOrNull()
            ?.takeIf { periods -> validate(periods) == null }
            ?.let(::normalize)
    }
}

private fun DeviceDosingCustomScheduleValidationError.toUiError() = when (this) {
    DeviceDosingCustomScheduleValidationError.INVALID_PERIOD ->
        DeviceDosingCustomScheduleContract.ValidationError.INVALID_PERIOD
    DeviceDosingCustomScheduleValidationError.TOO_MANY_DOSES ->
        DeviceDosingCustomScheduleContract.ValidationError.TOO_MANY_DOSES
    DeviceDosingCustomScheduleValidationError.OVERLAPPING_PERIODS ->
        DeviceDosingCustomScheduleContract.ValidationError.OVERLAPPING_PERIODS
}

private fun decodeCustomPeriods(encoded: String): List<DeviceDosingCustomPeriod> =
    encoded.split(PERIOD_SEPARATOR).map { entry ->
        val fields = entry.split(FIELD_SEPARATOR)
        require(fields.size == PERIOD_FIELD_COUNT)
        DeviceDosingCustomPeriod(fields[0].toLong(), fields[1].toLong(), fields[2].toInt())
    }

private const val PERIOD_SEPARATOR = ";"
private const val FIELD_SEPARATOR = ":"
private const val PERIOD_FIELD_COUNT = 3
