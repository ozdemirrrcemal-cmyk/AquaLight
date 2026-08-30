@file:Suppress("MagicNumber")

package com.aqua.aqualight.ui.tabs.devices.detail.cooling

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardGeometry
import com.aqua.aqualight.ui.common.cooling.AquaCoolingTemperatureChartSpec
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardGeometry
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardSurface
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography

@Composable
internal fun CoolingTemperatureCard(
    state: DeviceCoolingRootUiState,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    AquaDeviceCardSurface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AquaCoolingDashboardGeometry.temperatureCardMinimumHeight)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AquaDeviceCardGeometry.contentGap)
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
                    modifier = Modifier.width(
                        AquaCoolingDashboardGeometry.temperatureMetricWidth
                    ),
                    verticalArrangement = Arrangement.spacedBy(
                        AquaDeviceCardGeometry.contentGap
                    )
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
    val chartShape = RoundedCornerShape(AquaCoolingDashboardGeometry.temperatureChartCornerRadius)
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(AquaCoolingDashboardGeometry.temperatureChartHeight)
                .clip(chartShape)
                .background(
                    colors.mediaSurface.copy(alpha = AquaCoolingDashboardAlpha.chartBackground)
                )
                .border(
                    width = AquaDeviceCardGeometry.outlineWidth,
                    color = colors.mediaOutline,
                    shape = chartShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val horizontalPadding =
                    AquaCoolingDashboardGeometry.temperatureChartPadding.toPx()
                val verticalPadding = horizontalPadding
                val plotWidth = (size.width - horizontalPadding * 2f).coerceAtLeast(1f)
                val plotHeight = (size.height - verticalPadding * 2f).coerceAtLeast(1f)
                val gridColor = colors.secondaryText.copy(
                    alpha = AquaCoolingDashboardAlpha.chartGrid
                )
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

                if (historyC.size >= 2) {
                    drawTemperatureHistory(
                        values = historyC.map(Double::toFloat),
                        horizontalPadding = horizontalPadding,
                        verticalPadding = verticalPadding,
                        plotWidth = plotWidth,
                        plotHeight = plotHeight,
                        colors = colors
                    )
                }
            }

            if (historyC.size < 2) {
                BasicText(
                    text = stringResource(R.string.device_cooling_temperature_history_empty),
                    style = typography.caption.copy(
                        color = colors.secondaryText,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier.padding(
                        horizontal = AquaCoolingDashboardGeometry.temperatureChartPadding
                    )
                )
            }
        }
        Spacer(modifier = Modifier.height(AquaDeviceCardGeometry.compactGap))
        Row(modifier = Modifier.fillMaxWidth()) {
            ChartAxisLabel(
                text = stringResource(R.string.device_cooling_chart_24h_start),
                style = typography.micro,
                modifier = Modifier.weight(1f)
            )
            ChartAxisLabel(
                text = stringResource(R.string.device_cooling_chart_12h),
                style = typography.micro.copy(textAlign = TextAlign.Center),
                modifier = Modifier.weight(1f)
            )
            ChartAxisLabel(
                text = stringResource(R.string.device_cooling_chart_now),
                style = typography.micro.copy(textAlign = TextAlign.End),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTemperatureHistory(
    values: List<Float>,
    horizontalPadding: Float,
    verticalPadding: Float,
    plotWidth: Float,
    plotHeight: Float,
    colors: AquaDeviceCardColors
) {
    val rawMin = values.minOrNull() ?: return
    val rawMax = values.maxOrNull() ?: return
    val rawSpan = rawMax - rawMin
    val span = rawSpan.coerceAtLeast(AquaCoolingTemperatureChartSpec.minimumVerticalSpanC)
    val center = (rawMin + rawMax) / 2f
    val minValue = center - span / 2f - AquaCoolingTemperatureChartSpec.verticalPaddingC
    val maxValue = center + span / 2f + AquaCoolingTemperatureChartSpec.verticalPaddingC
    val valueSpan = (maxValue - minValue).coerceAtLeast(1f)
    val denominator = values.lastIndex.coerceAtLeast(1).toFloat()
    val path = Path()

    values.forEachIndexed { index, value ->
        val x = horizontalPadding + plotWidth * (index / denominator)
        val normalized = ((value - minValue) / valueSpan).coerceIn(0f, 1f)
        val y = verticalPadding + plotHeight * (1f - normalized)
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }

    drawPath(
        path = path,
        color = colors.accent.copy(alpha = AquaCoolingDashboardAlpha.chartLine),
        style = Stroke(
            width = AquaCoolingDashboardGeometry.chartLineStrokeWidth.toPx(),
            cap = StrokeCap.Round
        )
    )

    val finalValue = values.last()
    val finalNormalized = ((finalValue - minValue) / valueSpan).coerceIn(0f, 1f)
    drawCircle(
        color = colors.accent,
        radius = AquaCoolingDashboardGeometry.chartPointRadius.toPx(),
        center = Offset(
            horizontalPadding + plotWidth,
            verticalPadding + plotHeight * (1f - finalNormalized)
        )
    )
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
