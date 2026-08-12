package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.timer

internal data class DeviceDosingTimerDose(
    val startTimeMs: Long,
    val amountMicroliters: Long
)

/** Typed UI boundary for up to 24 independent time-and-amount doses. */
internal object DeviceDosingTimerScheduleContract {
    const val RESULT_REQUEST_KEY = "dosing_timer_schedule_result"
    const val RESULT_KEY = "dosing_timer_schedule_result_state"
    const val RESULT_DOSES_DRAFT = "dosing_timer_schedule_doses_draft"
    const val RESULT_SLOT_ID = "dosing_timer_schedule_slot_id"
    const val RESULT_SAVED = "saved"

    const val MAX_DOSES_PER_DAY = 24
    const val MILLIS_PER_MINUTE = 60_000L
    const val MINUTES_PER_DAY = 24 * 60
    const val LAST_MINUTE_START_MS = (MINUTES_PER_DAY - 1) * MILLIS_PER_MINUTE

    enum class ValidationError {
        INVALID_DOSE,
        TOO_MANY_DOSES,
        DUPLICATE_TIME,
        TOTAL_OVERFLOW
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

    fun validate(doses: List<DeviceDosingTimerDose>): ValidationError? = when {
        doses.any { dose -> !isValidTimerDose(dose) } -> ValidationError.INVALID_DOSE
        doses.size > MAX_DOSES_PER_DAY -> ValidationError.TOO_MANY_DOSES
        hasDuplicateTimerTime(doses) -> ValidationError.DUPLICATE_TIME
        runCatching { totalDoseMicroliters(doses) }.isFailure -> ValidationError.TOTAL_OVERFLOW
        else -> null
    }

    fun normalize(doses: List<DeviceDosingTimerDose>): List<DeviceDosingTimerDose> {
        require(validate(doses) == null) { "Timer doses are invalid." }
        return doses.sortedBy(DeviceDosingTimerDose::startTimeMs)
    }

    fun totalDoseMicroliters(doses: List<DeviceDosingTimerDose>): Long =
        doses.fold(0L) { total, dose -> Math.addExact(total, dose.amountMicroliters) }

    fun encodeDraft(doses: List<DeviceDosingTimerDose>): String =
        normalize(doses).joinToString(DOSE_SEPARATOR) { dose ->
            listOf(dose.startTimeMs, dose.amountMicroliters).joinToString(FIELD_SEPARATOR)
        }

    fun decodeDraft(encoded: String): List<DeviceDosingTimerDose>? = when {
        encoded.isBlank() -> emptyList()
        else -> runCatching { decodeTimerDoses(encoded) }
            .getOrNull()
            ?.takeIf { doses -> validate(doses) == null }
            ?.let(::normalize)
    }

}

private fun isValidTimerDose(dose: DeviceDosingTimerDose): Boolean =
    DeviceDosingTimerScheduleContract.isValidTime(dose.startTimeMs) &&
        dose.amountMicroliters > 0L

private fun hasDuplicateTimerTime(doses: List<DeviceDosingTimerDose>): Boolean =
    doses.map(DeviceDosingTimerDose::startTimeMs).distinct().size != doses.size

private fun decodeTimerDoses(encoded: String): List<DeviceDosingTimerDose> =
    encoded.split(DOSE_SEPARATOR).map { entry ->
        val fields = entry.split(FIELD_SEPARATOR)
        require(fields.size == DOSE_FIELD_COUNT)
        DeviceDosingTimerDose(
            startTimeMs = fields[0].toLong(),
            amountMicroliters = fields[1].toLong()
        )
    }

private const val DOSE_SEPARATOR = ";"
private const val FIELD_SEPARATOR = ":"
private const val DOSE_FIELD_COUNT = 2
