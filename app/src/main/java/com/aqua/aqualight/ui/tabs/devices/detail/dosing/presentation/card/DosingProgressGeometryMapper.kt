package com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.card

import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgram
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgramSchedule

internal fun List<DosingProgressOccurrenceUiState>.withDoseFractions(
    totalAmountMl: Double
): List<DosingProgressOccurrenceUiState> {
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
    totalAmountMl: Double
): List<DosingProgressMarkerUiState> {
    if (
        occurrences.isEmpty() ||
        totalAmountMl <= 0.0 ||
        schedule is DeviceDosingProgramSchedule.Single
    ) {
        return emptyList()
    }
    // Every visible firmware occurrence is a marker candidate in distributed modes, including
    // Custom Periods. Dense schedules still use the shared marker cap below to avoid label crowding.
    val markerIndexes = occurrences.indices.toList().evenlySampled(MAX_PROGRESS_MARKERS)
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
