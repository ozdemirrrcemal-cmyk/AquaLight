package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.root

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AquaLightDashboardAlpha
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AquaLightDashboardGeometry
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AquaLightPlanChartColors
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.AquaLightPlanChartSpec

internal fun DrawScope.drawLightPlanGrid(colors: AquaLightPlanChartColors) {
    val horizontalLines = AquaLightPlanChartSpec.maximumPercent /
        AquaLightPlanChartSpec.percentStep
    repeat(horizontalLines + 1) { index ->
        val y = size.height * index / horizontalLines
        drawLine(
            color = colors.grid.copy(alpha = AquaLightDashboardAlpha.horizontalGrid),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = AquaLightDashboardGeometry.planGridStrokeWidth.toPx()
        )
    }
    val verticalLines = AquaLightPlanChartSpec.maximumHour / AquaLightPlanChartSpec.hourStep
    repeat(verticalLines + 1) { index ->
        val x = size.width * index / verticalLines
        drawLine(
            color = colors.grid.copy(alpha = AquaLightDashboardAlpha.verticalGrid),
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = AquaLightDashboardGeometry.planGridStrokeWidth.toPx()
        )
    }
}

internal fun DrawScope.drawCurrentTimeGuide(
    nowTimeMs: Long,
    colors: AquaLightPlanChartColors
) {
    val x = size.width * nowTimeMs.toFloat() / MILLIS_IN_DAY
    drawLine(
        color = colors.currentGuide.copy(alpha = AquaLightDashboardAlpha.currentGuide),
        start = Offset(x, 0f),
        end = Offset(x, size.height),
        strokeWidth = AquaLightDashboardGeometry.planCurrentGuideStrokeWidth.toPx(),
        pathEffect = PathEffect.dashPathEffect(
            floatArrayOf(
                AquaLightDashboardGeometry.planCurrentGuideDash.toPx(),
                AquaLightDashboardGeometry.planCurrentGuideGap.toPx()
            )
        )
    )
    drawCircle(
        color = colors.currentGuide,
        radius = AquaLightDashboardGeometry.planCurrentPointRadius.toPx(),
        center = Offset(x, 0f)
    )
}

internal fun DrawScope.drawLightPlanSeries(
    series: DeviceLightPlanSeries,
    color: Color,
    channelScale: Int
) {
    if (series.points.isEmpty()) return
    val path = Path()
    series.points.forEachIndexed { index, point ->
        val x = size.width * point.timeMs.toFloat() / MILLIS_IN_DAY
        val y = size.height * (
            1f - point.level.toFloat() / channelScale.toFloat()
            )
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(
        path = path,
        color = color.copy(alpha = AquaLightDashboardAlpha.inactiveLine),
        style = Stroke(
            width = AquaLightDashboardGeometry.planLineStrokeWidth.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}

@Composable
internal fun LightPlanChevron(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val startX = size.width * AquaLightDashboardGeometry.planChevronStartXFraction
        val endX = size.width * AquaLightDashboardGeometry.planChevronEndXFraction
        val middleY = size.height * AquaLightDashboardGeometry.planChevronMiddleYFraction
        val strokeWidth = AquaLightDashboardGeometry.planChevronStrokeWidth.toPx()
        drawLine(
            color = color,
            start = Offset(
                startX,
                size.height * AquaLightDashboardGeometry.planChevronTopYFraction
            ),
            end = Offset(endX, middleY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(endX, middleY),
            end = Offset(
                startX,
                size.height * AquaLightDashboardGeometry.planChevronBottomYFraction
            ),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

internal const val MILLIS_IN_DAY = 86_400_000f
