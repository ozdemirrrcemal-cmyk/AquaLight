package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.dashboard

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardAlpha
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardGeometry
import com.aqua.aqualight.ui.common.cooling.AquaCoolingTemperatureChartSpec
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import kotlin.math.ceil
import kotlin.math.floor

internal fun DrawScope.drawTemperatureHistory(
    values: List<Float>,
    scale: TemperatureChartScale,
    viewport: TemperaturePlotViewport,
    lineColor: Color,
    drawArea: Boolean
) {
    val points = temperatureHistoryOffsets(values, scale, viewport)
    if (drawArea) {
        drawTemperatureArea(
            path = smoothTemperatureAreaPath(
                points = points,
                bottomY = viewport.verticalPadding + viewport.height
            ),
            lineColor = lineColor,
            viewport = viewport
        )
    }
    drawTemperatureSeries(
        path = smoothTemperaturePath(points),
        lineColor = lineColor,
        emphasized = drawArea
    )
}

internal fun DrawScope.drawTemperatureGrid(
    viewport: TemperaturePlotViewport,
    colors: AquaDeviceCardColors
) {
    val gridColor = colors.secondaryText.copy(alpha = AquaCoolingDashboardAlpha.chartGrid)
    val gridStroke = AquaCoolingDashboardGeometry.chartGridStrokeWidth.toPx()
    val dashEffect = PathEffect.dashPathEffect(
        floatArrayOf(
            AquaCoolingDashboardGeometry.chartGridDashLength.toPx(),
            AquaCoolingDashboardGeometry.chartGridDashGap.toPx()
        )
    )
    val gridCount = AquaCoolingTemperatureChartSpec.horizontalGridLineCount
    repeat(gridCount) { index ->
        val fraction = index.toFloat() / (gridCount - 1).coerceAtLeast(1)
        val y = viewport.verticalPadding + viewport.height * fraction
        drawLine(
            color = gridColor,
            start = Offset(viewport.horizontalPadding, y),
            end = Offset(viewport.horizontalPadding + viewport.width, y),
            strokeWidth = gridStroke,
            pathEffect = dashEffect
        )
    }
    drawLine(
        color = gridColor,
        start = Offset(viewport.horizontalPadding, viewport.verticalPadding),
        end = Offset(
            viewport.horizontalPadding,
            viewport.verticalPadding + viewport.height
        ),
        strokeWidth = gridStroke
    )
}

internal fun temperatureChartScale(values: List<Float>): TemperatureChartScale {
    var minimum = AquaCoolingTemperatureChartSpec.defaultMinimumC
    var maximum = AquaCoolingTemperatureChartSpec.defaultMaximumC
    val step = AquaCoolingTemperatureChartSpec.expansionStepC
    values.minOrNull()?.let { rawMinimum ->
        if (rawMinimum < minimum) minimum = floor(rawMinimum / step).toFloat() * step
    }
    values.maxOrNull()?.let { rawMaximum ->
        if (rawMaximum > maximum) maximum = ceil(rawMaximum / step).toFloat() * step
    }
    if (maximum <= minimum) {
        maximum = minimum + step * (AquaCoolingTemperatureChartSpec.horizontalGridLineCount - 1)
    }
    return TemperatureChartScale(minimumC = minimum, maximumC = maximum)
}

internal data class TemperaturePlotViewport(
    val horizontalPadding: Float,
    val verticalPadding: Float,
    val width: Float,
    val height: Float
)

internal data class TemperatureChartScale(
    val minimumC: Float,
    val maximumC: Float
) {
    fun axisValues(): List<Float> {
        val count = AquaCoolingTemperatureChartSpec.horizontalGridLineCount
        val interval = (maximumC - minimumC) / (count - 1).coerceAtLeast(1)
        return List(count) { index -> maximumC - interval * index }
    }
}

private fun temperatureHistoryOffsets(
    values: List<Float>,
    scale: TemperatureChartScale,
    viewport: TemperaturePlotViewport
): List<Offset> {
    val valueSpan = (scale.maximumC - scale.minimumC).coerceAtLeast(1f)
    val denominator = values.lastIndex.coerceAtLeast(1).toFloat()
    return values.mapIndexed { index, value ->
        val x = viewport.horizontalPadding + viewport.width * (index / denominator)
        val normalized = ((value - scale.minimumC) / valueSpan).coerceIn(0f, 1f)
        Offset(x, viewport.verticalPadding + viewport.height * (1f - normalized))
    }
}

private fun DrawScope.drawTemperatureSeries(
    path: Path,
    lineColor: Color,
    emphasized: Boolean
) {
    if (emphasized) {
        drawPath(
            path = path,
            color = lineColor.copy(alpha = AquaCoolingDashboardAlpha.chartGlow),
            style = Stroke(
                width = AquaCoolingDashboardGeometry.chartGlowStrokeWidth.toPx(),
                cap = StrokeCap.Round
            )
        )
    }
    drawPath(
        path = path,
        color = lineColor.copy(alpha = AquaCoolingDashboardAlpha.chartLine),
        style = Stroke(
            width = if (emphasized) {
                AquaCoolingDashboardGeometry.chartLineStrokeWidth.toPx()
            } else {
                AquaCoolingDashboardGeometry.chartSecondaryLineStrokeWidth.toPx()
            },
            cap = StrokeCap.Round
        )
    )
}

private fun DrawScope.drawTemperatureArea(
    path: Path,
    lineColor: Color,
    viewport: TemperaturePlotViewport
) {
    drawPath(
        path = path,
        brush = Brush.verticalGradient(
            colors = listOf(
                lineColor.copy(alpha = AquaCoolingDashboardAlpha.chartAreaTop),
                lineColor.copy(alpha = AquaCoolingDashboardAlpha.chartAreaBottom)
            ),
            startY = viewport.verticalPadding,
            endY = viewport.verticalPadding + viewport.height
        )
    )
}

private fun smoothTemperaturePath(points: List<Offset>): Path {
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

private fun smoothTemperatureAreaPath(points: List<Offset>, bottomY: Float): Path {
    if (points.isEmpty()) return Path()
    return smoothTemperaturePath(points).apply {
        lineTo(points.last().x, bottomY)
        lineTo(points.first().x, bottomY)
        close()
    }
}
