package com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.card

import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgram
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgramSchedule

internal fun List<DosingProgressOccurrenceUiState>.withDoseFractions():
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

internal fun DeviceDosingProgram.toProgressMarkers(
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

private const val MAX_PROGRESS_MARKERS = 6
