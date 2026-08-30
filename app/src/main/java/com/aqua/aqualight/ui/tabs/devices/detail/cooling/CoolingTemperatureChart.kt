@file:Suppress("LongMethod", "MagicNumber", "ReturnCount")

package com.aqua.aqualight.ui.tabs.devices.detail.cooling

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aqua.aqualight.R

@Composable
internal fun CoolingTemperatureChart(modifier: Modifier = Modifier) {
    val chartDescription = stringResource(R.string.device_cooling_temperature_chart_description)
    CoolingDashboardCard(
        modifier = modifier
            .fillMaxWidth()
            .height(CHART_CARD_HEIGHT),
        contentPadding = PaddingValues(0.dp)
    ) {
        BasicText(
            text = stringResource(R.string.device_cooling_temperature),
            modifier = Modifier.padding(start = 12.dp, top = 11.dp, end = 12.dp),
            style = coolingTextStyle(
                size = 14.sp,
                lineHeight = 18.sp,
                color = CoolingDashboardPalette.textPrimary,
                semiBold = true
            )
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(start = 7.dp, end = 9.dp, bottom = 8.dp)
        ) {
            CoolingTemperatureCanvas(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .semantics { contentDescription = chartDescription }
            )
            CoolingTemperatureMetrics(
                modifier = Modifier
                    .width(83.dp)
                    .fillMaxHeight()
                    .padding(start = 9.dp, top = 2.dp)
            )
        }
    }
}

@Composable
private fun CoolingTemperatureCanvas(modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer(cacheSize = 12)
    val axisLabels = listOf(
        stringResource(R.string.device_cooling_chart_24_hours),
        stringResource(R.string.device_cooling_chart_18_hours),
        stringResource(R.string.device_cooling_chart_12_hours),
        stringResource(R.string.device_cooling_chart_6_hours),
        stringResource(R.string.device_cooling_chart_now)
    )
    val labelStyle = coolingTextStyle(
        size = 8.sp,
        lineHeight = 10.sp,
        color = CoolingDashboardPalette.textSecondary
    )

    Canvas(modifier = modifier) {
        val left = 36.dp.toPx()
        val right = 4.dp.toPx()
        val top = 8.dp.toPx()
        val bottom = 20.dp.toPx()
        val graphWidth = size.width - left - right
        val graphHeight = size.height - top - bottom
        val graphBottom = top + graphHeight
        val yValues = listOf(30f, 27f, 24f, 21f)

        fun yPosition(value: Float): Float = top + ((CHART_MAX - value) / (CHART_MAX - CHART_MIN)) * graphHeight

        yValues.forEach { value ->
            val y = yPosition(value)
            drawLine(
                color = CoolingDashboardPalette.outlineSoft.copy(alpha = 0.75f),
                start = Offset(left, y),
                end = Offset(left + graphWidth, y),
                strokeWidth = 0.75.dp.toPx()
            )
            val label = "${value.toInt()} °C"
            val measured = textMeasurer.measure(label, labelStyle)
            drawText(
                textLayoutResult = measured,
                topLeft = Offset(0f, y - measured.size.height / 2f)
            )
        }

        val targetY = yPosition(PLACEHOLDER_TARGET)
        drawLine(
            color = CoolingDashboardPalette.textSecondary.copy(alpha = 0.9f),
            start = Offset(left, targetY),
            end = Offset(left + graphWidth, targetY),
            strokeWidth = 0.9.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
        )

        drawLine(
            color = CoolingDashboardPalette.outlineSoft,
            start = Offset(left, top),
            end = Offset(left, graphBottom),
            strokeWidth = 0.8.dp.toPx()
        )
        drawLine(
            color = CoolingDashboardPalette.outlineSoft,
            start = Offset(left, graphBottom),
            end = Offset(left + graphWidth, graphBottom),
            strokeWidth = 0.8.dp.toPx()
        )

        val points = PLACEHOLDER_TEMPERATURES.mapIndexed { index, value ->
            Offset(
                x = left + graphWidth * index / (PLACEHOLDER_TEMPERATURES.lastIndex.toFloat()),
                y = yPosition(value)
            )
        }
        val curve = smoothPath(points)
        drawPath(
            path = curve,
            color = CoolingDashboardPalette.accent.copy(alpha = 0.18f),
            style = Stroke(width = 4.5.dp.toPx(), cap = StrokeCap.Round)
        )
        drawPath(
            path = curve,
            color = CoolingDashboardPalette.accent,
            style = Stroke(width = 1.35.dp.toPx(), cap = StrokeCap.Round)
        )

        axisLabels.forEachIndexed { index, label ->
            val measured = textMeasurer.measure(label, labelStyle)
            val x = left + graphWidth * index / axisLabels.lastIndex - measured.size.width / 2f
            drawText(
                textLayoutResult = measured,
                topLeft = Offset(
                    x = x.coerceIn(left - 2.dp.toPx(), size.width - measured.size.width),
                    y = graphBottom + 5.dp.toPx()
                )
            )
        }
    }
}

private fun smoothPath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points.first().x, points.first().y)
    if (points.size == 1) return path

    for (index in 0 until points.lastIndex) {
        val p0 = points.getOrElse(index - 1) { points[index] }
        val p1 = points[index]
        val p2 = points[index + 1]
        val p3 = points.getOrElse(index + 2) { p2 }
        val control1 = Offset(
            x = p1.x + (p2.x - p0.x) / 6f,
            y = p1.y + (p2.y - p0.y) / 6f
        )
        val control2 = Offset(
            x = p2.x - (p3.x - p1.x) / 6f,
            y = p2.y - (p3.y - p1.y) / 6f
        )
        path.cubicTo(
            control1.x,
            control1.y,
            control2.x,
            control2.y,
            p2.x,
            p2.y
        )
    }
    return path
}

@Composable
private fun CoolingTemperatureMetrics(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        BasicText(
            text = stringResource(R.string.device_cooling_current_temperature_value),
            style = coolingTextStyle(
                size = 20.sp,
                lineHeight = 24.sp,
                color = CoolingDashboardPalette.textPrimary,
                semiBold = true,
                textAlign = TextAlign.End
            )
        )
        BasicText(
            text = stringResource(R.string.device_cooling_now),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 3.dp),
            style = coolingTextStyle(
                size = 9.sp,
                lineHeight = 12.sp,
                color = CoolingDashboardPalette.textSecondary,
                textAlign = TextAlign.End
            )
        )
        BasicText(
            text = stringResource(R.string.device_cooling_target_temperature_value),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 29.dp),
            style = coolingTextStyle(
                size = 16.sp,
                lineHeight = 20.sp,
                color = CoolingDashboardPalette.textPrimary,
                semiBold = true,
                textAlign = TextAlign.End
            )
        )
        BasicText(
            text = stringResource(R.string.device_cooling_target),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 3.dp),
            style = coolingTextStyle(
                size = 9.sp,
                lineHeight = 12.sp,
                color = CoolingDashboardPalette.textSecondary,
                textAlign = TextAlign.End
            )
        )
    }
}

private val CHART_CARD_HEIGHT = 158.dp
private const val CHART_MIN = 21f
private const val CHART_MAX = 30f
private const val PLACEHOLDER_TARGET = 26f
private val PLACEHOLDER_TEMPERATURES = listOf(
    23.6f,
    24.0f,
    24.6f,
    24.1f,
    24.2f,
    23.7f,
    24.0f,
    25.0f,
    26.1f,
    26.9f,
    27.8f,
    28.9f,
    27.8f,
    28.5f,
    29.2f,
    30.0f,
    28.8f,
    27.8f,
    27.5f,
    26.3f,
    25.1f,
    24.2f,
    23.8f,
    23.4f,
    23.1f,
    23.8f,
    24.2f,
    24.0f,
    24.5f,
    24.1f,
    24.4f,
    23.9f
)
