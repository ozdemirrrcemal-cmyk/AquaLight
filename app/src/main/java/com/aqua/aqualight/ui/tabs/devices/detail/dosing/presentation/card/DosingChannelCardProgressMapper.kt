package com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.card

import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingOccurrenceProgress
import com.aqua.aqualight.application.devices.dosing.DeviceDosingOccurrenceState
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgram
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgramSchedule
import com.aqua.aqualight.application.devices.dosing.DeviceDosingRunSource
import com.aqua.aqualight.application.devices.dosing.dailyDoseMicroliters

internal fun DeviceDosingChannelSnapshot.toProgramProgressUiState(): DosingProgramProgressUiState {
    val manualDeliveredTodayMl = usageToday.manualDeliveredMicroliters
        .takeIf { usageToday.valid }
        ?.toMilliliters()
        ?: 0.0
    val configuredProgram = program ?: return DosingProgramProgressUiState(
        manualDeliveredTodayMl = manualDeliveredTodayMl
    )
    val occurrences = if (progress.executionCurrent) {
        progress.occurrences
            .map(DeviceDosingOccurrenceProgress::toUiState)
            .withDoseFractions()
    } else {
        emptyList()
    }
    val customPeriods = configuredProgram.toCustomPeriodUiStates(occurrences)
    return DosingProgramProgressUiState(
        mode = configuredProgram.schedule.toUiMode(),
        dailyDoseMl = configuredProgram.dailyDoseMicroliters().toMilliliters(),
        scheduledDeliveredTodayMl = progress.completedAmountMicroliters.toMilliliters(),
        manualDeliveredTodayMl = manualDeliveredTodayMl,
        occurrences = occurrences,
        customPeriods = customPeriods,
        markers = configuredProgram.toProgressMarkers(occurrences, customPeriods),
        scheduledToday = configuredProgram.enabled &&
            progress.executionCurrent &&
            progress.scheduledAmountMicroliters > 0L,
        visualState = toProgressVisualState(configuredProgram)
    )
}

private fun DeviceDosingChannelSnapshot.toProgressVisualState(
    configuredProgram: DeviceDosingProgram
): DosingDoseProgressVisualState = when {
    hasAttentionState() || !progress.accountingCertain -> DosingDoseProgressVisualState.ERROR
    !configuredProgram.enabled -> DosingDoseProgressVisualState.DISABLED
    !progress.executionCurrent || progress.scheduledAmountMicroliters <= 0L ->
        DosingDoseProgressVisualState.EMPTY
    activeRun.active && activeRun.source == DeviceDosingRunSource.SCHEDULED ->
        DosingDoseProgressVisualState.ACTIVE
    progress.completedAmountMicroliters >= progress.scheduledAmountMicroliters ->
        DosingDoseProgressVisualState.COMPLETE
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

private fun DeviceDosingProgram.toCustomPeriodUiStates(
    occurrences: List<DosingProgressOccurrenceUiState>
): List<DosingCustomPeriodProgressUiState> {
    val periods = (schedule as? DeviceDosingProgramSchedule.CustomPeriods)?.periods
    if (occurrences.isEmpty() || periods == null) return emptyList()
    var cursor = 0
    return periods.map { period ->
        DosingCustomPeriodProgressUiState(
            occurrences = occurrences.drop(cursor).take(period.doseCount)
        ).also { cursor += period.doseCount }
    }
}
