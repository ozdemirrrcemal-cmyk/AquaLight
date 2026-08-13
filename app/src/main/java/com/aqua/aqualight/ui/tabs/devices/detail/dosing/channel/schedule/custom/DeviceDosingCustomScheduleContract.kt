package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.custom

import com.aqua.aqualight.application.devices.dosing.DeviceDosingCustomPeriodDraft
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCustomScheduleDraftPolicy
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCustomScheduleValidationError
import com.aqua.aqualight.application.devices.dosing.DeviceDosingScheduleDraftLimits
import com.aqua.aqualight.application.devices.dosing.DeviceDosingScheduleTimeDraftPolicy

internal typealias DeviceDosingCustomPeriod = DeviceDosingCustomPeriodDraft

internal data class DeviceDosingCustomEditorPayload(
    val periods: List<DeviceDosingCustomPeriod>,
    val maxPeriods: Int,
    val maxDoseCount: Int
)

/** UI transport boundary; firmware-published capacities are supplied by the Plan owner. */
internal object DeviceDosingCustomScheduleContract {
    const val RESULT_REQUEST_KEY = "dosing_custom_schedule_result"
    const val RESULT_KEY = "dosing_custom_schedule_result_state"
    const val RESULT_PERIODS_DRAFT = "dosing_custom_schedule_periods_draft"
    const val RESULT_SLOT_ID = "dosing_custom_schedule_slot_id"
    const val RESULT_SAVED = "saved"

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

    fun validate(
        periods: List<DeviceDosingCustomPeriod>,
        maxPeriods: Int,
        maxDoseCount: Int
    ): ValidationError? = DeviceDosingCustomScheduleDraftPolicy
        .validate(periods, maxPeriods, maxDoseCount)
        ?.toUiError()

    fun normalize(periods: List<DeviceDosingCustomPeriod>): List<DeviceDosingCustomPeriod> =
        DeviceDosingCustomScheduleDraftPolicy.normalize(periods)

    fun totalDoseCount(periods: List<DeviceDosingCustomPeriod>): Int =
        DeviceDosingCustomScheduleDraftPolicy.totalDoseCount(periods)

    fun averageDoseMl(dailyDoseMicroliters: Long, periods: List<DeviceDosingCustomPeriod>): Double =
        DeviceDosingCustomScheduleDraftPolicy.averageDoseMl(dailyDoseMicroliters, periods)

    fun encodeEditorPayload(
        periods: List<DeviceDosingCustomPeriod>,
        maxPeriods: Int,
        maxDoseCount: Int
    ): String {
        require(maxPeriods > 0 && maxDoseCount > 0)
        return listOf(maxPeriods, maxDoseCount).joinToString(LIMIT_SEPARATOR) +
            ENVELOPE_SEPARATOR + encodeDraft(periods)
    }

    fun decodeEditorPayload(encoded: String): DeviceDosingCustomEditorPayload? = runCatching {
        val envelope = encoded.split(ENVELOPE_SEPARATOR, limit = 2)
        require(envelope.size == 2)
        val limits = envelope[0].split(LIMIT_SEPARATOR)
        require(limits.size == 2)
        val maxPeriods = limits[0].toInt().also { require(it > 0) }
        val maxDoseCount = limits[1].toInt().also { require(it > 0) }
        val periods = decodeDraft(envelope[1]) ?: error("Invalid custom Dosing draft.")
        require(validate(periods, maxPeriods, maxDoseCount) == null)
        DeviceDosingCustomEditorPayload(periods, maxPeriods, maxDoseCount)
    }.getOrNull()

    fun encodeDraft(periods: List<DeviceDosingCustomPeriod>): String =
        normalize(periods).joinToString(PERIOD_SEPARATOR) { period ->
            listOf(period.startTimeMs, period.endTimeMs, period.doseCount).joinToString(FIELD_SEPARATOR)
        }

    fun decodeDraft(encoded: String): List<DeviceDosingCustomPeriod>? = when {
        encoded.isBlank() -> emptyList()
        else -> runCatching { decodeCustomPeriods(encoded) }.getOrNull()?.let(::normalize)
    }
}

private fun DeviceDosingCustomScheduleValidationError.toUiError() = when (this) {
    DeviceDosingCustomScheduleValidationError.INVALID_PERIOD ->
        DeviceDosingCustomScheduleContract.ValidationError.INVALID_PERIOD
    DeviceDosingCustomScheduleValidationError.TOO_MANY_PERIODS,
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

private const val ENVELOPE_SEPARATOR = "|"
private const val LIMIT_SEPARATOR = ","
private const val PERIOD_SEPARATOR = ";"
private const val FIELD_SEPARATOR = ":"
private const val PERIOD_FIELD_COUNT = 3
