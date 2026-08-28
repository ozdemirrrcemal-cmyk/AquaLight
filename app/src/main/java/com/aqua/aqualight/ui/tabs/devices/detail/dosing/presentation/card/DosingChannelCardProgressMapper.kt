package com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.card

import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingOccurrenceProgress
import com.aqua.aqualight.application.devices.dosing.DeviceDosingOccurrenceState
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgram
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgramSchedule
import com.aqua.aqualight.application.devices.dosing.DeviceDosingRunSource
import com.aqua.aqualight.application.devices.dosing.DeviceDosingScheduleState
import com.aqua.aqualight.application.devices.dosing.dailyDoseMicroliters
import com.aqua.aqualight.application.devices.dosing.nextScheduledOccurrence

internal fun DeviceDosingChannelSnapshot.toProgramProgressUiState(): DosingProgramProgressUiState {
    val completedProgramAmountMl = progress.completedAmountMicroliters.toMilliliters()
    val manualDeliveredTodayMl = usageToday.manualDeliveredMicroliters
        .takeIf { usageToday.valid }
        ?.toMilliliters()
        ?: 0.0
    val configuredProgram = program ?: return DosingProgramProgressUiState(
        manualDeliveredTodayMl = manualDeliveredTodayMl
    )
    val scheduledAmountTodayMl = progress.scheduledAmountMicroliters.toMilliliters()
    val occurrences = progress.occurrences
        .map(DeviceDosingOccurrenceProgress::toUiState)
        .withDoseFractions(scheduledAmountTodayMl)
    val customPeriods = configuredProgram.toCustomPeriodUiStates(
        compiledOccurrences = progress.occurrences,
        uiOccurrences = occurrences
    )
    val scheduledToday = configuredProgram.enabled &&
        progress.scheduleState == DeviceDosingScheduleState.ACTIVE &&
        progress.totalOccurrences > 0 &&
        progress.scheduledAmountMicroliters > 0L
    return DosingProgramProgressUiState(
        mode = configuredProgram.schedule.toUiMode(),
        dailyDoseMl = configuredProgram.dailyDoseMicroliters().toMilliliters(),
        scheduledAmountTodayMl = scheduledAmountTodayMl,
        scheduledDeliveredTodayMl = completedProgramAmountMl,
        remainingScheduledTodayMl = progress.remainingAmountMicroliters.toMilliliters(),
        completionFraction = (progress.completionPercent / PERCENT_SCALE)
            .toFloat()
            .coerceIn(0f, 1f),
        manualDeliveredTodayMl = manualDeliveredTodayMl,
        occurrences = occurrences,
        customPeriods = customPeriods,
        markers = configuredProgram.toProgressMarkers(
            occurrences = occurrences,
            totalAmountMl = scheduledAmountTodayMl
        ),
        nextDose = progress.nextScheduledOccurrence()?.toNextDoseUiState(),
        totalOccurrences = progress.totalOccurrences,
        completedOccurrences = progress.completedOccurrences,
        scheduledToday = scheduledToday,
        visualState = toProgressVisualState(configuredProgram, scheduledToday)
    )
}

private fun DeviceDosingChannelSnapshot.toProgressVisualState(
    configuredProgram: DeviceDosingProgram,
    scheduledToday: Boolean
): DosingDoseProgressVisualState = when {
    // Delivery uncertainty is rendered on the affected occurrence, not as a
    // channel-wide failure that implies a reservoir recovery action.
    hasAttentionState() -> DosingDoseProgressVisualState.ERROR
    !configuredProgram.enabled -> DosingDoseProgressVisualState.DISABLED
    !scheduledToday -> DosingDoseProgressVisualState.EMPTY
    activeRun.active && activeRun.source == DeviceDosingRunSource.SCHEDULED ->
        DosingDoseProgressVisualState.ACTIVE
    progress.completionPercent >= PERCENT_SCALE -> DosingDoseProgressVisualState.COMPLETE
    else -> DosingDoseProgressVisualState.READY
}

private fun DeviceDosingProgramSchedule.toUiMode(): DosingProgramModeUiState = when (this) {
    is DeviceDosingProgramSchedule.Single -> DosingProgramModeUiState.SINGLE
    is DeviceDosingProgramSchedule.Hourly24 -> DosingProgramModeUiState.HOURLY_24
    is DeviceDosingProgramSchedule.CustomPeriods -> DosingProgramModeUiState.CUSTOM_PERIODS
    is DeviceDosingProgramSchedule.Timer -> DosingProgramModeUiState.TIMER
}

private fun DeviceDosingOccurrenceProgress.toUiState() = DosingProgressOccurrenceUiState(
    amountMl = amountMicroliters.toMilliliters(),
    visualState = when (state) {
        DeviceDosingOccurrenceState.PENDING -> DosingOccurrenceVisualState.PENDING
        DeviceDosingOccurrenceState.RUNNING -> DosingOccurrenceVisualState.ACTIVE
        DeviceDosingOccurrenceState.COMPLETED -> DosingOccurrenceVisualState.COMPLETED
        DeviceDosingOccurrenceState.SKIPPED -> DosingOccurrenceVisualState.SKIPPED
        DeviceDosingOccurrenceState.UNCERTAIN -> DosingOccurrenceVisualState.UNCERTAIN
    }
)

private fun DeviceDosingOccurrenceProgress.toNextDoseUiState() = DosingNextDoseUiState(
    timeMillis = timeMillis,
    amountMl = amountMicroliters.toMilliliters(),
    programDayOffset = programDayOffset
)

private fun DeviceDosingProgram.toCustomPeriodUiStates(
    compiledOccurrences: List<DeviceDosingOccurrenceProgress>,
    uiOccurrences: List<DosingProgressOccurrenceUiState>
): List<DosingCustomPeriodProgressUiState> {
    val periods = (schedule as? DeviceDosingProgramSchedule.CustomPeriods)?.periods
    if (uiOccurrences.isEmpty() || periods == null) return emptyList()
    require(compiledOccurrences.size == uiOccurrences.size)

    // occurrence.index is firmware's canonical program-day identity. Activation-day progress may
    // expose only a suffix, so visible array position must never be used to choose a custom period.
    val visibleOccurrences = compiledOccurrences.zip(uiOccurrences)
    var canonicalStartIndex = 0
    return periods.map { period ->
        val canonicalEndIndex = canonicalStartIndex + period.doseCount
        val periodOccurrences = visibleOccurrences
            .filter { (compiledOccurrence, _) ->
                compiledOccurrence.index in canonicalStartIndex until canonicalEndIndex
            }
            .map { (_, uiOccurrence) -> uiOccurrence }
        canonicalStartIndex = canonicalEndIndex
        DosingCustomPeriodProgressUiState(occurrences = periodOccurrences)
    }
}

private const val PERCENT_SCALE = 100.0
