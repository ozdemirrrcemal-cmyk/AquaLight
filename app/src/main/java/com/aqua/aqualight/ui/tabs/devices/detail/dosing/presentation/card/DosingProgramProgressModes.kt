package com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.card

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography

@Composable
internal fun DosingSingleProgramProgress(
    state: DosingProgramProgressUiState,
    palette: DosingProgressPalette,
    typography: AquaDeviceCardTypography,
    modifier: Modifier = Modifier
) {
    DosingDoseRail(state, palette, typography, modifier = modifier)
}

@Composable
internal fun DosingHourlyProgramProgress(
    state: DosingProgramProgressUiState,
    palette: DosingProgressPalette,
    typography: AquaDeviceCardTypography,
    modifier: Modifier = Modifier
) {
    DosingDoseRail(
        state = state,
        palette = palette,
        typography = typography,
        groupBreaks = state.hourlyGroupBreaks(),
        groupGap = HOURLY_GROUP_GAP,
        modifier = modifier
    )
}

@Composable
internal fun DosingCustomProgramProgress(
    state: DosingProgramProgressUiState,
    palette: DosingProgressPalette,
    typography: AquaDeviceCardTypography,
    modifier: Modifier = Modifier
) {
    DosingDoseRail(
        state = state,
        palette = palette,
        typography = typography,
        groupBreaks = state.customGroupBreaks(),
        groupGap = CUSTOM_GROUP_GAP,
        modifier = modifier
    )
}

@Composable
internal fun DosingTimerProgramProgress(
    state: DosingProgramProgressUiState,
    palette: DosingProgressPalette,
    typography: AquaDeviceCardTypography,
    modifier: Modifier = Modifier
) {
    DosingDoseRail(state, palette, typography, modifier = modifier)
}

private fun DosingProgramProgressUiState.customGroupBreaks(): Set<Int> {
    var consumedOccurrences = 0
    return customPeriods.dropLast(1).map { period ->
        consumedOccurrences += period.occurrences.size
        consumedOccurrences
    }.toSet()
}

private fun DosingProgramProgressUiState.hourlyGroupBreaks(): Set<Int> =
    (HOURLY_GROUP_SIZE until totalOccurrences step HOURLY_GROUP_SIZE).toSet()

private const val HOURLY_GROUP_SIZE = 4
private val HOURLY_GROUP_GAP = 2.dp
private val CUSTOM_GROUP_GAP = 6.dp
