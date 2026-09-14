package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.custom

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors

internal fun DrawScope.drawCurveGrid(colors: AquaDeviceCardColors) {
    repeat(CHART_PERCENT_DIVISIONS + 1) { index ->
        val fraction = index / CHART_PERCENT_DIVISIONS.toFloat()
        drawLine(
            color = colors.outline.copy(alpha = HORIZONTAL_GRID_ALPHA),
            start = Offset(0f, size.height * fraction),
            end = Offset(size.width, size.height * fraction),
            strokeWidth = GRID_WIDTH_DP.dp.toPx()
        )
    }
    repeat(HOURS_PER_DAY / CHART_HOUR_STEP + 1) { index ->
        val fraction = index * CHART_HOUR_STEP / HOURS_PER_DAY.toFloat()
        drawLine(
            color = colors.outline.copy(alpha = VERTICAL_GRID_ALPHA),
            start = Offset(size.width * fraction, 0f),
            end = Offset(size.width * fraction, size.height),
            strokeWidth = GRID_WIDTH_DP.dp.toPx()
        )
    }
}

internal fun DrawScope.drawSelectedGuide(
    timeMs: Long,
    visuals: DeviceLightCustomVisuals
) {
    val x = size.width * timeMs / (MILLIS_PER_DAY - MILLIS_PER_MINUTE).toFloat()
    drawLine(
        color = visuals.colors.card.primaryText.copy(alpha = SELECTED_GUIDE_ALPHA),
        start = Offset(x, 0f),
        end = Offset(x, size.height),
        strokeWidth = SELECTED_GUIDE_WIDTH_DP.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(
            floatArrayOf(GUIDE_DASH_DP.dp.toPx(), GUIDE_GAP_DP.dp.toPx())
        )
    )
}

internal fun DrawScope.drawChannelCurve(
    points: List<DeviceLightCustomPointUiState>,
    channel: DeviceLightCustomChannelId,
    visuals: DeviceLightCustomVisuals
) {
    if (points.isEmpty()) return
    val color = visuals.channelColor(channel)
    val path = Path()
    points.forEachIndexed { index, point ->
        val coordinate = point.chartCoordinate(size.width, size.height, channel)
        if (index == 0) path.moveTo(coordinate.x, coordinate.y)
        else path.lineTo(coordinate.x, coordinate.y)
    }
    drawPath(
        path,
        color,
        style = Stroke(width = CURVE_WIDTH_DP.dp.toPx(), cap = StrokeCap.Round)
    )
    points.forEach { point ->
        drawCircle(
            color,
            radius = CURVE_POINT_RADIUS_DP.dp.toPx(),
            center = point.chartCoordinate(size.width, size.height, channel)
        )
    }
}

private fun DeviceLightCustomPointUiState.chartCoordinate(
    width: Float,
    height: Float,
    channel: DeviceLightCustomChannelId
): Offset = Offset(
    x = width * timeMs / (MILLIS_PER_DAY - MILLIS_PER_MINUTE).toFloat(),
    y = height * (1f - (channels[channel] ?: 0) / MAX_LIGHT_CHANNEL_PERCENT.toFloat())
)

private const val HOURS_PER_DAY = 24
private const val CHART_HOUR_STEP = 2
private const val CHART_PERCENT_DIVISIONS = 4
private const val HORIZONTAL_GRID_ALPHA = 0.45f
private const val VERTICAL_GRID_ALPHA = 0.32f
private const val SELECTED_GUIDE_ALPHA = 0.8f
private const val GRID_WIDTH_DP = 1
private const val SELECTED_GUIDE_WIDTH_DP = 1.2f
private const val GUIDE_DASH_DP = 5
private const val GUIDE_GAP_DP = 4
private const val CURVE_WIDTH_DP = 1.8f
private const val CURVE_POINT_RADIUS_DP = 3
