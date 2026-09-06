package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.dashboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardGeometry
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardGeometry
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography
import kotlin.math.roundToInt

@Composable
internal fun CoolingTemperatureChart(
    data: CoolingTemperatureChartData,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    modifier: Modifier = Modifier
) {
    val liveTarget = data.liveTimeline.currentLivePoint?.temperatureC?.toFloat()
    val animatedLiveHead = key(liveTarget != null) {
        animateFloatAsState(
            targetValue = liveTarget ?: 0f,
            animationSpec = tween(durationMillis = LIVE_HEAD_ANIMATION_MILLIS),
            label = "Cooling live temperature"
        ).value
    }
    val chartValues = temperatureChartValues(
        archivedPoints = data.archivedPoints,
        historyGeneratedAtEpochMillis = data.historyGeneratedAtEpochMillis,
        liveTimeline = data.liveTimeline,
        animatedLiveHeadTemperatureC = liveTarget?.let { animatedLiveHead }
    )
    val scale = temperatureChartScale(
        chartValues.map(TemperatureChartValue::temperatureC)
    )
    val revealRightFraction = remember { Animatable(0f) }
    val seriesHasRendered = remember { mutableStateOf(false) }
    val liveHead = data.liveTimeline.currentLivePoint
    val latestArchiveEpochMillis = data.archivedPoints
        .maxOfOrNull { point -> point.sampledAtEpochMillis }
    LaunchedEffect(
        data.historyGeneratedAtEpochMillis,
        data.archivedPoints.size,
        latestArchiveEpochMillis,
        liveHead?.inputSampleSequence,
        liveHead?.sampledAtUptimeMillis
    ) {
        if (chartValues.isEmpty()) {
            revealRightFraction.snapTo(0f)
            seriesHasRendered.value = false
        } else {
            val target = chartValues.last().xFraction
            val start = when {
                chartValues.size == 1 -> target
                !seriesHasRendered.value -> chartValues.first().xFraction
                else -> chartValues[chartValues.lastIndex - 1].xFraction
            }
            revealRightFraction.snapTo(start)
            if (target > start) {
                revealRightFraction.animateTo(
                    targetValue = target,
                    animationSpec = tween(durationMillis = SERIES_REVEAL_ANIMATION_MILLIS)
                )
            }
            seriesHasRendered.value = true
        }
    }

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
                chartValues = chartValues,
                scale = scale,
                colors = colors,
                typography = typography,
                revealRightFraction = revealRightFraction.value
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
    chartValues: List<TemperatureChartValue>,
    scale: TemperatureChartScale,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography,
    revealRightFraction: Float
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
            if (chartValues.isNotEmpty()) {
                drawTemperatureHistory(
                    values = chartValues,
                    scale = scale,
                    viewport = viewport,
                    lineColor = colors.accent,
                    drawArea = true,
                    revealRightFraction = revealRightFraction
                )
            }
        }

        if (chartValues.isEmpty()) {
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
    Layout(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AquaCoolingDashboardGeometry.temperatureChartPadding),
        content = {
            labels.forEach { label ->
                ChartAxisLabel(
                    text = label,
                    style = typography.micro.copy(
                        color = colors.secondaryText,
                        textAlign = TextAlign.Center
                    )
                )
            }
        }
    ) { measurables, constraints ->
        val childConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { measurable ->
            measurable.measure(childConstraints)
        }
        val width = constraints.maxWidth
        val height = placeables.maxOfOrNull { placeable -> placeable.height } ?: 0
        val intervalCount = (placeables.size - 1).coerceAtLeast(1)
        layout(width, height) {
            placeables.forEachIndexed { index, placeable ->
                val tickCenter = width.toFloat() * index / intervalCount
                val x = (tickCenter - placeable.width / 2f)
                    .roundToInt()
                    .coerceIn(0, (width - placeable.width).coerceAtLeast(0))
                placeable.place(x, 0)
            }
        }
    }
}

@Composable
private fun ChartAxisLabel(
    text: String,
    style: TextStyle
) {
    BasicText(
        text = text,
        style = style,
        maxLines = 1
    )
}

private const val LIVE_HEAD_ANIMATION_MILLIS = 250
private const val SERIES_REVEAL_ANIMATION_MILLIS = 550
