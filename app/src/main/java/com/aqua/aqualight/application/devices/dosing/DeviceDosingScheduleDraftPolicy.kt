package com.aqua.aqualight.application.devices.dosing

import java.math.BigDecimal

data class DeviceDosingCustomPeriodDraft(
    val startTimeMs: Long,
    val endTimeMs: Long,
    val doseCount: Int
)

data class DeviceDosingTimerDoseDraft(
    val startTimeMs: Long,
    val amountMicroliters: Long
)

enum class DeviceDosingCustomScheduleValidationError {
    INVALID_PERIOD,
    TOO_MANY_DOSES,
    OVERLAPPING_PERIODS
}

enum class DeviceDosingTimerScheduleValidationError {
    INVALID_DOSE,
    TOO_MANY_DOSES,
    DUPLICATE_TIME,
    TOTAL_OVERFLOW
}

/**
 * Product-level Dosing editor limits.
 *
 * These defaults are intentionally owned outside presentation. Per-channel firmware limits are
 * passed into the policies by the application boundary without changing validation ownership.
 */
object DeviceDosingScheduleDraftLimits {
    const val MAX_DOSES_PER_DAY = 24
    const val MICROLITERS_PER_MILLILITER = 1_000L
    const val MILLIS_PER_MINUTE = 60_000L
    const val MINUTES_PER_DAY = 24 * 60
    const val LAST_MINUTE_START_MS = (MINUTES_PER_DAY - 1) * MILLIS_PER_MINUTE
}

object DeviceDosingAmountDraftPolicy {
    fun exactMicroliters(milliliters: BigDecimal): Long? {
        if (milliliters.signum() <= 0) return null
        return runCatching {
            milliliters
                .multiply(BigDecimal.valueOf(DeviceDosingScheduleDraftLimits.MICROLITERS_PER_MILLILITER))
                .stripTrailingZeros()
                .longValueExact()
        }.getOrNull()?.takeIf { microliters -> microliters > 0L }
    }

    fun milliliters(microliters: Long): Double {
        require(microliters >= 0L) { "microliters must not be negative." }
        return microliters.toDouble() /
            DeviceDosingScheduleDraftLimits.MICROLITERS_PER_MILLILITER
    }
}

object DeviceDosingScheduleTimeDraftPolicy {
    fun startTimeMs(minutesOfDay: Int): Long {
        require(minutesOfDay in 0 until DeviceDosingScheduleDraftLimits.MINUTES_PER_DAY) {
            "minutesOfDay must be inside one day."
        }
        return minutesOfDay * DeviceDosingScheduleDraftLimits.MILLIS_PER_MINUTE
    }

    fun minutesOfDay(timeMs: Long): Int {
        require(isValidTime(timeMs)) {
            "timeMs must be minute-aligned inside one day."
        }
        return (timeMs / DeviceDosingScheduleDraftLimits.MILLIS_PER_MINUTE).toInt()
    }

    fun isValidTime(timeMs: Long): Boolean =
        timeMs in 0L..DeviceDosingScheduleDraftLimits.LAST_MINUTE_START_MS &&
            timeMs % DeviceDosingScheduleDraftLimits.MILLIS_PER_MINUTE == 0L
}

object DeviceDosingCustomScheduleDraftPolicy {
    fun validate(
        periods: List<DeviceDosingCustomPeriodDraft>,
        maxEventsPerChannel: Int = DeviceDosingScheduleDraftLimits.MAX_DOSES_PER_DAY,
        maxPeriodsPerChannel: Int = DeviceDosingScheduleDraftLimits.MAX_DOSES_PER_DAY
    ): DeviceDosingCustomScheduleValidationError? {
        require(maxEventsPerChannel > 0)
        require(maxPeriodsPerChannel > 0)
        return when {
            periods.any { period -> !isValidPeriod(period, maxEventsPerChannel) } ->
                DeviceDosingCustomScheduleValidationError.INVALID_PERIOD
            periods.size > maxPeriodsPerChannel || totalDoseCount(periods) > maxEventsPerChannel ->
                DeviceDosingCustomScheduleValidationError.TOO_MANY_DOSES
            periodsOverlap(periods) ->
                DeviceDosingCustomScheduleValidationError.OVERLAPPING_PERIODS
            else -> null
        }
    }

    fun normalize(
        periods: List<DeviceDosingCustomPeriodDraft>,
        maxEventsPerChannel: Int = DeviceDosingScheduleDraftLimits.MAX_DOSES_PER_DAY,
        maxPeriodsPerChannel: Int = DeviceDosingScheduleDraftLimits.MAX_DOSES_PER_DAY
    ): List<DeviceDosingCustomPeriodDraft> {
        require(
            validate(periods, maxEventsPerChannel, maxPeriodsPerChannel) == null
        ) { "Custom periods are invalid." }
        return periods.sortedBy(DeviceDosingCustomPeriodDraft::startTimeMs)
    }

    fun totalDoseCount(periods: List<DeviceDosingCustomPeriodDraft>): Int =
        periods.sumOf(DeviceDosingCustomPeriodDraft::doseCount)

    fun averageDoseMl(
        dailyDoseMicroliters: Long,
        periods: List<DeviceDosingCustomPeriodDraft>
    ): Double {
        require(dailyDoseMicroliters >= 0L) { "dailyDoseMicroliters must not be negative." }
        val count = totalDoseCount(periods)
        return if (count == 0) {
            0.0
        } else {
            DeviceDosingAmountDraftPolicy.milliliters(dailyDoseMicroliters) / count
        }
    }

    private fun isValidPeriod(
        period: DeviceDosingCustomPeriodDraft,
        maxEventsPerChannel: Int
    ): Boolean =
        DeviceDosingScheduleTimeDraftPolicy.isValidTime(period.startTimeMs) &&
            DeviceDosingScheduleTimeDraftPolicy.isValidTime(period.endTimeMs) &&
            period.endTimeMs > period.startTimeMs &&
            period.doseCount in 1..maxEventsPerChannel

    private fun periodsOverlap(periods: List<DeviceDosingCustomPeriodDraft>): Boolean = periods
        .sortedBy(DeviceDosingCustomPeriodDraft::startTimeMs)
        .zipWithNext()
        .any { (first, second) -> second.startTimeMs <= first.endTimeMs }
}

object DeviceDosingTimerScheduleDraftPolicy {
    fun validate(
        doses: List<DeviceDosingTimerDoseDraft>,
        maxEventsPerChannel: Int = DeviceDosingScheduleDraftLimits.MAX_DOSES_PER_DAY
    ): DeviceDosingTimerScheduleValidationError? {
        require(maxEventsPerChannel > 0)
        return when {
            doses.any { dose -> !isValidDose(dose) } ->
                DeviceDosingTimerScheduleValidationError.INVALID_DOSE
            doses.size > maxEventsPerChannel ->
                DeviceDosingTimerScheduleValidationError.TOO_MANY_DOSES
            hasDuplicateTime(doses) ->
                DeviceDosingTimerScheduleValidationError.DUPLICATE_TIME
            runCatching { totalDoseMicroliters(doses) }.isFailure ->
                DeviceDosingTimerScheduleValidationError.TOTAL_OVERFLOW
            else -> null
        }
    }

    fun normalize(
        doses: List<DeviceDosingTimerDoseDraft>,
        maxEventsPerChannel: Int = DeviceDosingScheduleDraftLimits.MAX_DOSES_PER_DAY
    ): List<DeviceDosingTimerDoseDraft> {
        require(validate(doses, maxEventsPerChannel) == null) { "Timer doses are invalid." }
        return doses.sortedBy(DeviceDosingTimerDoseDraft::startTimeMs)
    }

    fun totalDoseMicroliters(doses: List<DeviceDosingTimerDoseDraft>): Long =
        doses.fold(0L) { total, dose -> Math.addExact(total, dose.amountMicroliters) }

    private fun isValidDose(dose: DeviceDosingTimerDoseDraft): Boolean =
        DeviceDosingScheduleTimeDraftPolicy.isValidTime(dose.startTimeMs) &&
            dose.amountMicroliters > 0L

    private fun hasDuplicateTime(doses: List<DeviceDosingTimerDoseDraft>): Boolean =
        doses.map(DeviceDosingTimerDoseDraft::startTimeMs).distinct().size != doses.size
}
