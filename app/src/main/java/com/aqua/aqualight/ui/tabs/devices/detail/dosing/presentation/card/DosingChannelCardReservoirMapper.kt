package com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.card

import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingReservoirSnapshot
import java.time.LocalDate

internal fun DeviceDosingChannelSnapshot.toReservoirUiState(
    today: LocalDate
): DosingReservoirUiState? {
    if (!reservoir.trackingEnabled) return null
    val estimatedDays = estimatedRemainingDays(today)
    return DosingReservoirUiState(
        remainingMl = reservoir.remainingMicroliters.toMilliliters(),
        fillFraction = reservoir.fillFraction(),
        estimatedRemainingDays = estimatedDays,
        tone = reservoir.toUiTone(deliveryAccountingCertain, estimatedDays)
    )
}

private fun DeviceDosingChannelSnapshot.estimatedRemainingDays(today: LocalDate): Int? {
    val configuredProgram = program
    val canEstimate = listOf(
        configuredProgram?.enabled == true,
        progress.executionCurrent,
        reservoir.accountingCertain,
        deliveryAccountingCertain
    ).all { valid -> valid }
    if (!canEstimate || configuredProgram == null) return null

    val remainingToday = (progress.scheduledAmountMicroliters -
        progress.completedAmountMicroliters).coerceAtLeast(0L)
    return DosingReservoirProjection.estimateRemainingDays(
        remainingMicroliters = reservoir.remainingMicroliters,
        dailyDoseMicroliters = configuredProgram.dailyDoseMicroliters(),
        remainingScheduledTodayMicroliters = remainingToday,
        selectedWeekdays = configuredProgram.weekdays,
        today = today
    )
}

private fun DeviceDosingReservoirSnapshot.fillFraction(): Float =
    capacityMicroliters.takeIf { capacity -> capacity > 0L }
        ?.let { capacity ->
            (remainingMicroliters.toDouble() / capacity).coerceIn(0.0, 1.0).toFloat()
        }
        ?: 0f

private fun DeviceDosingReservoirSnapshot.toUiTone(
    deliveryAccountingCertain: Boolean,
    estimatedDays: Int?
): DosingReservoirTone = when {
    !accountingCertain || !deliveryAccountingCertain -> DosingReservoirTone.UNCERTAIN
    estimatedDays != null && estimatedDays < CRITICAL_REMAINING_DAYS ->
        DosingReservoirTone.CRITICAL
    estimatedDays != null && estimatedDays <= WARNING_REMAINING_DAYS ->
        DosingReservoirTone.WARNING
    else -> DosingReservoirTone.NORMAL
}

private const val CRITICAL_REMAINING_DAYS = 10
private const val WARNING_REMAINING_DAYS = 20
