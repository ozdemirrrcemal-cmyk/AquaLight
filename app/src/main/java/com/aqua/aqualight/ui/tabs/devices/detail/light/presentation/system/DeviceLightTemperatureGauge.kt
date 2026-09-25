package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.system

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.aqua.aqualight.application.devices.light.system.DeviceLightTemperatureSensorState
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.toCommercialLightSensorStatusRes

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
internal fun DeviceLightTemperatureStatus(
    condition: DeviceLightSystemCondition,
    sensorState: DeviceLightTemperatureSensorState,
    visuals: DeviceLightSystemVisuals,
    modifier: Modifier = Modifier
) {
    val statusColor = condition.statusColor(visuals)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(DeviceLightSystemGeometry.temperatureStatusHeight),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = stringResource(condition.statusLabelRes(sensorState)),
            style = visuals.typography.caption.copy(
                color = statusColor,
                textAlign = TextAlign.Center
            )
        )
    }
}

private fun Double?.gaugeFraction(): Float = this?.toFloat()?.let { value ->
    ((value - DeviceLightSystemGeometry.gaugeMinimumTemperature) /
        (DeviceLightSystemGeometry.gaugeMaximumTemperature -
            DeviceLightSystemGeometry.gaugeMinimumTemperature)).coerceIn(0f, 1f)
} ?: 0f

private fun DeviceLightSystemCondition.statusLabelRes(
    sensorState: DeviceLightTemperatureSensorState
): Int = when (this) {
    DeviceLightSystemCondition.NORMAL -> R.string.device_light_system_status_normal
    DeviceLightSystemCondition.PROTECTION_ACTIVE ->
        R.string.device_light_system_condition_protection
    DeviceLightSystemCondition.SENSOR_FAIL_SAFE -> if (
        sensorState == DeviceLightTemperatureSensorState.HEALTHY
    ) {
        R.string.device_light_system_condition_sensor_fail_safe
    } else {
        sensorState.toCommercialLightSensorStatusRes()
    }
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
