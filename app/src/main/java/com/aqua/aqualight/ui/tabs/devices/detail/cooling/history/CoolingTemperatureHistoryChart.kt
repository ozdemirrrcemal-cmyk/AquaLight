package com.aqua.aqualight.ui.tabs.devices.detail.cooling.history

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTemperatureHistoryPoint
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTemperatureHistoryRange
import com.aqua.aqualight.i18n.LocaleFormatter
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardCardSurface
import com.aqua.aqualight.ui.common.cooling.AquaCoolingHistoryAlpha
import com.aqua.aqualight.ui.common.cooling.AquaCoolingHistoryGeometry
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography

@Composable
internal fun CoolingHistoryChartCard(
    points: List<DeviceCoolingTemperatureHistoryPoint>,
    range: DeviceCoolingTemperatureHistoryRange,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    AquaCoolingDashboardCardSurface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AquaCoolingHistoryGeometry.chartCardMinimumHeight)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AquaCoolingHistoryGeometry.chartAxisGap)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                BasicText(
                    text = stringResource(R.string.device_cooling_history_chart_title),
                    style = typography.title.copy(color = colors.primaryText),
                    maxLines = 1
                )
                BasicText(
                    text = stringResource(R.string.device_cooling_history_chart_subtitle),
                    style = typography.micro.copy(color = colors.secondaryText),
                    maxLines = 1
                )
            }
            CoolingHistoryChart(points, range, colors, typography)
        }
    }
}

@Composable
private fun CoolingHistoryChart(
    points: List<DeviceCoolingTemperatureHistoryPoint>,
    range: DeviceCoolingTemperatureHistoryRange,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    val validPoints = points.filter { point ->
        point.temperatureC.isFinite() && point.sampledAtEpochMillis >= 0L
    }.sortedBy(DeviceCoolingTemperatureHistoryPoint::sampledAtEpochMillis)
    val scale = historyTemperatureScale(validPoints.map { point -> point.temperatureC.toFloat() })
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        HistoryYAxis(scale = scale, colors = colors, typography = typography)
        Spacer(modifier = Modifier.width(AquaCoolingHistoryGeometry.chartYAxisGap))
        Column(modifier = Modifier.weight(1f)) {
            CoolingHistoryPlot(validPoints, scale, colors, typography)
            Spacer(modifier = Modifier.height(AquaCoolingHistoryGeometry.chartAxisGap))
            HistoryTimeAxis(validPoints, range, colors, typography)
        }
    }
}

@Composable
private fun CoolingHistoryPlot(
    points: List<DeviceCoolingTemperatureHistoryPoint>,
    scale: HistoryTemperatureScale,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    val shape = RoundedCornerShape(AquaCoolingHistoryGeometry.chartCornerRadius)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(AquaCoolingHistoryGeometry.chartHeight)
            .clip(shape)
            .background(colors.mediaSurface.copy(alpha = AquaCoolingHistoryAlpha.chartBackground)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val padding = AquaCoolingHistoryGeometry.chartPadding.toPx()
            val viewport = HistoryPlotViewport(
                horizontalPadding = padding,
                verticalPadding = padding,
                width = (size.width - padding * 2f).coerceAtLeast(1f),
                height = (size.height - padding * 2f).coerceAtLeast(1f)
            )
            drawHistoryGrid(viewport = viewport, colors = colors)
            if (points.size >= 2) {
                drawHistorySeries(
                    points = points,
                    scale = scale,
                    viewport = viewport,
                    colors = colors
                )
            }
        }
        if (points.size < 2) {
            BasicText(
                text = stringResource(R.string.device_cooling_history_no_samples),
                style = typography.caption.copy(
                    color = colors.secondaryText,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.padding(horizontal = AquaCoolingHistoryGeometry.chartPadding)
            )
        }
    }
}

@Composable
private fun HistoryYAxis(
    scale: HistoryTemperatureScale,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    Column(
        modifier = Modifier
            .width(AquaCoolingHistoryGeometry.chartYAxisWidth)
            .height(AquaCoolingHistoryGeometry.chartHeight),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        scale.axisValues().forEach { value ->
            BasicText(
                text = stringResource(R.string.device_cooling_temperature_axis_value_format, value),
                style = typography.micro.copy(
                    color = colors.secondaryText,
                    textAlign = TextAlign.End
                ),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun HistoryTimeAxis(
    points: List<DeviceCoolingTemperatureHistoryPoint>,
    range: DeviceCoolingTemperatureHistoryRange,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    val labels = historyTimeLabels(LocalContext.current, points, range)
    Row(modifier = Modifier.fillMaxWidth()) {
        labels.forEachIndexed { index, label ->
            BasicText(
                text = label,
                style = typography.micro.copy(
                    color = colors.secondaryText,
                    textAlign = when (index) {
                        0 -> TextAlign.Start
                        labels.lastIndex -> TextAlign.End
                        else -> TextAlign.Center
                    }
                ),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun historyTimeLabels(
    context: Context,
    points: List<DeviceCoolingTemperatureHistoryPoint>,
    range: DeviceCoolingTemperatureHistoryRange
): List<String> {
    val first = points.firstOrNull()?.sampledAtEpochMillis
    val last = points.lastOrNull()?.sampledAtEpochMillis
    return if (first != null && last != null && last >= first) {
        List(HISTORY_VERTICAL_GRID_COUNT) { index ->
            val fraction = index.toDouble() / (HISTORY_VERTICAL_GRID_COUNT - 1)
            formatHistoryTick(
                context = context,
                epochMillis = first + ((last - first) * fraction).toLong(),
                range = range
            )
        }
    } else {
        List(HISTORY_VERTICAL_GRID_COUNT) {
            stringResource(R.string.device_cooling_value_unavailable)
        }
    }
}

private fun formatHistoryTick(
    context: Context,
    epochMillis: Long,
    range: DeviceCoolingTemperatureHistoryRange
): String = when (range) {
    DeviceCoolingTemperatureHistoryRange.HOURS_24 ->
        LocaleFormatter.formatTime(context, epochMillis)
    DeviceCoolingTemperatureHistoryRange.DAYS_7 ->
        LocaleFormatter.formatWeekdayShort(context, epochMillis)
    DeviceCoolingTemperatureHistoryRange.DAYS_30 ->
        LocaleFormatter.formatDayMonthShort(context, epochMillis)
}
