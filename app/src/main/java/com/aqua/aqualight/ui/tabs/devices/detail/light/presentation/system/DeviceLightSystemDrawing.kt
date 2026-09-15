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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.light.system.DeviceLightSystemCondition
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

@Composable
internal fun DeviceLightTemperatureGauge(
    temperatureCelsius: Double?,
    condition: DeviceLightSystemCondition,
    visuals: DeviceLightSystemVisuals,
    modifier: Modifier = Modifier
) {
    val statusColor = condition.statusColor(visuals)
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = DeviceLightSystemGeometry.gaugeTrackWidth.toPx()
            val inset = stroke / 2f
            val bounds = Rect(
                left = inset,
                top = inset,
                right = size.width - inset,
                bottom = size.height - inset
            )
            drawArc(
                color = visuals.colors.card.mediaOutline.copy(
                    alpha = DeviceLightSystemAlpha.track
                ),
                startAngle = DeviceLightSystemGeometry.gaugeStartAngle,
                sweepAngle = DeviceLightSystemGeometry.gaugeSweep,
                useCenter = false,
                topLeft = bounds.topLeft,
                size = bounds.size,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
            val fraction = temperatureCelsius?.toFloat()?.let { value ->
                ((value - DeviceLightSystemGeometry.gaugeMinimumTemperature) /
                    (DeviceLightSystemGeometry.gaugeMaximumTemperature -
                        DeviceLightSystemGeometry.gaugeMinimumTemperature)).coerceIn(0f, 1f)
            } ?: 0f
            drawArc(
                color = statusColor,
                startAngle = DeviceLightSystemGeometry.gaugeStartAngle,
                sweepAngle = DeviceLightSystemGeometry.gaugeSweep * fraction,
                useCenter = false,
                topLeft = bounds.topLeft,
                size = bounds.size,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BasicText(
                text = temperatureCelsius?.let { value ->
                    stringResource(R.string.device_light_system_temperature_decimal_format, value)
                } ?: stringResource(R.string.device_light_system_temperature_unavailable),
                style = visuals.typography.title.copy(
                    fontSize = DeviceLightSystemGeometry.gaugeValueSize,
                    color = visuals.colors.card.primaryText,
                    textAlign = TextAlign.Center
                )
            )
            Spacer(Modifier.height(DeviceLightSystemGeometry.gaugeStatusGap))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.width(DeviceLightSystemGeometry.statusDotSize).height(
                    DeviceLightSystemGeometry.statusDotSize
                )) {
                    drawCircle(statusColor)
                }
                Spacer(Modifier.width(DeviceLightSystemGeometry.gaugeStatusGap))
                BasicText(
                    text = stringResource(condition.statusLabelRes()),
                    style = visuals.typography.caption.copy(color = statusColor)
                )
            }
        }
    }
}

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

@Composable
internal fun DeviceLightFanIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val blade = min(size.width, size.height) * 0.26f
        repeat(3) { index ->
            rotate(index * 120f, center) {
                drawOval(
                    color = tint,
                    topLeft = Offset(center.x - blade * 0.58f, center.y - blade * 1.8f),
                    size = Size(blade * 1.16f, blade * 2f)
                )
            }
        }
        drawCircle(tint, radius = blade * 0.34f, center = center)
        drawCircle(
            contrastingColor(tint),
            radius = blade * 0.12f,
            center = center
        )
    }
}

@Composable
internal fun DeviceLightInfoIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = max(1f, size.minDimension * 0.08f)
        drawCircle(tint)
        drawCircle(Color.Transparent, radius = size.minDimension * 0.38f)
        drawLine(
            color = contrastingColor(tint),
            start = Offset(size.width / 2f, size.height * 0.44f),
            end = Offset(size.width / 2f, size.height * 0.73f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawCircle(
            color = contrastingColor(tint),
            radius = stroke / 2f,
            center = Offset(size.width / 2f, size.height * 0.28f)
        )
    }
}

@Composable
internal fun DeviceLightProtectionIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = size.minDimension * 0.065f
        val shield = Path().apply {
            moveTo(size.width * 0.50f, size.height * 0.06f)
            lineTo(size.width * 0.84f, size.height * 0.19f)
            lineTo(size.width * 0.81f, size.height * 0.62f)
            quadraticBezierTo(
                size.width * 0.76f,
                size.height * 0.82f,
                size.width * 0.50f,
                size.height * 0.95f
            )
            quadraticBezierTo(
                size.width * 0.24f,
                size.height * 0.82f,
                size.width * 0.19f,
                size.height * 0.62f
            )
            lineTo(size.width * 0.16f, size.height * 0.19f)
            close()
        }
        drawPath(shield, tint, style = Stroke(stroke, cap = StrokeCap.Round))
        drawCircle(
            color = tint,
            radius = size.minDimension * 0.12f,
            center = Offset(size.width * 0.50f, size.height * 0.61f),
            style = Stroke(stroke)
        )
        drawLine(
            color = tint,
            start = Offset(size.width * 0.50f, size.height * 0.26f),
            end = Offset(size.width * 0.50f, size.height * 0.53f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
internal fun DeviceLightLockIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = size.minDimension * 0.10f
        drawArc(
            color = tint,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(size.width * 0.27f, size.height * 0.08f),
            size = Size(size.width * 0.46f, size.height * 0.58f),
            style = Stroke(stroke, cap = StrokeCap.Round)
        )
        drawRoundRect(
            color = tint,
            topLeft = Offset(size.width * 0.18f, size.height * 0.43f),
            size = Size(size.width * 0.64f, size.height * 0.49f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(stroke)
        )
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

private fun DeviceLightSystemCondition.statusLabelRes(): Int = when (this) {
    DeviceLightSystemCondition.NORMAL -> R.string.device_light_system_condition_normal
    DeviceLightSystemCondition.PROTECTION_ACTIVE ->
        R.string.device_light_system_condition_protection
    DeviceLightSystemCondition.SENSOR_FAIL_SAFE ->
        R.string.device_light_system_condition_sensor_fail_safe
    DeviceLightSystemCondition.FAN_FAULT -> R.string.device_light_system_condition_fan_fault
}

private fun DeviceLightSystemCondition.statusColor(visuals: DeviceLightSystemVisuals): Color =
    when (this) {
        DeviceLightSystemCondition.NORMAL -> visuals.colors.action
        DeviceLightSystemCondition.PROTECTION_ACTIVE -> visuals.colors.card.warning
        DeviceLightSystemCondition.SENSOR_FAIL_SAFE,
        DeviceLightSystemCondition.FAN_FAULT -> visuals.colors.card.danger
    }

private fun contrastingColor(tint: Color): Color =
    if (tint.luminance() > 0.5f) Color.Black else Color.White

private fun Color.luminance(): Float = red * 0.299f + green * 0.587f + blue * 0.114f
