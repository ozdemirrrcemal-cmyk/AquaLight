package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.timer

import com.aqua.aqualight.application.devices.dosing.DeviceDosingScheduleDraftLimits
import com.aqua.aqualight.application.devices.dosing.DeviceDosingScheduleTimeDraftPolicy
import com.aqua.aqualight.application.devices.dosing.DeviceDosingTimerDoseDraft
import com.aqua.aqualight.application.devices.dosing.DeviceDosingTimerScheduleDraftPolicy
import com.aqua.aqualight.application.devices.dosing.DeviceDosingTimerScheduleValidationError

internal typealias DeviceDosingTimerDose = DeviceDosingTimerDoseDraft

internal data class DeviceDosingTimerEditorPayload(
    val doses: List<DeviceDosingTimerDose>,
    val maxDoseCount: Int
)

/** UI transport boundary; firmware-published event capacity is supplied by the Plan owner. */
internal object DeviceDosingTimerScheduleContract {
    const val RESULT_REQUEST_KEY = "dosing_timer_schedule_result"
    const val RESULT_KEY = "dosing_timer_schedule_result_state"
    const val RESULT_DOSES_DRAFT = "dosing_timer_schedule_doses_draft"
    const val RESULT_SLOT_ID = "dosing_timer_schedule_slot_id"
    const val RESULT_SAVED = "saved"

    const val MILLIS_PER_MINUTE = DeviceDosingScheduleDraftLimits.MILLIS_PER_MINUTE
    const val MINUTES_PER_DAY = DeviceDosingScheduleDraftLimits.MINUTES_PER_DAY
    const val LAST_MINUTE_START_MS = DeviceDosingScheduleDraftLimits.LAST_MINUTE_START_MS

    enum class ValidationError { INVALID_DOSE, TOO_MANY_DOSES, DUPLICATE_TIME, TOTAL_OVERFLOW }

    fun startTimeMs(minutesOfDay: Int): Long =
        DeviceDosingScheduleTimeDraftPolicy.startTimeMs(minutesOfDay)

    fun minutesOfDay(timeMs: Long): Int =
        DeviceDosingScheduleTimeDraftPolicy.minutesOfDay(timeMs)

    fun isValidTime(timeMs: Long): Boolean =
        DeviceDosingScheduleTimeDraftPolicy.isValidTime(timeMs)

    fun validate(doses: List<DeviceDosingTimerDose>, maxDoseCount: Int): ValidationError? =
        DeviceDosingTimerScheduleDraftPolicy.validate(doses, maxDoseCount)?.toUiError()

    fun normalize(doses: List<DeviceDosingTimerDose>): List<DeviceDosingTimerDose> =
        DeviceDosingTimerScheduleDraftPolicy.normalize(doses)

    fun totalDoseMicroliters(doses: List<DeviceDosingTimerDose>): Long =
        DeviceDosingTimerScheduleDraftPolicy.totalDoseMicroliters(doses)

    fun encodeEditorPayload(doses: List<DeviceDosingTimerDose>, maxDoseCount: Int): String {
        require(maxDoseCount > 0)
        return maxDoseCount.toString() + ENVELOPE_SEPARATOR + encodeDraft(doses)
    }

    fun decodeEditorPayload(encoded: String): DeviceDosingTimerEditorPayload? = runCatching {
        val envelope = encoded.split(ENVELOPE_SEPARATOR, limit = 2)
        require(envelope.size == 2)
        val maxDoseCount = envelope[0].toInt().also { require(it > 0) }
        val doses = decodeDraft(envelope[1]) ?: error("Invalid Timer-mode Dosing draft.")
        require(validate(doses, maxDoseCount) == null)
        DeviceDosingTimerEditorPayload(doses, maxDoseCount)
    }.getOrNull()

    fun encodeDraft(doses: List<DeviceDosingTimerDose>): String =
        normalize(doses).joinToString(DOSE_SEPARATOR) { dose ->
            listOf(dose.startTimeMs, dose.amountMicroliters).joinToString(FIELD_SEPARATOR)
        }

    fun decodeDraft(encoded: String): List<DeviceDosingTimerDose>? = when {
        encoded.isBlank() -> emptyList()
        else -> runCatching { decodeTimerDoses(encoded) }.getOrNull()?.let(::normalize)
    }
}

private fun DeviceDosingTimerScheduleValidationError.toUiError() = when (this) {
    DeviceDosingTimerScheduleValidationError.INVALID_DOSE ->
        DeviceDosingTimerScheduleContract.ValidationError.INVALID_DOSE
    DeviceDosingTimerScheduleValidationError.TOO_MANY_DOSES ->
        DeviceDosingTimerScheduleContract.ValidationError.TOO_MANY_DOSES
    DeviceDosingTimerScheduleValidationError.DUPLICATE_TIME ->
        DeviceDosingTimerScheduleContract.ValidationError.DUPLICATE_TIME
    DeviceDosingTimerScheduleValidationError.TOTAL_OVERFLOW ->
        DeviceDosingTimerScheduleContract.ValidationError.TOTAL_OVERFLOW
}

private fun decodeTimerDoses(encoded: String): List<DeviceDosingTimerDose> =
    encoded.split(DOSE_SEPARATOR).map { entry ->
        val fields = entry.split(FIELD_SEPARATOR)
        require(fields.size == DOSE_FIELD_COUNT)
        DeviceDosingTimerDose(fields[0].toLong(), fields[1].toLong())
    }

private const val ENVELOPE_SEPARATOR = "|"
private const val DOSE_SEPARATOR = ";"
private const val FIELD_SEPARATOR = ":"
private const val DOSE_FIELD_COUNT = 2
