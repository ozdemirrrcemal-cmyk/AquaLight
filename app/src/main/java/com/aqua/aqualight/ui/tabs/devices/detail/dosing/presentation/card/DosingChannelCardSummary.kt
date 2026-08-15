package com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.card

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography

@Composable
internal fun DosingChannelSummary(
    state: DosingChannelCardUiState,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SUMMARY_ROW_GAP)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SUMMARY_COLUMN_GAP),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DosingMetricSummary(
                icon = DosingMetricGlyphType.DOSE,
                label = stringResource(
                    R.string.device_dosing_channel_daily_dose_format,
                    state.programProgress.dailyDoseMl
                ),
                colors = colors,
                typography = typography,
                modifier = Modifier.weight(1f)
            )
            DosingMetricSummary(
                icon = DosingMetricGlyphType.DAYS,
                label = state.scheduleDays.summaryLabel(),
                colors = colors,
                typography = typography,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SUMMARY_COLUMN_GAP),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DosingProgramSummary(
                visualState = state.visualState,
                progress = state.programProgress,
                colors = colors,
                typography = typography,
                modifier = Modifier.weight(1f)
            )
            state.reservoir?.let { reservoir ->
                DosingReservoirSummary(
                    state = reservoir,
                    colors = colors,
                    typography = typography,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun DosingMetricSummary(
    icon: DosingMetricGlyphType,
    label: String,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    modifier: Modifier = Modifier
) {
    val tint = when (icon) {
        DosingMetricGlyphType.DOSE -> colors.accent
        DosingMetricGlyphType.DAYS -> colors.secondaryText
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(SUMMARY_ICON_GAP),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DosingMetricGlyph(
            type = icon,
            tint = tint,
            modifier = Modifier.size(SUMMARY_ICON_SIZE)
        )
        BasicText(
            text = label,
            modifier = Modifier.weight(1f),
            style = typography.caption.copy(color = tint),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DosingProgramSummary(
    visualState: DosingChannelVisualState,
    progress: DosingProgramProgressUiState,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    modifier: Modifier = Modifier
) {
    val mode = progress.mode ?: return
    val automaticDosingOff = visualState == DosingChannelVisualState.AUTOMATIC_DOSING_OFF
    val label = when {
        automaticDosingOff -> stringResource(visualState.labelRes)
        progress.totalOccurrences > 0 -> stringResource(
            R.string.device_dosing_channel_mode_progress_format,
            progress.completedOccurrences,
            progress.totalOccurrences,
            stringResource(mode.compactLabelRes())
        )
        else -> stringResource(
            R.string.device_dosing_channel_no_dose_today_format,
            stringResource(mode.compactLabelRes())
        )
    }
    val tint = if (automaticDosingOff) colors.warning else colors.accent
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(SUMMARY_ICON_GAP),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DosingProgramModeGlyph(
            mode = mode,
            tint = tint,
            modifier = Modifier.size(SUMMARY_ICON_SIZE)
        )
        BasicText(
            text = label,
            modifier = Modifier.weight(1f),
            style = typography.caption.copy(
                color = if (automaticDosingOff) colors.warning else colors.primaryText
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DosingScheduleDaysUiState.summaryLabel(): String = when {
    selectedDays.isEmpty() -> stringResource(R.string.device_dosing_channel_no_days_selected)
    isEveryDay -> stringResource(R.string.device_dosing_channel_every_day)
    isWeekdays -> stringResource(R.string.device_dosing_channel_weekdays)
    isWeekend -> stringResource(R.string.device_dosing_channel_weekend)
    else -> selectedDays
        .map { day -> stringResource(day.shortLabelRes) }
        .joinToString(separator = DAY_SEPARATOR)
}

private fun DosingProgramModeUiState.compactLabelRes(): Int = when (this) {
    DosingProgramModeUiState.SINGLE -> R.string.device_dosing_channel_mode_single
    DosingProgramModeUiState.HOURLY_24 -> R.string.device_dosing_channel_mode_hourly
    DosingProgramModeUiState.CUSTOM_PERIODS -> R.string.device_dosing_channel_mode_custom
    DosingProgramModeUiState.TIMER -> R.string.device_dosing_channel_mode_timer
}

private const val DAY_SEPARATOR = " · "
private val SUMMARY_ROW_GAP = 5.dp
private val SUMMARY_COLUMN_GAP = 18.dp
private val SUMMARY_ICON_GAP = 6.dp
private val SUMMARY_ICON_SIZE = 16.dp
