package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.history

import androidx.annotation.StringRes
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
import androidx.compose.foundation.lazy.LazyListScope
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
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingDataFreshness
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingDataState
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingStateMessageCard
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.toCommercialCoolingError

/** Device-backed Cooling history surface with explicit typed read-state presentation. */
@Composable
internal fun DeviceCoolingTemperatureHistoryScreen(
    state: DeviceCoolingTemperatureHistoryUiState,
    onRangeSelected: (DeviceCoolingTemperatureHistoryRange) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = aquaCoolingDashboardColors()
    val typography = aquaCoolingDashboardTypography(colors)

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
        historyStateItem(historyStateMessage(state), onRetry)
        historySnapshotItems(state, onRangeSelected, colors, typography)
    }
}

private fun LazyListScope.historyStateItem(
    message: HistoryStateMessage?,
    onRetry: () -> Unit
) {
    message?.let { content ->
        item(key = "state") {
            CoolingStateMessageCard(
                title = stringResource(content.titleRes),
                message = stringResource(content.messageRes),
                retryLabel = if (content.retryAvailable) {
                    stringResource(R.string.device_cooling_state_retry)
                } else {
                    null
                },
                onRetry = onRetry.takeIf { content.retryAvailable }
            )
        }
    }
}

private fun LazyListScope.historySnapshotItems(
    state: DeviceCoolingTemperatureHistoryUiState,
    onRangeSelected: (DeviceCoolingTemperatureHistoryRange) -> Unit,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    val snapshot = state.snapshot
    if (snapshot != null) {
        item(key = "range") {
            CoolingHistoryRangeSelector(
                selectedRange = state.selectedRange,
                onRangeSelected = onRangeSelected,
                colors = colors,
                typography = typography
            )
        }
        if (state.dataState !is CoolingDataState.Empty) {
            item(key = "chart") {
                CoolingHistoryChartCard(
                    points = snapshot.points,
                    range = snapshot.range,
                    colors = colors,
                    typography = typography
                )
            }
            item(key = "summary") {
                CoolingHistorySummaryRow(
                    minimumTemperatureC = snapshot.minimumTemperatureC,
                    averageTemperatureC = snapshot.averageTemperatureC,
                    maximumTemperatureC = snapshot.maximumTemperatureC,
                    colors = colors,
                    typography = typography
                )
            }
            item(key = "daily") {
                CoolingDailyHistoryCard(
                    days = snapshot.dailySummaries,
                    colors = colors,
                    typography = typography
                )
            }
        }
    }
}

private data class HistoryStateMessage(
    @StringRes val titleRes: Int,
    @StringRes val messageRes: Int,
    val retryAvailable: Boolean
)

private fun historyStateMessage(state: DeviceCoolingTemperatureHistoryUiState): HistoryStateMessage? {
    state.dataState.typedHistoryFailureOrNull()?.let { failure ->
        if (failure is DeviceCoolingTemperatureHistoryFailure.Rejected) {
            val copy = failure.reason.toCommercialCoolingError()
            return HistoryStateMessage(
                titleRes = copy.titleRes,
                messageRes = copy.messageRes,
                retryAvailable = true
            )
        }
    }

    return when (val dataState = state.dataState) {
        CoolingDataState.Initial,
        CoolingDataState.Loading -> HistoryStateMessage(
            titleRes = R.string.device_cooling_history_loading_title,
            messageRes = R.string.device_cooling_history_loading_message,
            retryAvailable = false
        )
        is CoolingDataState.Content -> dataState.freshness.historyMessage()
        is CoolingDataState.Empty -> when (dataState.freshness) {
            CoolingDataFreshness.CURRENT -> HistoryStateMessage(
                titleRes = R.string.device_cooling_history_empty_title,
                messageRes = R.string.device_cooling_history_empty_message,
                retryAvailable = false
            )
            CoolingDataFreshness.REFRESHING -> refreshingHistoryMessage()
            CoolingDataFreshness.STALE -> staleHistoryMessage()
        }
        CoolingDataState.Unsupported -> HistoryStateMessage(
            titleRes = R.string.device_cooling_history_unsupported_title,
            messageRes = R.string.device_cooling_history_unsupported_message,
            retryAvailable = false
        )
        CoolingDataState.Unavailable,
        is CoolingDataState.OperationError -> HistoryStateMessage(
            titleRes = R.string.device_cooling_history_unavailable_title,
            messageRes = R.string.device_cooling_history_unavailable_message,
            retryAvailable = true
        )
    }
}

private fun CoolingDataState<*, DeviceCoolingTemperatureHistoryFailure>.typedHistoryFailureOrNull():
    DeviceCoolingTemperatureHistoryFailure? = when (this) {
    is CoolingDataState.Content -> refreshFailure
    is CoolingDataState.Empty -> refreshFailure
    is CoolingDataState.OperationError -> failure
    CoolingDataState.Initial,
    CoolingDataState.Loading,
    CoolingDataState.Unavailable,
    CoolingDataState.Unsupported -> null
}

private fun CoolingDataFreshness.historyMessage(): HistoryStateMessage? = when (this) {
    CoolingDataFreshness.CURRENT -> null
    CoolingDataFreshness.REFRESHING -> refreshingHistoryMessage()
    CoolingDataFreshness.STALE -> staleHistoryMessage()
}

private fun refreshingHistoryMessage() = HistoryStateMessage(
    titleRes = R.string.device_cooling_history_refreshing_title,
    messageRes = R.string.device_cooling_history_refreshing_message,
    retryAvailable = false
)

private fun staleHistoryMessage() = HistoryStateMessage(
    titleRes = R.string.device_cooling_history_stale_title,
    messageRes = R.string.device_cooling_history_stale_message,
    retryAvailable = true
)

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
