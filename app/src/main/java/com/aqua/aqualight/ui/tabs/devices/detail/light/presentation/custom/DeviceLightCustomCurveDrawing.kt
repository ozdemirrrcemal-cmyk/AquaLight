package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.custom

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors

internal fun DrawScope.drawCurveGrid(
    colors: AquaDeviceCardColors,
    timeDivisions: Int
) {
    val chartBottom = size.height - CHART_BOTTOM_INSET_DP.dp.toPx()
    repeat(CHART_PERCENT_DIVISIONS + 1) { index ->
        val fraction = index / CHART_PERCENT_DIVISIONS.toFloat()
        drawLine(
            color = colors.outline.copy(alpha = HORIZONTAL_GRID_ALPHA),
            start = Offset(0f, chartBottom * fraction),
            end = Offset(size.width, chartBottom * fraction),
            strokeWidth = GRID_WIDTH_DP.dp.toPx()
        )
    }
    repeat(timeDivisions + 1) { index ->
        val fraction = index / timeDivisions.toFloat()
        drawLine(
            color = colors.outline.copy(alpha = VERTICAL_GRID_ALPHA),
            start = Offset(size.width * fraction, 0f),
            end = Offset(size.width * fraction, chartBottom),
            strokeWidth = GRID_WIDTH_DP.dp.toPx()
        )
    }
}

internal fun DrawScope.drawPlayheadGuide(
    timeMs: Long,
    windowStartMs: Long,
    windowEndMs: Long,
    visuals: DeviceLightCustomVisuals
) {
    val x = chartX(timeMs, size.width, windowStartMs, windowEndMs)
    val chartBottom = size.height - CHART_BOTTOM_INSET_DP.dp.toPx()
    drawLine(
        color = visuals.colors.action.copy(alpha = SELECTED_GUIDE_ALPHA),
        start = Offset(x, 0f),
        end = Offset(x, chartBottom),
        strokeWidth = SELECTED_GUIDE_WIDTH_DP.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(
            floatArrayOf(GUIDE_DASH_DP.dp.toPx(), GUIDE_GAP_DP.dp.toPx())
        )
    )
}

internal fun DrawScope.drawPlayheadThumb(
    timeMs: Long,
    windowStartMs: Long,
    windowEndMs: Long,
    visuals: DeviceLightCustomVisuals
) {
    val haloRadius = PLAYHEAD_HALO_RADIUS_DP.dp.toPx()
    val center = Offset(
        x = chartX(timeMs, size.width, windowStartMs, windowEndMs)
            .coerceIn(haloRadius, size.width - haloRadius),
        y = size.height - CHART_BOTTOM_INSET_DP.dp.toPx()
    )
    drawCircle(
        color = visuals.colors.action.copy(alpha = PLAYHEAD_HALO_ALPHA),
        radius = haloRadius,
        center = center
    )
    drawCircle(
        color = visuals.colors.card.primaryText,
        radius = PLAYHEAD_RING_RADIUS_DP.dp.toPx(),
        center = center
    )
    drawCircle(
        color = visuals.colors.action,
        radius = PLAYHEAD_THUMB_RADIUS_DP.dp.toPx(),
        center = center
    )
}

internal fun DrawScope.drawChannelCurve(
    samples: List<DeviceLightCustomChartSample>,
    actualPoints: List<DeviceLightCustomPointUiState>,
    channel: DeviceLightCustomChannelId,
    selectedTimeMs: Long?,
    windowStartMs: Long,
    windowEndMs: Long,
    visuals: DeviceLightCustomVisuals
) {
    if (samples.isEmpty()) return
    val color = visuals.channelColor(channel)
    val chartHeight = size.height - CHART_BOTTOM_INSET_DP.dp.toPx()
    val path = Path()
    samples.forEachIndexed { index, sample ->
        val coordinate = sample.chartCoordinate(
            size.width,
            chartHeight,
            channel,
            windowStartMs,
            windowEndMs
        )
        if (index == 0) path.moveTo(coordinate.x, coordinate.y)
        else path.lineTo(coordinate.x, coordinate.y)
    }
    drawPath(
        path,
        color,
        style = Stroke(width = CURVE_WIDTH_DP.dp.toPx(), cap = StrokeCap.Round)
    )
    actualPoints.filter { point -> point.timeMs in windowStartMs..windowEndMs }
        .forEach { point ->
            val center = DeviceLightCustomChartSample(point.timeMs, point.channels).chartCoordinate(
                size.width,
                chartHeight,
                channel,
                windowStartMs,
                windowEndMs
            )
            val selected = point.timeMs == selectedTimeMs
            if (selected) {
                drawCircle(
                    color.copy(alpha = SELECTED_HALO_ALPHA),
                    radius = SELECTED_HALO_RADIUS_DP.dp.toPx(),
                    center = center
                )
                drawCircle(
                    visuals.colors.card.primaryText,
                    radius = SELECTED_RING_RADIUS_DP.dp.toPx(),
                    center = center
                )
            } else {
                drawCircle(
                    visuals.colors.card.primaryText,
                    radius = POINT_RING_RADIUS_DP.dp.toPx(),
                    center = center
                )
            }
            drawCircle(
                color,
                radius = if (selected) {
                    SELECTED_POINT_RADIUS_DP.dp.toPx()
                } else {
                    CURVE_POINT_RADIUS_DP.dp.toPx()
                },
                center = center
            )
        }
}

private fun DeviceLightCustomChartSample.chartCoordinate(
    width: Float,
    height: Float,
    channel: DeviceLightCustomChannelId,
    windowStartMs: Long,
    windowEndMs: Long
): Offset = Offset(
    x = chartX(timeMs, width, windowStartMs, windowEndMs),
    y = height * (1f - (channels[channel] ?: 0) / MAX_LIGHT_CHANNEL_PERCENT.toFloat())
)

private const val CHART_PERCENT_DIVISIONS = 4
private const val HORIZONTAL_GRID_ALPHA = 0.45f
private const val VERTICAL_GRID_ALPHA = 0.32f
private const val SELECTED_GUIDE_ALPHA = 0.9f
private const val SELECTED_HALO_ALPHA = 0.24f
private const val PLAYHEAD_HALO_ALPHA = 0.22f
private const val GRID_WIDTH_DP = 1
private const val CHART_BOTTOM_INSET_DP = 12
private const val SELECTED_GUIDE_WIDTH_DP = 1.2f
private const val GUIDE_DASH_DP = 5
private const val GUIDE_GAP_DP = 4
private const val CURVE_WIDTH_DP = 2.2f
private const val POINT_RING_RADIUS_DP = 4.8f
private const val CURVE_POINT_RADIUS_DP = 3.4f
private const val SELECTED_HALO_RADIUS_DP = 12
private const val SELECTED_RING_RADIUS_DP = 7.2f
private const val SELECTED_POINT_RADIUS_DP = 5.4f
private const val PLAYHEAD_HALO_RADIUS_DP = 15
private const val PLAYHEAD_RING_RADIUS_DP = 11
private const val PLAYHEAD_THUMB_RADIUS_DP = 8
