@file:Suppress("MagicNumber")

package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.library

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryChannel
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryEntry
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryPayload
import com.aqua.aqualight.ui.common.light.AquaLightLibraryAlpha
import com.aqua.aqualight.ui.common.light.AquaLightLibraryGeometry

@Composable
internal fun CustomCurveChart(
    entry: DeviceLightLibraryEntry,
    payload: DeviceLightLibraryPayload.Custom,
    visuals: DeviceLightLibraryVisuals
) {
    val chartDescription = pluralStringResource(
        R.plurals.device_light_library_chart_description,
        payload.points.size,
        payload.points.size
    )
    Column(modifier = Modifier.clearAndSetSemantics { contentDescription = chartDescription }) {
        Row(modifier = Modifier.fillMaxWidth()) {
            ChartYAxis(visuals)
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(AquaLightLibraryGeometry.chartHeight)
            ) {
                drawChartGrid(visuals)
                entry.channels.forEach { channel ->
                    val path = Path()
                    payload.points.forEachIndexed { index, point ->
                        val x = size.width * (point.timeMs / LAST_DAY_MILLIS_FLOAT)
                        val y = size.height *
                            (1f - point.scene.channels.getValue(channel) / 100f)
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        if (payload.points.size == 1) {
                            drawCircle(
                                color = channel.libraryColor(visuals.colors),
                                radius = 2.5.dp.toPx(),
                                center = Offset(x, y)
                            )
                        }
                    }
                    drawPath(
                        path = path,
                        color = channel.libraryColor(visuals.colors),
                        style = Stroke(
                            width = AquaLightLibraryGeometry.chartLineStrokeWidth.toPx(),
                            cap = StrokeCap.Round
                        )
                    )
                }
            }
        }
        ChartXAxis(visuals)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawChartGrid(
    visuals: DeviceLightLibraryVisuals
) {
    val gridColor = visuals.colors.card.outline.copy(alpha = AquaLightLibraryAlpha.grid)
    repeat(GRID_LINE_COUNT) { index ->
        val y = size.height * index / (GRID_LINE_COUNT - 1f)
        drawLine(
            color = gridColor,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = AquaLightLibraryGeometry.chartGridStrokeWidth.toPx()
        )
        val x = size.width * index / (GRID_LINE_COUNT - 1f)
        drawLine(
            color = gridColor,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = AquaLightLibraryGeometry.chartGridStrokeWidth.toPx()
        )
    }
}

@Composable
private fun ChartYAxis(visuals: DeviceLightLibraryVisuals) {
    Column(
        modifier = Modifier
            .width(AquaLightLibraryGeometry.chartYAxisWidth)
            .height(AquaLightLibraryGeometry.chartHeight),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        listOf(100, 50, 0).forEach { percent ->
            BasicText(
                text = stringResource(
                    R.string.device_light_library_channel_percent_format,
                    percent
                ),
                style = visuals.typography.micro
            )
        }
    }
}

@Composable
private fun ChartXAxis(visuals: DeviceLightLibraryVisuals) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = AquaLightLibraryGeometry.chartYAxisWidth),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        listOf("00", "06", "12", "18", "24").forEach { hour ->
            BasicText(text = hour, style = visuals.typography.micro)
        }
    }
}

@Composable
internal fun ChartLegend(
    channels: List<DeviceLightLibraryChannel>,
    visuals: DeviceLightLibraryVisuals
) {
    Row(horizontalArrangement = Arrangement.spacedBy(AquaLightLibraryGeometry.chartLegendGap)) {
        channels.forEach { channel ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(AquaLightLibraryGeometry.chartLegendDotSize)
                        .background(
                            channel.libraryColor(visuals.colors),
                            RoundedCornerShape(percent = 50)
                        )
                )
                Spacer(Modifier.width(5.dp))
                BasicText(text = channel.shortLabel(), style = visuals.typography.micro)
            }
        }
    }
}

private const val GRID_LINE_COUNT = 5
private const val LAST_DAY_MILLIS_FLOAT = 86_399_999f
