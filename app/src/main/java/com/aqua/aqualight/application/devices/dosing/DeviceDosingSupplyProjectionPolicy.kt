package com.aqua.aqualight.application.devices.dosing

import java.time.LocalDate

/**
 * Application-owned supply projection for a firmware-authoritative reservoir snapshot.
 *
 * Supply severity is a product forecast. It is deliberately independent from the firmware
 * low-level alarm, which remains the only authoritative alarm signal.
 */
object DeviceDosingSupplyProjectionPolicy {

    fun evaluate(snapshot: DeviceDosingChannelSnapshot): DeviceDosingSupplyProjection? {
        val reservoir = snapshot.reservoir
        if (!reservoir.trackingEnabled) return null

        // Firmware's certain remaining value is already conservative after an
        // interrupted dose; exact daily-delivery certainty is not a second
        // reservoir authority and must not suppress this supply projection.
        val remainingDays = snapshot.estimatedRemainingDays()
        val supplySeverity = when {
            !reservoir.accountingCertain ->
                DeviceDosingSupplySeverity.UNCERTAIN
            remainingDays != null && remainingDays < CRITICAL_REMAINING_DAYS ->
                DeviceDosingSupplySeverity.CRITICAL
            remainingDays != null && remainingDays <= WARNING_REMAINING_DAYS ->
                DeviceDosingSupplySeverity.WARNING
            else -> DeviceDosingSupplySeverity.NORMAL
        }
        return DeviceDosingSupplyProjection(
            estimatedRemainingDays = remainingDays,
            supplySeverity = supplySeverity
        )
    }

    private fun DeviceDosingChannelSnapshot.estimatedRemainingDays(): Int? {
        val configuredProgram = program ?: return null
        val canEstimate = listOf(
            configuredProgram.enabled,
            progress.executionCurrent,
            reservoir.accountingCertain
        ).all { valid -> valid }
        val dailyDoseMicroliters = configuredProgram.dailyDoseMicroliters()
        val programDayDate = progress.programDayDate
        return when {
            !canEstimate -> null
            programDayDate == null -> null
            dailyDoseMicroliters <= 0L -> null
            configuredProgram.weekdays.none { selected -> selected } -> null
            else -> estimateRemainingDays(
                remainingMicroliters = reservoir.remainingMicroliters,
                dailyDoseMicroliters = dailyDoseMicroliters,
                remainingScheduledTodayMicroliters =
                    (progress.scheduledAmountMicroliters - progress.completedAmountMicroliters)
                        .coerceAtLeast(0L),
                selectedWeekdays = configuredProgram.weekdays,
                programDayDate = programDayDate
            )
        }
    }

    private fun estimateRemainingDays(
        remainingMicroliters: Long,
        dailyDoseMicroliters: Long,
        remainingScheduledTodayMicroliters: Long,
        selectedWeekdays: List<Boolean>,
        programDayDate: LocalDate
    ): Int {
        var availableMicroliters = remainingMicroliters
        return (0..MAX_PROJECTION_DAYS).firstOrNull { dayOffset ->
            val plannedMicroliters = when {
                dayOffset == 0 -> remainingScheduledTodayMicroliters
                selectedWeekdays[
                    programDayDate.plusDays(dayOffset.toLong()).dayOfWeek.value - 1
                ] -> dailyDoseMicroliters
                else -> 0L
            }
            val exhausted = plannedMicroliters > availableMicroliters
            if (!exhausted) availableMicroliters -= plannedMicroliters
            exhausted
        } ?: MAX_PROJECTION_DAYS
    }

    private const val CRITICAL_REMAINING_DAYS = 10
    private const val WARNING_REMAINING_DAYS = 20
    private const val MAX_PROJECTION_DAYS = 3_650
}

data class DeviceDosingSupplyProjection(
    val estimatedRemainingDays: Int?,
    val supplySeverity: DeviceDosingSupplySeverity
)

enum class DeviceDosingSupplySeverity {
    NORMAL,
    WARNING,
    CRITICAL,
    UNCERTAIN
}

fun DeviceDosingProgram.dailyDoseMicroliters(): Long = when (val value = schedule) {
    is DeviceDosingProgramSchedule.Single -> value.dailyDoseMicroliters
    is DeviceDosingProgramSchedule.Hourly24 -> value.dailyDoseMicroliters
    is DeviceDosingProgramSchedule.CustomPeriods -> value.dailyDoseMicroliters
    is DeviceDosingProgramSchedule.Timer -> value.doses.sumOf { dose -> dose.amountMicroliters }
}
