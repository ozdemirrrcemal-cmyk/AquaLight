@file:Suppress("MagicNumber")

package com.aqua.aqualight.ui.tabs.devices.detail.cooling

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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardAlpha
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardCardSurface
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardGeometry
import com.aqua.aqualight.ui.common.cooling.AquaCoolingTemperatureChartSpec
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardGeometry
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography
import kotlin.math.ceil
import kotlin.math.floor

@Composable
internal fun CoolingTemperatureCard(
    state: DeviceCoolingRootUiState,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    AquaCoolingDashboardCardSurface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AquaCoolingDashboardGeometry.temperatureCardMinimumHeight)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AquaDeviceCardGeometry.compactGap)
        ) {
            CoolingSectionHeader(
                title = stringResource(R.string.device_cooling_temperature_title),
                trailing = stringResource(R.string.device_cooling_temperature_history_caption),
                colors = colors,
                typography = typography
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                    AquaCoolingDashboardGeometry.temperatureMetricGap
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CoolingTemperatureChart(
                    historyC = state.temperatureHistoryC,
                    colors = colors,
                    typography = typography,
                    modifier = Modifier.weight(1f)
                )
                Column(
                    modifier = Modifier.width(AquaCoolingDashboardGeometry.temperatureMetricWidth),
                    verticalArrangement = Arrangement.spacedBy(AquaDeviceCardGeometry.compactGap)
                ) {
                    CoolingMetric(
                        label = stringResource(R.string.device_cooling_tank_temperature_label),
                        value = coolingTemperatureText(state.tankTemperatureC),
                        colors = colors,
                        typography = typography,
                        primary = true
                    )
                    CoolingMetric(
                        label = stringResource(R.string.device_cooling_room_temperature_label),
                        value = coolingTemperatureText(state.roomTemperatureC),
                        colors = colors,
                        typography = typography
                    )
                    CoolingMetric(
                        label = stringResource(R.string.device_cooling_humidity_label),
                        value = coolingHumidityText(state.humidityPercent),
                        colors = colors,
                        typography = typography
                    )
                }
            }
        }
    }
}

@Composable
private fun CoolingTemperatureChart(
    historyC: List<Double>,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    modifier: Modifier = Modifier
) {
    val chartValues = historyC
        .asSequence()
        .filter(Double::isFinite)
        .map(Double::toFloat)
        .toList()
    val scale = temperatureChartScale(chartValues)

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        TemperatureYAxis(
            scale = scale,
            colors = colors,
            typography = typography
        )
        Spacer(modifier = Modifier.width(AquaCoolingDashboardGeometry.temperatureYAxisGap))
        Column(modifier = Modifier.weight(1f)) {
            CoolingTemperaturePlot(
                values = chartValues,
                scale = scale,
                colors = colors,
                typography = typography
            )
            Spacer(modifier = Modifier.height(AquaDeviceCardGeometry.compactGap))
            TemperatureTimeAxis(colors = colors, typography = typography)
        }
    }
}

@Composable
private fun TemperatureYAxis(
    scale: TemperatureChartScale,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    Column(
        modifier = Modifier
            .width(AquaCoolingDashboardGeometry.temperatureYAxisWidth)
            .height(AquaCoolingDashboardGeometry.temperatureChartHeight),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        scale.axisValues().forEach { value ->
            BasicText(
                text = stringResource(
                    R.string.device_cooling_temperature_axis_value_format,
                    value
                ),
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
private fun CoolingTemperaturePlot(
    values: List<Float>,
    scale: TemperatureChartScale,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    val chartShape = RoundedCornerShape(AquaCoolingDashboardGeometry.temperatureChartCornerRadius)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(AquaCoolingDashboardGeometry.temperatureChartHeight)
            .clip(chartShape)
            .background(colors.mediaSurface.copy(alpha = AquaCoolingDashboardAlpha.chartBackground)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val horizontalPadding = AquaCoolingDashboardGeometry.temperatureChartPadding.toPx()
            val verticalPadding = horizontalPadding
            val plotWidth = (size.width - horizontalPadding * 2f).coerceAtLeast(1f)
            val plotHeight = (size.height - verticalPadding * 2f).coerceAtLeast(1f)
            val gridColor = colors.secondaryText.copy(alpha = AquaCoolingDashboardAlpha.chartGrid)
            val gridStroke = AquaCoolingDashboardGeometry.chartGridStrokeWidth.toPx()
            val gridCount = AquaCoolingTemperatureChartSpec.horizontalGridLineCount

            repeat(gridCount) { index ->
                val fraction = index.toFloat() / (gridCount - 1).coerceAtLeast(1)
                val y = verticalPadding + plotHeight * fraction
                drawLine(
                    color = gridColor,
                    start = Offset(horizontalPadding, y),
                    end = Offset(horizontalPadding + plotWidth, y),
                    strokeWidth = gridStroke
                )
            }
            drawLine(
                color = gridColor,
                start = Offset(horizontalPadding, verticalPadding),
                end = Offset(horizontalPadding, verticalPadding + plotHeight),
                strokeWidth = gridStroke
            )

            if (values.size >= 2) {
                drawTemperatureHistory(
                    values = values,
                    scale = scale,
                    horizontalPadding = horizontalPadding,
                    verticalPadding = verticalPadding,
                    plotWidth = plotWidth,
                    plotHeight = plotHeight,
                    colors = colors
                )
            }
        }

        if (values.size < 2) {
            BasicText(
                text = stringResource(R.string.device_cooling_temperature_history_empty),
                style = typography.micro.copy(
                    color = colors.secondaryText,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.padding(
                    horizontal = AquaCoolingDashboardGeometry.temperatureChartPadding
                )
            )
        }
    }
}

@Composable
private fun TemperatureTimeAxis(
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    val labels = listOf(
        stringResource(R.string.device_cooling_chart_24h_start),
        stringResource(R.string.device_cooling_chart_18h),
        stringResource(R.string.device_cooling_chart_12h),
        stringResource(R.string.device_cooling_chart_6h),
        stringResource(R.string.device_cooling_chart_now)
    )
    Row(modifier = Modifier.fillMaxWidth()) {
        labels.forEachIndexed { index, label ->
            val alignment = when (index) {
                0 -> TextAlign.Start
                labels.lastIndex -> TextAlign.End
                else -> TextAlign.Center
            }
            ChartAxisLabel(
                text = label,
                style = typography.micro.copy(
                    color = colors.secondaryText,
                    textAlign = alignment
                ),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTemperatureHistory(
    values: List<Float>,
    scale: TemperatureChartScale,
    horizontalPadding: Float,
    verticalPadding: Float,
    plotWidth: Float,
    plotHeight: Float,
    colors: AquaDeviceCardColors
) {
    val valueSpan = (scale.maximumC - scale.minimumC).coerceAtLeast(1f)
    val denominator = values.lastIndex.coerceAtLeast(1).toFloat()
    val points = values.mapIndexed { index, value ->
        val x = horizontalPadding + plotWidth * (index / denominator)
        val normalized = ((value - scale.minimumC) / valueSpan).coerceIn(0f, 1f)
        Offset(x, verticalPadding + plotHeight * (1f - normalized))
    }
    val path = smoothPath(points)

    drawPath(
        path = path,
        color = colors.accent.copy(alpha = AquaCoolingDashboardAlpha.chartGlow),
        style = Stroke(
            width = AquaCoolingDashboardGeometry.chartGlowStrokeWidth.toPx(),
            cap = StrokeCap.Round
        )
    )
    drawPath(
        path = path,
        color = colors.accent.copy(alpha = AquaCoolingDashboardAlpha.chartLine),
        style = Stroke(
            width = AquaCoolingDashboardGeometry.chartLineStrokeWidth.toPx(),
            cap = StrokeCap.Round
        )
    )
    drawCircle(
        color = colors.primaryText,
        radius = AquaCoolingDashboardGeometry.chartPointRadius.toPx() + 1f,
        center = points.last()
    )
    drawCircle(
        color = colors.accent,
        radius = AquaCoolingDashboardGeometry.chartPointRadius.toPx(),
        center = points.last()
    )
}

private fun temperatureChartScale(values: List<Float>): TemperatureChartScale {
    var minimum = AquaCoolingTemperatureChartSpec.defaultMinimumC
    var maximum = AquaCoolingTemperatureChartSpec.defaultMaximumC
    val step = AquaCoolingTemperatureChartSpec.expansionStepC

    values.minOrNull()?.let { rawMinimum ->
        if (rawMinimum < minimum) {
            minimum = floor(rawMinimum / step).toFloat() * step
        }
    }
    values.maxOrNull()?.let { rawMaximum ->
        if (rawMaximum > maximum) {
            maximum = ceil(rawMaximum / step).toFloat() * step
        }
    }
    if (maximum <= minimum) {
        maximum = minimum + step * (AquaCoolingTemperatureChartSpec.horizontalGridLineCount - 1)
    }
    return TemperatureChartScale(minimumC = minimum, maximumC = maximum)
}

private data class TemperatureChartScale(
    val minimumC: Float,
    val maximumC: Float
) {
    fun axisValues(): List<Float> {
        val count = AquaCoolingTemperatureChartSpec.horizontalGridLineCount
        val interval = (maximumC - minimumC) / (count - 1).coerceAtLeast(1)
        return List(count) { index -> maximumC - interval * index }
    }
}

private fun smoothPath(points: List<Offset>): Path {
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

@Composable
private fun ChartAxisLabel(
    text: String,
    style: TextStyle,
    modifier: Modifier
) {
    BasicText(
        text = text,
        style = style,
        modifier = modifier,
        maxLines = 1
    )
}
