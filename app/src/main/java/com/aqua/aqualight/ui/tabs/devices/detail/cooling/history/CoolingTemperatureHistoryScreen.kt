@file:Suppress("LongMethod", "MagicNumber")

package com.aqua.aqualight.ui.tabs.devices.detail.cooling.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingDailyTemperatureSummary
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTemperatureHistoryPoint
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTemperatureHistoryRange
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTemperatureHistorySnapshot
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardCardSurface
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardGeometry
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardPalette
import com.aqua.aqualight.ui.common.cooling.AquaCoolingHistoryAlpha
import com.aqua.aqualight.ui.common.cooling.AquaCoolingHistoryGeometry
import com.aqua.aqualight.ui.common.cooling.AquaCoolingTemperatureChartSpec
import com.aqua.aqualight.ui.common.cooling.aquaCoolingDashboardColors
import com.aqua.aqualight.ui.common.cooling.aquaCoolingDashboardTypography
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

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
        item(key = "range") {
            CoolingHistoryRangeSelector(
                selectedRange = state.selectedRange,
                onRangeSelected = onRangeSelected,
                colors = colors,
                typography = typography
            )
        }

        when (state.loadState) {
            DeviceCoolingTemperatureHistoryLoadState.IDLE,
            DeviceCoolingTemperatureHistoryLoadState.LOADING -> item(key = "loading") {
                CoolingHistoryMessageCard(
                    title = stringResource(R.string.device_cooling_history_loading_title),
                    message = stringResource(R.string.device_cooling_history_loading_message),
                    colors = colors,
                    typography = typography
                )
            }
            DeviceCoolingTemperatureHistoryLoadState.UNSUPPORTED -> item(key = "unsupported") {
                CoolingHistoryMessageCard(
                    title = stringResource(R.string.device_cooling_history_unsupported_title),
                    message = stringResource(R.string.device_cooling_history_unsupported_message),
                    colors = colors,
                    typography = typography,
                    actionLabel = stringResource(R.string.device_cooling_history_retry),
                    onAction = onRetry
                )
            }
            DeviceCoolingTemperatureHistoryLoadState.UNAVAILABLE -> item(key = "unavailable") {
                CoolingHistoryMessageCard(
                    title = stringResource(R.string.device_cooling_history_unavailable_title),
                    message = stringResource(R.string.device_cooling_history_unavailable_message),
                    colors = colors,
                    typography = typography,
                    actionLabel = stringResource(R.string.device_cooling_history_retry),
                    onAction = onRetry
                )
            }
            DeviceCoolingTemperatureHistoryLoadState.CONTENT -> {
                val snapshot = state.snapshot
                if (snapshot != null) {
                    item(key = "chart") {
                        CoolingHistoryChartCard(
                            snapshot = snapshot,
                            colors = colors,
                            typography = typography
                        )
                    }
                    item(key = "summary") {
                        CoolingHistorySummaryRow(
                            snapshot = snapshot,
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
private fun CoolingHistoryChartCard(
    snapshot: DeviceCoolingTemperatureHistorySnapshot,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AquaCoolingHistoryGeometry.sourceGap)
                ) {
                    Box(
                        modifier = Modifier
                            .size(AquaCoolingHistoryGeometry.sourceDotSize)
                            .clip(CircleShape)
                            .background(
                                colors.accent.copy(alpha = AquaCoolingHistoryAlpha.sourceDot)
                            )
                    )
                    BasicText(
                        text = stringResource(R.string.device_cooling_history_firmware_source),
                        style = typography.micro.copy(color = colors.secondaryText),
                        maxLines = 1
                    )
                }
            }
            CoolingHistoryChart(
                points = snapshot.points,
                range = snapshot.range,
                colors = colors,
                typography = typography
            )
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
    val validPoints = points.filter { point -> point.temperatureC.isFinite() }
    val scale = historyTemperatureScale(validPoints.map { point -> point.temperatureC.toFloat() })

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        HistoryYAxis(scale = scale, colors = colors, typography = typography)
        Spacer(modifier = Modifier.width(AquaCoolingHistoryGeometry.chartYAxisGap))
        Column(modifier = Modifier.weight(1f)) {
            val shape = RoundedCornerShape(AquaCoolingHistoryGeometry.chartCornerRadius)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(AquaCoolingHistoryGeometry.chartHeight)
                    .clip(shape)
                    .background(
                        colors.mediaSurface.copy(alpha = AquaCoolingHistoryAlpha.chartBackground)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val padding = AquaCoolingHistoryGeometry.chartPadding.toPx()
                    val plotWidth = (size.width - padding * 2f).coerceAtLeast(1f)
                    val plotHeight = (size.height - padding * 2f).coerceAtLeast(1f)
                    val gridColor = colors.secondaryText.copy(alpha = AquaCoolingHistoryAlpha.chartGrid)
                    val gridStroke = AquaCoolingHistoryGeometry.chartGridStrokeWidth.toPx()
                    val gridCount = AquaCoolingTemperatureChartSpec.horizontalGridLineCount
                    repeat(gridCount) { index ->
                        val fraction = index.toFloat() / (gridCount - 1).coerceAtLeast(1)
                        val y = padding + plotHeight * fraction
                        drawLine(
                            color = gridColor,
                            start = Offset(padding, y),
                            end = Offset(padding + plotWidth, y),
                            strokeWidth = gridStroke
                        )
                    }
                    repeat(HISTORY_VERTICAL_GRID_COUNT) { index ->
                        val fraction = index.toFloat() / (HISTORY_VERTICAL_GRID_COUNT - 1)
                        val x = padding + plotWidth * fraction
                        drawLine(
                            color = gridColor,
                            start = Offset(x, padding),
                            end = Offset(x, padding + plotHeight),
                            strokeWidth = gridStroke
                        )
                    }
                    if (validPoints.size >= 2) {
                        drawHistorySeries(
                            points = validPoints,
                            scale = scale,
                            horizontalPadding = padding,
                            verticalPadding = padding,
                            plotWidth = plotWidth,
                            plotHeight = plotHeight,
                            colors = colors
                        )
                    }
                }
                if (validPoints.size < 2) {
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
            Spacer(modifier = Modifier.height(AquaCoolingHistoryGeometry.chartAxisGap))
            HistoryTimeAxis(
                points = validPoints,
                range = range,
                colors = colors,
                typography = typography
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
    val first = points.firstOrNull()?.sampledAtEpochMillis
    val last = points.lastOrNull()?.sampledAtEpochMillis
    val labels = if (first != null && last != null && last >= first) {
        List(HISTORY_VERTICAL_GRID_COUNT) { index ->
            val fraction = index.toDouble() / (HISTORY_VERTICAL_GRID_COUNT - 1)
            formatHistoryTick(
                epochMillis = first + ((last - first) * fraction).toLong(),
                range = range
            )
        }
    } else {
        List(HISTORY_VERTICAL_GRID_COUNT) { stringResource(R.string.device_cooling_value_unavailable) }
    }
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
                maxLines = 1
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHistorySeries(
    points: List<DeviceCoolingTemperatureHistoryPoint>,
    scale: HistoryTemperatureScale,
    horizontalPadding: Float,
    verticalPadding: Float,
    plotWidth: Float,
    plotHeight: Float,
    colors: AquaDeviceCardColors
) {
    val firstTime = points.first().sampledAtEpochMillis
    val lastTime = points.last().sampledAtEpochMillis
    val timeSpan = (lastTime - firstTime).coerceAtLeast(1L).toDouble()
    val valueSpan = (scale.maximumC - scale.minimumC).coerceAtLeast(1f)
    val offsets = points.map { point ->
        val xFraction = ((point.sampledAtEpochMillis - firstTime) / timeSpan)
            .toFloat()
            .coerceIn(0f, 1f)
        val yFraction = ((point.temperatureC.toFloat() - scale.minimumC) / valueSpan)
            .coerceIn(0f, 1f)
        Offset(
            x = horizontalPadding + plotWidth * xFraction,
            y = verticalPadding + plotHeight * (1f - yFraction)
        )
    }
    val linePath = smoothHistoryPath(offsets)
    val areaPath = Path().apply {
        addPath(linePath)
        lineTo(offsets.last().x, verticalPadding + plotHeight)
        lineTo(offsets.first().x, verticalPadding + plotHeight)
        close()
    }
    drawPath(areaPath, colors.accent.copy(alpha = AquaCoolingHistoryAlpha.chartArea))
    drawPath(
        path = linePath,
        color = colors.accent.copy(alpha = AquaCoolingHistoryAlpha.chartGlow),
        style = Stroke(
            width = AquaCoolingHistoryGeometry.chartGlowStrokeWidth.toPx(),
            cap = StrokeCap.Round
        )
    )
    drawPath(
        path = linePath,
        color = colors.accent,
        style = Stroke(
            width = AquaCoolingHistoryGeometry.chartLineStrokeWidth.toPx(),
            cap = StrokeCap.Round
        )
    )
    drawCircle(
        color = colors.primaryText,
        radius = AquaCoolingHistoryGeometry.chartPointRadius.toPx() + 1f,
        center = offsets.last()
    )
    drawCircle(
        color = colors.accent,
        radius = AquaCoolingHistoryGeometry.chartPointRadius.toPx(),
        center = offsets.last()
    )
}

@Composable
private fun CoolingHistorySummaryRow(
    snapshot: DeviceCoolingTemperatureHistorySnapshot,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AquaCoolingHistoryGeometry.summaryGap)
    ) {
        HistorySummaryMetric(
            label = stringResource(R.string.device_cooling_history_minimum),
            value = historyTemperatureText(snapshot.minimumTemperatureC),
            colors = colors,
            typography = typography,
            modifier = Modifier.weight(1f)
        )
        HistorySummaryMetric(
            label = stringResource(R.string.device_cooling_history_average),
            value = historyTemperatureText(snapshot.averageTemperatureC),
            colors = colors,
            typography = typography,
            modifier = Modifier.weight(1f),
            emphasize = true
        )
        HistorySummaryMetric(
            label = stringResource(R.string.device_cooling_history_maximum),
            value = historyTemperatureText(snapshot.maximumTemperatureC),
            colors = colors,
            typography = typography,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun HistorySummaryMetric(
    label: String,
    value: String,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    modifier: Modifier,
    emphasize: Boolean = false
) {
    AquaCoolingDashboardCardSurface(
        modifier = modifier.heightIn(min = AquaCoolingHistoryGeometry.summaryCardMinimumHeight)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AquaCoolingHistoryGeometry.sourceGap)
        ) {
            BasicText(
                text = label,
                style = typography.micro.copy(color = colors.secondaryText),
                maxLines = 1
            )
            BasicText(
                text = value,
                style = typography.title.copy(
                    color = if (emphasize) colors.accent else colors.primaryText,
                    fontSize = AquaCoolingHistoryGeometry.summaryValueSize
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CoolingDailyHistoryCard(
    days: List<DeviceCoolingDailyTemperatureSummary>,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    AquaCoolingDashboardCardSurface(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            BasicText(
                text = stringResource(R.string.device_cooling_history_daily_title),
                style = typography.title.copy(color = colors.primaryText),
                modifier = Modifier.padding(bottom = AquaCoolingHistoryGeometry.tableHeaderVerticalPadding)
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
        TableCell(
            text = stringResource(R.string.device_cooling_history_minimum_short),
            style = typography.micro.copy(color = colors.secondaryText),
            modifier = Modifier.weight(AquaCoolingHistoryGeometry.tableValueWeight)
        )
        TableCell(
            text = stringResource(R.string.device_cooling_history_average_short),
            style = typography.micro.copy(color = colors.secondaryText),
            modifier = Modifier.weight(AquaCoolingHistoryGeometry.tableValueWeight)
        )
        TableCell(
            text = stringResource(R.string.device_cooling_history_maximum_short),
            style = typography.micro.copy(color = colors.secondaryText),
            modifier = Modifier.weight(AquaCoolingHistoryGeometry.tableValueWeight)
        )
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
            text = formatHistoryDate(day.dayStartEpochMillis),
            style = typography.body.copy(color = colors.primaryText),
            modifier = Modifier.weight(AquaCoolingHistoryGeometry.tableDateWeight),
            alignment = TextAlign.Start
        )
        TableCell(
            text = historyTemperatureText(day.minimumTemperatureC),
            style = typography.caption.copy(color = colors.secondaryText),
            modifier = Modifier.weight(AquaCoolingHistoryGeometry.tableValueWeight)
        )
        TableCell(
            text = historyTemperatureText(day.averageTemperatureC),
            style = typography.caption.copy(color = colors.primaryText),
            modifier = Modifier.weight(AquaCoolingHistoryGeometry.tableValueWeight)
        )
        TableCell(
            text = historyTemperatureText(day.maximumTemperatureC),
            style = typography.caption.copy(color = colors.secondaryText),
            modifier = Modifier.weight(AquaCoolingHistoryGeometry.tableValueWeight)
        )
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
private fun CoolingHistoryMessageCard(
    title: String,
    message: String,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    AquaCoolingDashboardCardSurface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AquaCoolingHistoryGeometry.messageCardMinimumHeight)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AquaCoolingHistoryGeometry.messageGap)
        ) {
            BasicText(text = title, style = typography.title.copy(color = colors.primaryText))
            BasicText(text = message, style = typography.caption.copy(color = colors.secondaryText))
            if (actionLabel != null && onAction != null) {
                val shape = AquaCoolingHistoryGeometry.retryShape
                BasicText(
                    text = actionLabel,
                    style = typography.body.copy(
                        color = colors.accent,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier
                        .clip(shape)
                        .background(colors.accent.copy(alpha = AquaCoolingHistoryAlpha.retryBackground))
                        .border(
                            width = AquaCoolingDashboardGeometry.chartGridStrokeWidth,
                            color = colors.accent.copy(alpha = AquaCoolingDashboardAlpha.selectedOutline),
                            shape = shape
                        )
                        .clickable(role = Role.Button, onClick = onAction)
                        .padding(
                            horizontal = AquaCoolingHistoryGeometry.retryHorizontalPadding,
                            vertical = AquaCoolingHistoryGeometry.retryVerticalPadding
                        )
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

@Composable
private fun historyTemperatureText(value: Double?): String = value
    ?.takeIf(Double::isFinite)
    ?.let { temperature ->
        stringResource(R.string.device_cooling_temperature_value_format, temperature)
    }
    ?: stringResource(R.string.device_cooling_value_unavailable)

private fun historyTemperatureScale(values: List<Float>): HistoryTemperatureScale {
    var minimum = AquaCoolingTemperatureChartSpec.defaultMinimumC
    var maximum = AquaCoolingTemperatureChartSpec.defaultMaximumC
    val step = AquaCoolingTemperatureChartSpec.expansionStepC
    values.minOrNull()?.let { rawMinimum ->
        if (rawMinimum < minimum) minimum = floor(rawMinimum / step).toFloat() * step
    }
    values.maxOrNull()?.let { rawMaximum ->
        if (rawMaximum > maximum) maximum = ceil(rawMaximum / step).toFloat() * step
    }
    if (maximum <= minimum) {
        maximum = minimum + step * (AquaCoolingTemperatureChartSpec.horizontalGridLineCount - 1)
    }
    return HistoryTemperatureScale(minimumC = minimum, maximumC = maximum)
}

private data class HistoryTemperatureScale(
    val minimumC: Float,
    val maximumC: Float
) {
    fun axisValues(): List<Float> {
        val count = AquaCoolingTemperatureChartSpec.horizontalGridLineCount
        val interval = (maximumC - minimumC) / (count - 1).coerceAtLeast(1)
        return List(count) { index -> maximumC - interval * index }
    }
}

private fun smoothHistoryPath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points.first().x, points.first().y)
    points.zipWithNext().forEach { (previous, current) ->
        val middleX = (previous.x + current.x) / 2f
        path.cubicTo(
            middleX,
            previous.y,
            middleX,
            current.y,
            current.x,
            current.y
        )
    }
    return path
}

private fun formatHistoryTick(
    epochMillis: Long,
    range: DeviceCoolingTemperatureHistoryRange
): String {
    val pattern = when (range) {
        DeviceCoolingTemperatureHistoryRange.HOURS_24 -> "HH:mm"
        DeviceCoolingTemperatureHistoryRange.DAYS_7 -> "EEE"
        DeviceCoolingTemperatureHistoryRange.DAYS_30 -> "d MMM"
    }
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(epochMillis))
}

private fun formatHistoryDate(epochMillis: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()).format(Date(epochMillis))

private const val HISTORY_VERTICAL_GRID_COUNT = 5
