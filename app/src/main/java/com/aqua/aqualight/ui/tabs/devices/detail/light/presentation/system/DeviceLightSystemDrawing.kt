@file:Suppress("MagicNumber")

package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.system

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

@Composable
internal fun DeviceLightAutomaticChart(
    startTemperature: Int,
    fullSpeedTemperature: Int,
    visuals: DeviceLightSystemVisuals,
    modifier: Modifier = Modifier
) {
    val domain = chartDomain(startTemperature, fullSpeedTemperature)
    Column(modifier) {
        Row(Modifier.weight(1f)) {
            Column(
                modifier = Modifier.width(DeviceLightSystemGeometry.chartYAxisWidth),
                horizontalAlignment = Alignment.End
            ) {
                ChartAxisLabel("%100", visuals, Modifier.weight(1f))
                ChartAxisLabel("%50", visuals, Modifier.weight(1f))
                ChartAxisLabel("%0", visuals, Modifier.weight(1f))
            }
            Canvas(
                Modifier
                    .weight(1f)
                    .fillMaxSize()
            ) {
                drawAutomaticChart(
                    startTemperature = startTemperature,
                    fullSpeedTemperature = fullSpeedTemperature,
                    domain = domain,
                    visuals = visuals
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(DeviceLightSystemGeometry.chartXAxisHeight),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(DeviceLightSystemGeometry.chartYAxisWidth))
            domain.labels.forEach { label ->
                BasicText(
                    text = label.toString(),
                    style = visuals.typography.micro.copy(textAlign = TextAlign.Center),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ChartAxisLabel(
    text: String,
    visuals: DeviceLightSystemVisuals,
    modifier: Modifier = Modifier
) {
    Box(modifier, contentAlignment = Alignment.TopEnd) {
        BasicText(text = text, style = visuals.typography.micro)
    }
}

private fun DrawScope.drawAutomaticChart(
    startTemperature: Int,
    fullSpeedTemperature: Int,
    domain: TemperatureChartDomain,
    visuals: DeviceLightSystemVisuals
) {
    val gridColor = visuals.colors.card.mediaOutline.copy(alpha = DeviceLightSystemAlpha.grid)
    repeat(3) { index ->
        val y = size.height * index / 2f
        drawLine(
            color = gridColor,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = DeviceLightSystemGeometry.chartGridWidth.toPx()
        )
    }
    domain.labels.forEach { label ->
        val x = domain.x(label, size.width)
        drawLine(
            color = gridColor,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = DeviceLightSystemGeometry.chartGridWidth.toPx()
        )
    }
    val startX = domain.x(startTemperature, size.width)
    val fullX = domain.x(fullSpeedTemperature, size.width)
    drawDashedGuide(startX, visuals)
    drawDashedGuide(fullX, visuals)
    val curve = Path().apply {
        moveTo(0f, size.height)
        lineTo(startX, size.height)
        lineTo(fullX, 0f)
        lineTo(size.width, 0f)
    }
    drawPath(
        path = curve,
        color = visuals.colors.action,
        style = Stroke(
            width = DeviceLightSystemGeometry.chartLineWidth.toPx(),
            cap = StrokeCap.Round
        )
    )
    drawCircle(
        color = visuals.colors.card.primaryText,
        radius = DeviceLightSystemGeometry.chartPointRadius.toPx(),
        center = Offset(startX, size.height)
    )
    drawCircle(
        color = visuals.colors.card.primaryText,
        radius = DeviceLightSystemGeometry.chartPointRadius.toPx(),
        center = Offset(fullX, 0f)
    )
}

private fun DrawScope.drawDashedGuide(x: Float, visuals: DeviceLightSystemVisuals) {
    val dash = 5.dp.toPx()
    var top = 0f
    while (top < size.height) {
        drawLine(
            color = visuals.colors.card.secondaryText,
            start = Offset(x, top),
            end = Offset(x, min(top + dash, size.height)),
            strokeWidth = DeviceLightSystemGeometry.chartGridWidth.toPx()
        )
        top += dash * 2f
    }
}

private fun chartDomain(start: Int, fullSpeed: Int): TemperatureChartDomain {
    val lower = floor((min(start, fullSpeed) - 10) / 5.0).toInt() * 5
    val upper = ceil((max(start, fullSpeed) + 10) / 5.0).toInt() * 5
    val step = if (upper - lower <= 40) 5 else 10
    return TemperatureChartDomain(
        minimum = lower,
        maximum = upper,
        labels = (lower..upper step step).toList()
    )
}

private data class TemperatureChartDomain(
    val minimum: Int,
    val maximum: Int,
    val labels: List<Int>
) {
    fun x(value: Int, width: Float): Float =
        (value - minimum).toFloat() / (maximum - minimum).coerceAtLeast(1) * width
}
