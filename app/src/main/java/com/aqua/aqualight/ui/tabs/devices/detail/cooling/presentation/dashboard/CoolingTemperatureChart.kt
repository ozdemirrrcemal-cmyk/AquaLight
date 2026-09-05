package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardGeometry
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardGeometry
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography

@Composable
internal fun CoolingTemperatureChart(
    tankHistoryC: List<Double>,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    modifier: Modifier = Modifier
) {
    val tankValues = tankHistoryC.toChartValues()
    val scale = temperatureChartScale(tankValues)

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
                tankValues = tankValues,
                scale = scale,
                colors = colors,
                typography = typography
            )
            Spacer(modifier = Modifier.height(AquaDeviceCardGeometry.compactGap))
            TemperatureTimeAxis(colors = colors, typography = typography)
        }
    }
}

private fun List<Double>.toChartValues(): List<Float> = asSequence()
    .filter(Double::isFinite)
    .map(Double::toFloat)
    .toList()

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
    tankValues: List<Float>,
    scale: TemperatureChartScale,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(AquaCoolingDashboardGeometry.temperatureChartHeight),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val horizontalPadding = AquaCoolingDashboardGeometry.temperatureChartPadding.toPx()
            val viewport = TemperaturePlotViewport(
                horizontalPadding = horizontalPadding,
                verticalPadding = horizontalPadding,
                width = (size.width - horizontalPadding * 2f).coerceAtLeast(1f),
                height = (size.height - horizontalPadding * 2f).coerceAtLeast(1f)
            )
            drawTemperatureGrid(viewport = viewport, colors = colors)
            if (tankValues.size >= MINIMUM_CHART_POINT_COUNT) {
                drawTemperatureHistory(
                    values = tankValues,
                    scale = scale,
                    viewport = viewport,
                    lineColor = colors.accent,
                    drawArea = true
                )
            }
        }

        if (tankValues.size < MINIMUM_CHART_POINT_COUNT) {
            CoolingTemperatureEmptyState(colors = colors, typography = typography)
        }
    }
}

@Composable
private fun CoolingTemperatureEmptyState(
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
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

private const val MINIMUM_CHART_POINT_COUNT = 2
