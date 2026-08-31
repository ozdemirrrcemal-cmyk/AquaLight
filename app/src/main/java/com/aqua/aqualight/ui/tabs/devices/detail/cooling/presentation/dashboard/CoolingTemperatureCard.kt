package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardAlpha
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardCardSurface
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardGeometry
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardGeometry
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.root.DeviceCoolingRootUiState

@Composable
internal fun CoolingTemperatureCard(
    state: DeviceCoolingRootUiState,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val historyDescription = stringResource(R.string.device_cooling_view_history_description)
    AquaCoolingDashboardCardSurface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AquaCoolingDashboardGeometry.temperatureCardMinimumHeight)
            .semantics { contentDescription = historyDescription }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
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
                CoolingTemperatureMetrics(state, colors, typography)
            }
        }
    }
}

@Composable
private fun CoolingTemperatureMetrics(
    state: DeviceCoolingRootUiState,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
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
            val viewport = TemperaturePlotViewport(
                horizontalPadding = horizontalPadding,
                verticalPadding = horizontalPadding,
                width = (size.width - horizontalPadding * 2f).coerceAtLeast(1f),
                height = (size.height - horizontalPadding * 2f).coerceAtLeast(1f)
            )
            drawTemperatureGrid(viewport = viewport, colors = colors)

            if (values.size >= 2) {
                drawTemperatureHistory(
                    values = values,
                    scale = scale,
                    viewport = viewport,
                    colors = colors
                )
            }
        }

        if (values.size < 2) {
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
