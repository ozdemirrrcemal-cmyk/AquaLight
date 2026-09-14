package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticChannel
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticProgram
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography
import com.aqua.aqualight.ui.common.light.AquaLightPlanChartColors
import kotlin.math.roundToInt

@Composable
internal fun DeviceLightAutomaticProgramChart(
    program: DeviceLightAutomaticProgram,
    channels: List<DeviceLightAutomaticChannel>,
    colors: AquaDeviceCardColors,
    chartColors: AquaLightPlanChartColors,
    typography: AquaDeviceCardTypography
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        AutomaticYAxis(colors, typography)
        Spacer(Modifier.width(DeviceLightAutomaticGeometry.chartYAxisGap))
        Column(modifier = Modifier.weight(1f)) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(DeviceLightAutomaticGeometry.chartHeight)
            ) {
                val gridStroke = DeviceLightAutomaticGeometry.chartGridStrokeWidth.toPx()
                repeat(HOUR_LABEL_COUNT) { index ->
                    val x = size.width * index / (HOUR_LABEL_COUNT - 1)
                    drawLine(
                        color = colors.outline.copy(alpha = DeviceLightAutomaticAlpha.grid),
                        start = androidx.compose.ui.geometry.Offset(x, 0f),
                        end = androidx.compose.ui.geometry.Offset(x, size.height),
                        strokeWidth = gridStroke
                    )
                }
                repeat(PERCENT_GRID_COUNT) { index ->
                    val y = size.height * index / (PERCENT_GRID_COUNT - 1)
                    drawLine(
                        color = colors.outline.copy(alpha = DeviceLightAutomaticAlpha.grid),
                        start = androidx.compose.ui.geometry.Offset(0f, y),
                        end = androidx.compose.ui.geometry.Offset(size.width, y),
                        strokeWidth = gridStroke
                    )
                }
                val plotPoints = automaticProgramPlotPoints(program)
                channels.forEach { channel ->
                    val authoredPercent = program.scene.channels.getValue(channel)
                    val path = Path()
                    plotPoints.forEachIndexed { index, point ->
                        val x = size.width * (point.timeMs.toFloat() / MILLIS_PER_DAY)
                        val value = authoredPercent * point.factor
                        val y = size.height * (1f - value / PERCENT_MAX)
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(
                        path = path,
                        color = channel.automaticColor(chartColors),
                        style = Stroke(
                            width = DeviceLightAutomaticGeometry.chartSeriesStrokeWidth.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }
            AutomaticXAxis(colors, typography)
        }
    }
}

@Composable
private fun AutomaticYAxis(
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    Column(
        modifier = Modifier
            .width(DeviceLightAutomaticGeometry.chartYAxisWidth)
            .height(DeviceLightAutomaticGeometry.chartHeight),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        listOf(PERCENT_MAX_INT, PERCENT_MIDDLE_INT, PERCENT_MIN_INT).forEach { value ->
            BasicText(
                text = stringResource(R.string.device_light_auto_percent_format, value),
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
private fun AutomaticXAxis(
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    val labels = (MINIMUM_HOUR..MAXIMUM_HOUR step HOUR_STEP).map { hour ->
        stringResource(R.string.device_light_auto_hour_format, hour)
    }
    Layout(
        modifier = Modifier
            .fillMaxWidth()
            .height(DeviceLightAutomaticGeometry.chartXAxisHeight),
        content = {
            labels.forEach { label ->
                BasicText(
                    text = label,
                    style = typography.micro.copy(
                        color = colors.secondaryText,
                        textAlign = TextAlign.Center
                    ),
                    maxLines = 1
                )
            }
        }
    ) { measurables, constraints ->
        val childConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { measurable -> measurable.measure(childConstraints) }
        val width = constraints.maxWidth
        layout(width, constraints.maxHeight) {
            val intervals = (placeables.size - 1).coerceAtLeast(1)
            placeables.forEachIndexed { index, placeable ->
                val centerX = width.toFloat() * index / intervals
                val desired = (centerX - placeable.width / 2f).roundToInt()
                val x = desired.coerceIn(0, (width - placeable.width).coerceAtLeast(0))
                placeable.place(x, 0)
            }
        }
    }
}

internal data class DeviceLightAutomaticPlotPoint(
    val timeMs: Long,
    val factor: Float
)

internal fun automaticProgramPlotPoints(
    program: DeviceLightAutomaticProgram
): List<DeviceLightAutomaticPlotPoint> {
    if (program.rampDurationMs == 0L) return zeroRampPlotPoints(program)
    val criticalTimes = sortedSetOf(
        0L,
        MILLIS_PER_DAY_LONG,
        program.startTimeMs,
        program.endTimeMs,
        normalizeTime(program.startTimeMs + program.rampDurationMs),
        normalizeTime(program.endTimeMs - program.rampDurationMs)
    )
    return criticalTimes.map { time ->
        DeviceLightAutomaticPlotPoint(time, programFactorAt(program, time))
    }
}

private fun zeroRampPlotPoints(
    program: DeviceLightAutomaticProgram
): List<DeviceLightAutomaticPlotPoint> = if (program.endTimeMs > program.startTimeMs) {
    buildList {
        add(DeviceLightAutomaticPlotPoint(0L, if (program.startTimeMs == 0L) 1f else 0f))
        if (program.startTimeMs > 0L) {
            add(DeviceLightAutomaticPlotPoint(program.startTimeMs, 0f))
            add(DeviceLightAutomaticPlotPoint(program.startTimeMs, 1f))
        }
        add(DeviceLightAutomaticPlotPoint(program.endTimeMs, 1f))
        add(DeviceLightAutomaticPlotPoint(program.endTimeMs, 0f))
        add(DeviceLightAutomaticPlotPoint(MILLIS_PER_DAY_LONG, 0f))
    }
} else {
    buildList {
        add(DeviceLightAutomaticPlotPoint(0L, if (program.endTimeMs > 0L) 1f else 0f))
        if (program.endTimeMs > 0L) {
            add(DeviceLightAutomaticPlotPoint(program.endTimeMs, 1f))
            add(DeviceLightAutomaticPlotPoint(program.endTimeMs, 0f))
        }
        add(DeviceLightAutomaticPlotPoint(program.startTimeMs, 0f))
        add(DeviceLightAutomaticPlotPoint(program.startTimeMs, 1f))
        add(DeviceLightAutomaticPlotPoint(MILLIS_PER_DAY_LONG, 1f))
    }
}

private fun programFactorAt(program: DeviceLightAutomaticProgram, timeMs: Long): Float {
    val normalizedTime = if (timeMs == MILLIS_PER_DAY_LONG) 0L else timeMs
    val duration = occupiedDuration(program)
    val offset = if (normalizedTime >= program.startTimeMs) {
        normalizedTime - program.startTimeMs
    } else {
        normalizedTime + MILLIS_PER_DAY_LONG - program.startTimeMs
    }
    if (offset >= duration) return 0f
    val ramp = program.rampDurationMs
    if (ramp == 0L) return 1f
    return when {
        offset < ramp -> offset.toFloat() / ramp
        offset > duration - ramp -> (duration - offset).toFloat() / ramp
        else -> 1f
    }.coerceIn(0f, 1f)
}

private fun occupiedDuration(program: DeviceLightAutomaticProgram): Long =
    if (program.endTimeMs > program.startTimeMs) {
        program.endTimeMs - program.startTimeMs
    } else {
        MILLIS_PER_DAY_LONG - program.startTimeMs + program.endTimeMs
    }

private fun normalizeTime(timeMs: Long): Long {
    val value = timeMs % MILLIS_PER_DAY_LONG
    return if (value < 0L) value + MILLIS_PER_DAY_LONG else value
}

internal fun DeviceLightAutomaticChannel.automaticColor(colors: AquaLightPlanChartColors): Color =
    when (this) {
        DeviceLightAutomaticChannel.RED -> colors.red
        DeviceLightAutomaticChannel.GREEN -> colors.green
        DeviceLightAutomaticChannel.BLUE -> colors.blue
        DeviceLightAutomaticChannel.WHITE -> colors.white
    }

private const val MINIMUM_HOUR = 0
private const val MAXIMUM_HOUR = 24
private const val HOUR_STEP = 2
private const val HOUR_LABEL_COUNT = 13
private const val PERCENT_GRID_COUNT = 3
private const val PERCENT_MIN_INT = 0
private const val PERCENT_MIDDLE_INT = 50
private const val PERCENT_MAX_INT = 100
private const val PERCENT_MAX = 100f
private const val MILLIS_PER_DAY = 86_400_000f
private const val MILLIS_PER_DAY_LONG = 86_400_000L
