package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.automatic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingOperatingState
import com.aqua.aqualight.ui.common.cooling.AquaCoolingTemperatureChartSpec
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardTypography
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.toCoolingDisplayPercentOrNull
import kotlin.math.ceil
import kotlin.math.floor

@Composable
internal fun AutomaticTemperatureRangeVisual(
    startTemperatureC: Double?,
    maximumTemperatureC: Double?,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    // This is a threshold-only visualization. Runtime fan targets, including the product-specific
    // minimum running duty, remain firmware-owned and arrive through telemetry.
    val range = automaticTemperatureVisualRange(startTemperatureC, maximumTemperatureC)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AquaCoolingAutomaticGeometry.rangeLegendGap)
    ) {
        AutomaticTemperatureRangeTrack(range, colors, typography)
        AutomaticTemperatureZoneLegend(colors, typography)
    }
}

@Composable
private fun AutomaticTemperatureRangeTrack(
    range: AutomaticTemperatureVisualRange,
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(AquaCoolingAutomaticGeometry.rangeVisualHeight)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawAutomaticTemperatureRange(range = range, colors = colors)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            BasicText(
                text = automaticAxisTemperatureText(range.lowerBound),
                style = typography.micro.copy(color = colors.secondaryText)
            )
            BasicText(
                text = automaticAxisTemperatureText(range.upperBound),
                style = typography.micro.copy(color = colors.secondaryText)
            )
        }
    }
}

private fun DrawScope.drawAutomaticTemperatureRange(
    range: AutomaticTemperatureVisualRange,
    colors: AquaDeviceCardColors
) {
    val padding = AquaCoolingAutomaticGeometry.rangeTrackHorizontalPadding.toPx()
    val trackWidth = (size.width - padding * 2f).coerceAtLeast(1f)
    val centerY = size.height * RANGE_TRACK_VERTICAL_FRACTION
    val trackStroke = AquaCoolingAutomaticGeometry.rangeTrackHeight.toPx()
    drawLine(
        color = colors.secondaryText.copy(
            alpha = AquaCoolingAutomaticAlpha.rangeInactiveTrack
        ),
        start = Offset(padding, centerY),
        end = Offset(padding + trackWidth, centerY),
        strokeWidth = trackStroke,
        cap = StrokeCap.Round
    )
    range.markerFractions?.let { (startFraction, maximumFraction) ->
        val startX = padding + trackWidth * startFraction
        val maximumX = padding + trackWidth * maximumFraction
        drawLine(
            color = colors.accent.copy(alpha = AquaCoolingAutomaticAlpha.rangeActiveTrack),
            start = Offset(startX, centerY),
            end = Offset(maximumX, centerY),
            strokeWidth = trackStroke,
            cap = StrokeCap.Round
        )
        listOf(startX, maximumX).forEach { markerX ->
            drawTemperatureRangeMarker(markerX, centerY, colors)
        }
    }
}

private fun DrawScope.drawTemperatureRangeMarker(
    markerX: Float,
    centerY: Float,
    colors: AquaDeviceCardColors
) {
    drawCircle(
        color = colors.primaryText,
        radius = AquaCoolingAutomaticGeometry.rangeMarkerRadius.toPx() +
            AquaCoolingAutomaticGeometry.rangeMarkerOutlineWidth.toPx(),
        center = Offset(markerX, centerY)
    )
    drawCircle(
        color = colors.accent.copy(alpha = AquaCoolingAutomaticAlpha.rangeMarkerFill),
        radius = AquaCoolingAutomaticGeometry.rangeMarkerRadius.toPx(),
        center = Offset(markerX, centerY)
    )
}

@Composable
private fun AutomaticTemperatureZoneLegend(
    colors: AquaDeviceCardColors,
    typography: AquaDeviceCardTypography
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        listOf(
            stringResource(R.string.device_cooling_automatic_zone_off),
            stringResource(R.string.device_cooling_automatic_zone_gradual),
            stringResource(R.string.device_cooling_automatic_zone_maximum)
        ).forEach { label ->
            BasicText(
                text = label,
                style = typography.micro.copy(
                    color = colors.secondaryText,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
        }
    }
}

private fun automaticTemperatureVisualRange(
    startTemperatureC: Double?,
    maximumTemperatureC: Double?
): AutomaticTemperatureVisualRange {
    val start = startTemperatureC?.takeIf(Double::isFinite)
    val maximum = maximumTemperatureC?.takeIf(Double::isFinite)
    val hasRange = start != null && maximum != null && maximum > start
    val lowerBound = if (hasRange) {
        floor(checkNotNull(start) - RANGE_VISUAL_MARGIN_C)
    } else {
        AquaCoolingTemperatureChartSpec.defaultMinimumC.toDouble()
    }
    val upperBound = if (hasRange) {
        ceil(checkNotNull(maximum) + RANGE_VISUAL_MARGIN_C)
            .coerceAtLeast(lowerBound + MINIMUM_RANGE_SPAN_C)
    } else {
        AquaCoolingTemperatureChartSpec.defaultMaximumC.toDouble()
    }
    return AutomaticTemperatureVisualRange(
        lowerBound = lowerBound,
        upperBound = upperBound,
        start = start,
        maximum = maximum
    )
}

private data class AutomaticTemperatureVisualRange(
    val lowerBound: Double,
    val upperBound: Double,
    val start: Double?,
    val maximum: Double?
) {
    private val span = (upperBound - lowerBound).coerceAtLeast(MINIMUM_RANGE_SPAN_C)

    val markerFractions: Pair<Float, Float>?
        get() = if (start != null && maximum != null && maximum > start) {
            ((start - lowerBound) / span).toFloat().coerceIn(0f, 1f) to
                ((maximum - lowerBound) / span).toFloat().coerceIn(0f, 1f)
        } else {
            null
        }
}

@Composable
internal fun automaticTemperatureText(value: Double?): String =
    if (value?.isFinite() == true) {
        stringResource(R.string.device_cooling_temperature_value_format, value)
    } else {
        stringResource(R.string.device_cooling_value_unavailable)
    }

@Composable
internal fun automaticFanPercentText(value: Double?): String {
    val displayPercent = value.toCoolingDisplayPercentOrNull()
    return if (displayPercent != null) {
        stringResource(
            R.string.device_cooling_percent_value_format,
            displayPercent
        )
    } else {
        stringResource(R.string.device_cooling_value_unavailable)
    }
}

@Composable
internal fun automaticRuntimeStatusText(operatingState: DeviceCoolingOperatingState?): String =
    when (operatingState) {
        DeviceCoolingOperatingState.COOLING ->
            stringResource(R.string.device_cooling_automatic_status_cooling)
        DeviceCoolingOperatingState.IDLE ->
            stringResource(R.string.device_cooling_automatic_status_waiting)
        DeviceCoolingOperatingState.MANUAL ->
            stringResource(R.string.device_cooling_mode_manual)
        DeviceCoolingOperatingState.PROGRAM ->
            stringResource(R.string.device_cooling_mode_program)
        DeviceCoolingOperatingState.FAULT ->
            stringResource(R.string.device_cooling_status_fault)
        null -> stringResource(R.string.device_cooling_value_unavailable)
}

@Composable
private fun automaticAxisTemperatureText(value: Double): String =
    stringResource(R.string.device_cooling_temperature_axis_value_format, value)

private const val RANGE_VISUAL_MARGIN_C = 3.0
private const val MINIMUM_RANGE_SPAN_C = 1.0
private const val RANGE_TRACK_VERTICAL_FRACTION = 0.52f
