package com.aqua.aqualight.ui.tabs.devices.detail.cooling.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTemperatureHistoryRange
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardGeometry
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardPalette
import com.aqua.aqualight.ui.common.cooling.AquaCoolingHistoryAlpha
import com.aqua.aqualight.ui.common.cooling.AquaCoolingHistoryGeometry
import com.aqua.aqualight.ui.common.cooling.aquaCoolingDashboardColors
import com.aqua.aqualight.ui.common.cooling.aquaCoolingDashboardTypography
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography

/**
 * Firmware-backed Cooling history surface.
 *
 * Connectivity is gated before this destination is entered. Range controls, chart, summary metrics
 * and the daily table therefore remain the only product surface; missing measurements are rendered
 * as unavailable placeholders and no synthetic history is invented.
 */
@Composable
internal fun DeviceCoolingTemperatureHistoryScreen(
    state: DeviceCoolingTemperatureHistoryUiState,
    onRangeSelected: (DeviceCoolingTemperatureHistoryRange) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = aquaCoolingDashboardColors()
    val typography = aquaCoolingDashboardTypography(colors)
    val snapshot = state.snapshot

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = AquaCoolingHistoryGeometry.screenHorizontalPadding,
            top = AquaCoolingHistoryGeometry.screenTopPadding,
            end = AquaCoolingHistoryGeometry.screenHorizontalPadding,
            bottom = AquaCoolingHistoryGeometry.screenBottomPadding
        ),
        verticalArrangement = Arrangement.spacedBy(AquaCoolingHistoryGeometry.sectionGap)
    ) {
        item(key = "range") {
            CoolingHistoryRangeSelector(
                selectedRange = state.selectedRange,
                onRangeSelected = onRangeSelected,
                colors = colors,
                typography = typography
            )
        }
        item(key = "chart") {
            CoolingHistoryChartCard(
                points = snapshot?.points.orEmpty(),
                range = snapshot?.range ?: state.selectedRange,
                colors = colors,
                typography = typography
            )
        }
        item(key = "summary") {
            CoolingHistorySummaryRow(
                minimumTemperatureC = snapshot?.minimumTemperatureC,
                averageTemperatureC = snapshot?.averageTemperatureC,
                maximumTemperatureC = snapshot?.maximumTemperatureC,
                colors = colors,
                typography = typography
            )
        }
        item(key = "daily") {
            CoolingDailyHistoryCard(
                days = snapshot?.dailySummaries.orEmpty(),
                colors = colors,
                typography = typography
            )
        }
    }
}

@Composable
private fun CoolingHistoryRangeSelector(
    selectedRange: DeviceCoolingTemperatureHistoryRange,
    onRangeSelected: (DeviceCoolingTemperatureHistoryRange) -> Unit,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    val shape = AquaCoolingHistoryGeometry.rangeContainerShape
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AquaCoolingDashboardPalette.insetSurface)
            .border(
                width = AquaCoolingDashboardGeometry.chartGridStrokeWidth,
                color = AquaCoolingDashboardPalette.insetOutline,
                shape = shape
            )
            .padding(AquaCoolingHistoryGeometry.rangeContainerPadding),
        horizontalArrangement = Arrangement.spacedBy(AquaCoolingHistoryGeometry.rangeSegmentGap)
    ) {
        DeviceCoolingTemperatureHistoryRange.entries.forEach { range ->
            val selected = range == selectedRange
            val segmentShape = AquaCoolingHistoryGeometry.rangeSegmentShape
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(segmentShape)
                    .background(
                        if (selected) {
                            colors.accent.copy(alpha = AquaCoolingHistoryAlpha.rangeSelectedBackground)
                        } else {
                            colors.mediaSurface.copy(alpha = AquaCoolingHistoryAlpha.rangeIdleBackground)
                        }
                    )
                    .selectable(
                        selected = selected,
                        role = Role.RadioButton,
                        onClick = { onRangeSelected(range) }
                    )
                    .padding(
                        horizontal = AquaCoolingHistoryGeometry.rangeSegmentHorizontalPadding,
                        vertical = AquaCoolingHistoryGeometry.rangeSegmentVerticalPadding
                    ),
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    text = rangeLabel(range),
                    style = typography.body.copy(
                        color = if (selected) colors.primaryText else colors.secondaryText,
                        textAlign = TextAlign.Center
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun rangeLabel(range: DeviceCoolingTemperatureHistoryRange): String = when (range) {
    DeviceCoolingTemperatureHistoryRange.HOURS_24 ->
        stringResource(R.string.device_cooling_history_range_24h)
    DeviceCoolingTemperatureHistoryRange.DAYS_7 ->
        stringResource(R.string.device_cooling_history_range_7d)
    DeviceCoolingTemperatureHistoryRange.DAYS_30 ->
        stringResource(R.string.device_cooling_history_range_30d)
}
