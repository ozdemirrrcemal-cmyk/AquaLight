package com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.card

import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCustomPeriodDraft
import com.aqua.aqualight.application.devices.dosing.DeviceDosingOccurrenceProgress
import com.aqua.aqualight.application.devices.dosing.DeviceDosingOccurrenceState
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgram
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgramSchedule
import com.aqua.aqualight.application.devices.dosing.DeviceDosingRunSource
import java.time.LocalDate

internal fun DeviceDosingChannelSnapshot.toProgramProgressUiState(
    today: LocalDate
): DosingProgramProgressUiState {
    val configuredProgram = program ?: return DosingProgramProgressUiState()
    val selectedToday = configuredProgram.weekdays.getOrNull(today.dayOfWeek.value - 1) == true
    val currentOccurrences = if (progress.executionCurrent) {
        progress.occurrences.map(DeviceDosingOccurrenceProgress::toUiState)
    } else {
        emptyList()
    }
    val occurrences = when {
        currentOccurrences.isNotEmpty() -> currentOccurrences
        !configuredProgram.enabled && selectedToday -> configuredProgram.placeholderOccurrences()
        else -> emptyList()
    }.withDoseFractions()
    val customPeriods = configuredProgram.toCustomPeriodUiStates(occurrences)
    return DosingProgramProgressUiState(
        mode = configuredProgram.schedule.toUiMode(),
        dailyDoseMl = configuredProgram.dailyDoseMicroliters().toMilliliters(),
        scheduledDeliveredTodayMl = progress.completedAmountMicroliters.toMilliliters(),
        manualDeliveredTodayMl = usageToday.manualDeliveredMicroliters
            .takeIf { usageToday.valid }
            ?.toMilliliters()
            ?: 0.0,
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

internal fun DeviceDosingProgram.dailyDoseMicroliters(): Long = when (val value = schedule) {
    is DeviceDosingProgramSchedule.Single -> value.dailyDoseMicroliters
    is DeviceDosingProgramSchedule.Hourly24 -> value.dailyDoseMicroliters
    is DeviceDosingProgramSchedule.CustomPeriods -> value.dailyDoseMicroliters
    is DeviceDosingProgramSchedule.Timer -> value.doses.sumOf { dose -> dose.amountMicroliters }
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

private fun List<DosingProgressOccurrenceUiState>.withDoseFractions():
    List<DosingProgressOccurrenceUiState> {
    val totalAmountMl = sumOf(DosingProgressOccurrenceUiState::amountMl)
    if (totalAmountMl <= 0.0) return this
    var cumulativeAmountMl = 0.0
    return map { occurrence ->
        val startFraction = cumulativeAmountMl / totalAmountMl
        cumulativeAmountMl += occurrence.amountMl
        occurrence.copy(
            startFraction = startFraction.toFloat().coerceIn(0f, 1f),
            endFraction = (cumulativeAmountMl / totalAmountMl).toFloat().coerceIn(0f, 1f)
        )
    }
}

private fun DeviceDosingProgram.placeholderOccurrences():
    List<DosingProgressOccurrenceUiState> = when (val value = schedule) {
        is DeviceDosingProgramSchedule.Single -> listOf(
            placeholderOccurrence(value.dailyDoseMicroliters)
        )
        is DeviceDosingProgramSchedule.Hourly24 -> {
            val amounts = splitAmount(value.dailyDoseMicroliters, HOURLY_OCCURRENCE_COUNT)
            List(HOURLY_OCCURRENCE_COUNT) { index ->
                placeholderOccurrence(amounts[index])
            }
        }
        is DeviceDosingProgramSchedule.CustomPeriods -> {
            val amounts = splitAmount(
                value.dailyDoseMicroliters,
                value.periods.sumOf(DeviceDosingCustomPeriodDraft::doseCount)
            )
            var amountIndex = 0
            value.periods.flatMap { period ->
                List(period.doseCount) {
                    placeholderOccurrence(amounts[amountIndex++])
                }
            }
        }
        is DeviceDosingProgramSchedule.Timer -> value.doses.map { dose ->
            placeholderOccurrence(dose.amountMicroliters)
        }
    }

private fun DeviceDosingProgram.toCustomPeriodUiStates(
    occurrences: List<DosingProgressOccurrenceUiState>
): List<DosingCustomPeriodProgressUiState> {
    val periods = (schedule as? DeviceDosingProgramSchedule.CustomPeriods)?.periods
        ?: return emptyList()
    var cursor = 0
    return periods.map { period ->
        DosingCustomPeriodProgressUiState(
            occurrences = occurrences.drop(cursor).take(period.doseCount)
        ).also { cursor += period.doseCount }
    }
}

private fun DeviceDosingProgram.toProgressMarkers(
    occurrences: List<DosingProgressOccurrenceUiState>,
    customPeriods: List<DosingCustomPeriodProgressUiState>
): List<DosingProgressMarkerUiState> {
    if (occurrences.isEmpty() || schedule is DeviceDosingProgramSchedule.Single) return emptyList()
    val candidateIndexes = when (schedule) {
        is DeviceDosingProgramSchedule.CustomPeriods -> customPeriods
            .runningFold(0) { total, period -> total + period.occurrences.size }
            .drop(1)
            .map { count -> count - 1 }
        else -> occurrences.indices.toList()
    }
    val markerIndexes = candidateIndexes.evenlySampled(MAX_PROGRESS_MARKERS)
    var cumulativeAmountMl = 0.0
    return occurrences.mapIndexedNotNull { index, occurrence ->
        cumulativeAmountMl += occurrence.amountMl
        if (index !in markerIndexes) return@mapIndexedNotNull null
        DosingProgressMarkerUiState(
            positionFraction = occurrence.endFraction,
            cumulativeAmountMl = cumulativeAmountMl
        )
    }
}

private fun List<Int>.evenlySampled(maximumSize: Int): Set<Int> {
    if (size <= maximumSize) return toSet()
    return (1..maximumSize).map { markerNumber ->
        val sourceIndex = (markerNumber * size + maximumSize - 1) / maximumSize - 1
        get(sourceIndex.coerceIn(0, lastIndex))
    }.toSet()
}

private fun placeholderOccurrence(amountMicroliters: Long) = DosingProgressOccurrenceUiState(
    amountMl = amountMicroliters.toMilliliters(),
    visualState = DosingOccurrenceVisualState.PENDING
)

private fun splitAmount(totalMicroliters: Long, count: Int): List<Long> {
    if (count <= 0) return emptyList()
    val baseAmount = totalMicroliters / count
    val remainder = (totalMicroliters % count).toInt()
    return List(count) { index -> baseAmount + if (index < remainder) 1L else 0L }
}

private const val HOURLY_OCCURRENCE_COUNT = 24
private const val MAX_PROGRESS_MARKERS = 6
