package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.system

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.light.system.DeviceLightSystemCondition

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
            drawTemperatureGaugeRing(temperatureCelsius, statusColor, visuals)
        }
        DeviceLightTemperatureGaugeLabel(temperatureCelsius, condition, statusColor, visuals)
    }
}

private fun DrawScope.drawTemperatureGaugeRing(
    temperatureCelsius: Double?,
    statusColor: Color,
    visuals: DeviceLightSystemVisuals
) {
    val stroke = DeviceLightSystemGeometry.gaugeTrackWidth.toPx()
    val inset = stroke / GAUGE_STROKE_INSET_DIVISOR
    val bounds = Rect(inset, inset, size.width - inset, size.height - inset)
    drawArc(
        color = visuals.colors.card.mediaOutline.copy(alpha = DeviceLightSystemAlpha.track),
        startAngle = DeviceLightSystemGeometry.gaugeStartAngle,
        sweepAngle = DeviceLightSystemGeometry.gaugeSweep,
        useCenter = false,
        topLeft = bounds.topLeft,
        size = bounds.size,
        style = Stroke(stroke, cap = StrokeCap.Round)
    )
    drawArc(
        color = statusColor,
        startAngle = DeviceLightSystemGeometry.gaugeStartAngle,
        sweepAngle = DeviceLightSystemGeometry.gaugeSweep * temperatureCelsius.gaugeFraction(),
        useCenter = false,
        topLeft = bounds.topLeft,
        size = bounds.size,
        style = Stroke(stroke, cap = StrokeCap.Round)
    )
}

@Composable
private fun DeviceLightTemperatureGaugeLabel(
    temperatureCelsius: Double?,
    condition: DeviceLightSystemCondition,
    statusColor: Color,
    visuals: DeviceLightSystemVisuals
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BasicText(
            text = temperatureCelsius?.let { value ->
                stringResource(R.string.device_light_system_temperature, value)
            } ?: stringResource(R.string.device_light_system_temperature_unavailable),
            style = visuals.typography.title.copy(
                fontSize = DeviceLightSystemGeometry.gaugeValueSize,
                color = visuals.colors.card.primaryText,
                textAlign = TextAlign.Center
            )
        )
        Spacer(Modifier.height(DeviceLightSystemGeometry.gaugeStatusGap))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(
                Modifier
                    .width(DeviceLightSystemGeometry.statusDotSize)
                    .height(DeviceLightSystemGeometry.statusDotSize)
            ) { drawCircle(statusColor) }
            Spacer(Modifier.width(DeviceLightSystemGeometry.gaugeStatusGap))
            BasicText(
                text = stringResource(condition.statusLabelRes()),
                style = visuals.typography.caption.copy(color = statusColor)
            )
        }
    }
}

private fun Double?.gaugeFraction(): Float = this?.toFloat()?.let { value ->
    ((value - DeviceLightSystemGeometry.gaugeMinimumTemperature) /
        (DeviceLightSystemGeometry.gaugeMaximumTemperature -
            DeviceLightSystemGeometry.gaugeMinimumTemperature)).coerceIn(0f, 1f)
} ?: 0f

private fun DeviceLightSystemCondition.statusLabelRes(): Int = when (this) {
    DeviceLightSystemCondition.NORMAL -> R.string.device_light_system_status_normal
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

private const val GAUGE_STROKE_INSET_DIVISOR = 2f
