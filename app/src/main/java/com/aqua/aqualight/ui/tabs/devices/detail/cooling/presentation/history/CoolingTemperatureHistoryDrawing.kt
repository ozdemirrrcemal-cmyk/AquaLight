package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.history

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTemperatureHistoryPoint
import com.aqua.aqualight.ui.common.cooling.AquaCoolingHistoryAlpha
import com.aqua.aqualight.ui.common.cooling.AquaCoolingHistoryChartSpec
import com.aqua.aqualight.ui.common.cooling.AquaCoolingHistoryGeometry
import com.aqua.aqualight.ui.common.cooling.AquaCoolingTemperatureChartSpec
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import kotlin.math.ceil
import kotlin.math.floor

internal fun DrawScope.drawHistoryGrid(
    viewport: HistoryPlotViewport,
    scale: HistoryTemperatureScale,
    colors: AquaDeviceCardColors
) {
    val majorGridColor = colors.secondaryText.copy(alpha = AquaCoolingHistoryAlpha.chartGrid)
    val minorGridColor = colors.secondaryText.copy(alpha = AquaCoolingHistoryAlpha.chartMinorGrid)
    val gridStroke = AquaCoolingHistoryGeometry.chartGridStrokeWidth.toPx()
    val horizontalGridCount = scale.axisValues().size
    repeat(horizontalGridCount) { index ->
        val fraction = index.toFloat() / (horizontalGridCount - 1).coerceAtLeast(1)
        val y = viewport.verticalPadding + viewport.height * fraction
        val isMajor = index % AquaCoolingHistoryChartSpec.temperatureMajorGridStride == 0
        drawLine(
            color = if (isMajor) majorGridColor else minorGridColor,
            start = Offset(viewport.horizontalPadding, y),
            end = Offset(viewport.horizontalPadding + viewport.width, y),
            strokeWidth = gridStroke
        )
    }
    repeat(AquaCoolingHistoryChartSpec.timeAxisLabelCount) { index ->
        val fraction = index.toFloat() /
            (AquaCoolingHistoryChartSpec.timeAxisLabelCount - 1).coerceAtLeast(1)
        val x = viewport.horizontalPadding + viewport.width * fraction
        drawLine(
            color = majorGridColor,
            start = Offset(x, viewport.verticalPadding),
            end = Offset(x, viewport.verticalPadding + viewport.height),
            strokeWidth = gridStroke
        )
    }
}

internal fun DrawScope.drawHistorySeries(
    points: List<DeviceCoolingTemperatureHistoryPoint>,
    scale: HistoryTemperatureScale,
    viewport: HistoryPlotViewport,
    colors: AquaDeviceCardColors
) {
    val firstTime = points.first().sampledAtEpochMillis
    val timeSpan = (points.last().sampledAtEpochMillis - firstTime).coerceAtLeast(1L).toDouble()
    val valueSpan = (scale.maximumC - scale.minimumC).coerceAtLeast(1f)
    val offsets = points.map { point ->
        val xFraction = historyHorizontalFraction(
            sampledAtEpochMillis = point.sampledAtEpochMillis,
            firstTime = firstTime,
            timeSpan = timeSpan
        )
        val yFraction = ((point.temperatureC.toFloat() - scale.minimumC) / valueSpan)
            .coerceIn(0f, 1f)
        Offset(
            x = viewport.horizontalPadding + viewport.width * xFraction,
            y = viewport.verticalPadding + viewport.height * (1f - yFraction)
        )
    }
    drawHistoryPaths(offsets = offsets, viewport = viewport, colors = colors)
    drawHistoryEndpoint(offset = offsets.last(), colors = colors)
}

internal fun historyHorizontalFraction(
    sampledAtEpochMillis: Long,
    firstTime: Long,
    timeSpan: Double
): Float {
    val chronologicalFraction = ((sampledAtEpochMillis - firstTime) / timeSpan)
        .toFloat()
        .coerceIn(0f, 1f)
    return 1f - chronologicalFraction
}

private fun DrawScope.drawHistoryPaths(
    offsets: List<Offset>,
    viewport: HistoryPlotViewport,
    colors: AquaDeviceCardColors
) {
    val linePath = smoothHistoryPath(offsets)
    drawPath(
        historyAreaPath(offsets, viewport.verticalPadding + viewport.height),
        colors.accent.copy(alpha = AquaCoolingHistoryAlpha.chartArea)
    )
    drawPath(
        path = linePath,
        color = colors.accent.copy(alpha = AquaCoolingHistoryAlpha.chartGlow),
        style = Stroke(
            width = AquaCoolingHistoryGeometry.chartGlowStrokeWidth.toPx(),
            cap = StrokeCap.Round
        )
    )
    drawPath(
        path = linePath,
        color = colors.accent,
        style = Stroke(
            width = AquaCoolingHistoryGeometry.chartLineStrokeWidth.toPx(),
            cap = StrokeCap.Round
        )
    )
}

private fun DrawScope.drawHistoryEndpoint(
    offset: Offset,
    colors: AquaDeviceCardColors
) {
    drawCircle(
        color = colors.primaryText,
        radius = AquaCoolingHistoryGeometry.chartPointRadius.toPx() + 1f,
        center = offset
    )
    drawCircle(
        color = colors.accent,
        radius = AquaCoolingHistoryGeometry.chartPointRadius.toPx(),
        center = offset
    )
}

internal fun historyTemperatureScale(values: List<Float>): HistoryTemperatureScale {
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
    return HistoryTemperatureScale(minimumC = minimum, maximumC = maximum)
}

internal data class HistoryTemperatureScale(
    val minimumC: Float,
    val maximumC: Float
) {
    fun axisValues(): List<Float> {
        val count = AquaCoolingHistoryChartSpec.temperatureAxisLabelCount
        val interval = (maximumC - minimumC) / (count - 1).coerceAtLeast(1)
        return List(count) { index -> maximumC - interval * index }
    }
}

internal data class HistoryPlotViewport(
    val horizontalPadding: Float,
    val verticalPadding: Float,
    val width: Float,
    val height: Float
)

private fun smoothHistoryPath(points: List<Offset>): Path {
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

private fun historyAreaPath(points: List<Offset>, bottomY: Float): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points.first().x, bottomY)
    path.lineTo(points.first().x, points.first().y)
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
    path.lineTo(points.last().x, bottomY)
    path.close()
    return path
}
