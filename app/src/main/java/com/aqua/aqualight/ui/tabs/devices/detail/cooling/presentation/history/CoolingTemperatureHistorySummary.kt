package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingDailyTemperatureSummary
import com.aqua.aqualight.i18n.LocaleFormatter
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardCardSurface
import com.aqua.aqualight.ui.common.cooling.AquaCoolingHistoryAlpha
import com.aqua.aqualight.ui.common.cooling.AquaCoolingHistoryGeometry
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography

private data class HistorySummaryMetricModel(
    val label: String,
    val value: String,
    val emphasize: Boolean = false
)

@Composable
internal fun CoolingHistorySummaryRow(
    minimumTemperatureC: Double?,
    averageTemperatureC: Double?,
    maximumTemperatureC: Double?,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AquaCoolingHistoryGeometry.summaryGap)
    ) {
        HistorySummaryMetric(
            model = HistorySummaryMetricModel(
                label = stringResource(R.string.device_cooling_history_minimum),
                value = historyTemperatureText(minimumTemperatureC)
            ),
            colors = colors,
            typography = typography,
            modifier = Modifier.weight(1f)
        )
        HistorySummaryMetric(
            model = HistorySummaryMetricModel(
                label = stringResource(R.string.device_cooling_history_average),
                value = historyTemperatureText(averageTemperatureC),
                emphasize = true
            ),
            colors = colors,
            typography = typography,
            modifier = Modifier.weight(1f)
        )
        HistorySummaryMetric(
            model = HistorySummaryMetricModel(
                label = stringResource(R.string.device_cooling_history_maximum),
                value = historyTemperatureText(maximumTemperatureC)
            ),
            colors = colors,
            typography = typography,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun HistorySummaryMetric(
    model: HistorySummaryMetricModel,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    modifier: Modifier
) {
    AquaCoolingDashboardCardSurface(
        modifier = modifier.heightIn(min = AquaCoolingHistoryGeometry.summaryCardMinimumHeight)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AquaCoolingHistoryGeometry.sourceGap)
        ) {
            BasicText(
                text = model.label,
                style = typography.micro.copy(color = colors.secondaryText),
                maxLines = 1
            )
            BasicText(
                text = model.value,
                style = typography.title.copy(
                    color = if (model.emphasize) colors.accent else colors.primaryText,
                    fontSize = AquaCoolingHistoryGeometry.summaryValueSize
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun CoolingDailyHistoryCard(
    days: List<DeviceCoolingDailyTemperatureSummary>,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    AquaCoolingDashboardCardSurface(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            BasicText(
                text = stringResource(R.string.device_cooling_history_daily_title),
                style = typography.title.copy(color = colors.primaryText),
                modifier = Modifier.padding(
                    bottom = AquaCoolingHistoryGeometry.tableHeaderVerticalPadding
                )
            )
            DailyTableHeader(colors = colors, typography = typography)
            HistoryDivider(colors)
            if (days.isEmpty()) {
                BasicText(
                    text = stringResource(R.string.device_cooling_history_daily_empty),
                    style = typography.caption.copy(
                        color = colors.secondaryText,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = AquaCoolingHistoryGeometry.messageGap)
                )
            } else {
                days.forEachIndexed { index, day ->
                    DailyHistoryRow(day = day, colors = colors, typography = typography)
                    if (index != days.lastIndex) HistoryDivider(colors)
                }
            }
        }
    }
}

@Composable
private fun DailyTableHeader(
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AquaCoolingHistoryGeometry.tableHeaderVerticalPadding),
        horizontalArrangement = Arrangement.spacedBy(AquaCoolingHistoryGeometry.tableColumnGap)
    ) {
        TableCell(
            text = stringResource(R.string.device_cooling_history_date),
            style = typography.micro.copy(color = colors.secondaryText),
            modifier = Modifier.weight(AquaCoolingHistoryGeometry.tableDateWeight),
            alignment = TextAlign.Start
        )
        listOf(
            R.string.device_cooling_history_minimum_short,
            R.string.device_cooling_history_average_short,
            R.string.device_cooling_history_maximum_short
        ).forEach { labelRes ->
            TableCell(
                text = stringResource(labelRes),
                style = typography.micro.copy(color = colors.secondaryText),
                modifier = Modifier.weight(AquaCoolingHistoryGeometry.tableValueWeight)
            )
        }
    }
}

@Composable
private fun DailyHistoryRow(
    day: DeviceCoolingDailyTemperatureSummary,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AquaCoolingHistoryGeometry.tableRowVerticalPadding),
        horizontalArrangement = Arrangement.spacedBy(AquaCoolingHistoryGeometry.tableColumnGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TableCell(
            text = LocaleFormatter.formatDate(LocalContext.current, day.dayStartEpochMillis),
            style = typography.body.copy(color = colors.primaryText),
            modifier = Modifier.weight(AquaCoolingHistoryGeometry.tableDateWeight),
            alignment = TextAlign.Start
        )
        listOf(
            day.minimumTemperatureC to colors.secondaryText,
            day.averageTemperatureC to colors.primaryText,
            day.maximumTemperatureC to colors.secondaryText
        ).forEach { (temperature, color) ->
            TableCell(
                text = historyTemperatureText(temperature),
                style = typography.caption.copy(color = color),
                modifier = Modifier.weight(AquaCoolingHistoryGeometry.tableValueWeight)
            )
        }
    }
}

@Composable
private fun TableCell(
    text: String,
    style: TextStyle,
    modifier: Modifier,
    alignment: TextAlign = TextAlign.End
) {
    BasicText(
        text = text,
        style = style.copy(textAlign = alignment),
        modifier = modifier,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun HistoryDivider(colors: AquaDeviceCardColors) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(AquaCoolingHistoryGeometry.tableDividerHeight)
            .background(colors.mediaOutline.copy(alpha = AquaCoolingHistoryAlpha.divider))
    )
}

@Composable
private fun historyTemperatureText(value: Double?): String = value
    ?.takeIf(Double::isFinite)
    ?.let { temperature ->
        stringResource(R.string.device_cooling_temperature_value_format, temperature)
    }
    ?: stringResource(R.string.device_cooling_value_unavailable)
