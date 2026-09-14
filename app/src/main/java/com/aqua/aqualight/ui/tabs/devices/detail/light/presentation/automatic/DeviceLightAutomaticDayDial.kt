package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.aqua.aqualight.R
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun DayDial(
    schedule: DaySimulationSchedule?,
    visuals: DeviceLightAutomaticEditorVisuals,
    modifier: Modifier
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) { drawDayDial(schedule, visuals) }
        DialAxisLabels(visuals)
        DialCenterCopy(schedule, visuals)
    }
}

private fun DrawScope.drawDayDial(
    schedule: DaySimulationSchedule?,
    visuals: DeviceLightAutomaticEditorVisuals
) {
    val stroke = DeviceLightAutomaticEditorGeometry.dialStrokeWidth.toPx()
    drawCircle(
        color = visuals.colors.card.mediaOutline.copy(
            alpha = DeviceLightAutomaticEditorAlpha.neutralDial
        ),
        style = Stroke(width = stroke)
    )
    drawDialGuides(visuals)
    if (schedule != null) {
        drawScheduleArcs(schedule, visuals, stroke)
        drawDialMarker(schedule.startTimeMs, visuals.colors.red)
        drawDialMarker(schedule.endTimeMs, visuals.colors.blue)
    }
}

private fun DrawScope.drawScheduleArcs(
    schedule: DaySimulationSchedule,
    visuals: DeviceLightAutomaticEditorVisuals,
    strokeWidth: Float
) {
    val start = schedule.startTimeMs.toDayDegrees()
    val duration = schedule.durationMs.toDayDegrees()
    val ramp = schedule.rampDurationMs.toDayDegrees()
    val hold = (duration - ramp - ramp).coerceAtLeast(NO_SWEEP_DEGREES)
    listOf(
        DialArc(start, ramp, visuals.colors.red),
        DialArc(start + ramp, hold, visuals.colors.action),
        DialArc(start + ramp + hold, ramp, visuals.colors.blue)
    ).forEach { arc ->
        drawArc(
            color = arc.color,
            startAngle = arc.startDegrees + DeviceLightAutomaticDialSpec.topOriginDegrees,
            sweepAngle = arc.sweepDegrees,
            useCenter = false,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }
}

private fun DrawScope.drawDialGuides(visuals: DeviceLightAutomaticEditorVisuals) {
    val radius = size.minDimension / HALF_DIVISOR
    val inset = radius * DeviceLightAutomaticDialSpec.radiusInsetFraction
    val stroke = DeviceLightAutomaticEditorGeometry.dialGuideStrokeWidth.toPx()
    repeat(DIAL_GUIDE_COUNT) { index ->
        val angle = index.toDouble() * FULL_ROTATION_RADIANS / DIAL_GUIDE_COUNT
        val outer = center.radialOffset(angle, radius)
        val inner = center.radialOffset(angle, radius - inset)
        drawLine(
            color = visuals.colors.card.secondaryText.copy(
                alpha = DeviceLightAutomaticEditorAlpha.dialGuide
            ),
            start = inner,
            end = outer,
            strokeWidth = stroke
        )
    }
}

private fun DrawScope.drawDialMarker(timeMs: Long, color: Color) {
    val radius = size.minDimension / HALF_DIVISOR
    val angle = Math.toRadians(
        timeMs.toDayDegrees().toDouble() - DeviceLightAutomaticDialSpec.markerAngleOffsetDegrees
    )
    val markerCenter = center.radialOffset(angle, radius)
    drawCircle(
        color = Color.White,
        radius = DeviceLightAutomaticEditorGeometry.dialMarkerRadius.toPx() +
            DeviceLightAutomaticEditorGeometry.dialMarkerOutlineWidth.toPx(),
        center = markerCenter
    )
    drawCircle(
        color = color,
        radius = DeviceLightAutomaticEditorGeometry.dialMarkerRadius.toPx(),
        center = markerCenter
    )
}

private fun Offset.radialOffset(angle: Double, radius: Float): Offset = this + Offset(
    x = (cos(angle) * radius).toFloat(),
    y = (sin(angle) * radius).toFloat()
)

@Composable
private fun DialAxisLabels(visuals: DeviceLightAutomaticEditorVisuals) {
    val style = visuals.typography.micro.copy(
        color = visuals.colors.card.secondaryText,
        textAlign = TextAlign.Center
    )
    Box(Modifier.fillMaxSize()) {
        BasicText(DIAL_TOP_LABEL, Modifier.align(Alignment.TopCenter), style)
        BasicText(DIAL_LEFT_LABEL, Modifier.align(Alignment.CenterStart), style)
        BasicText(DIAL_RIGHT_LABEL, Modifier.align(Alignment.CenterEnd), style)
        BasicText(DIAL_BOTTOM_LABEL, Modifier.align(Alignment.BottomCenter), style)
    }
}

@Composable
private fun DialCenterCopy(
    schedule: DaySimulationSchedule?,
    visuals: DeviceLightAutomaticEditorVisuals
) {
    Column(
        modifier = Modifier.width(DeviceLightAutomaticEditorGeometry.dialCenterWidth),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (schedule == null) {
            BasicText(
                text = stringResource(R.string.device_light_auto_editor_complete_settings),
                style = visuals.typography.micro.copy(
                    color = visuals.colors.card.secondaryText,
                    textAlign = TextAlign.Center
                )
            )
        } else {
            DialValue(
                stringResource(R.string.device_light_auto_editor_start_short),
                automaticEditorTimeText(schedule.startTimeMs),
                visuals
            )
            DialValue(
                stringResource(R.string.device_light_auto_editor_end_short),
                automaticEditorTimeText(schedule.endTimeMs),
                visuals
            )
            BasicText(
                text = stringResource(
                    R.string.device_light_auto_editor_ramp_short,
                    (schedule.rampDurationMs / MILLIS_PER_MINUTE).toInt()
                ),
                style = visuals.typography.micro.copy(color = visuals.colors.card.secondaryText)
            )
        }
    }
}

@Composable
private fun DialValue(
    label: String,
    value: String,
    visuals: DeviceLightAutomaticEditorVisuals
) {
    BasicText(label, style = visuals.typography.micro.copy(color = visuals.colors.card.secondaryText))
    BasicText(value, style = visuals.typography.caption.copy(color = visuals.colors.card.primaryText))
}

private fun Long.toDayDegrees(): Float =
    toFloat() / MILLIS_PER_DAY * DeviceLightAutomaticDialSpec.fullCircleDegrees

internal data class DaySimulationSchedule(
    val startTimeMs: Long,
    val endTimeMs: Long,
    val rampDurationMs: Long,
    val durationMs: Long
)

private data class DialArc(val startDegrees: Float, val sweepDegrees: Float, val color: Color)

private const val DIAL_GUIDE_COUNT = 12
private const val DIAL_TOP_LABEL = "24"
private const val DIAL_LEFT_LABEL = "06"
private const val DIAL_RIGHT_LABEL = "18"
private const val DIAL_BOTTOM_LABEL = "12"
private const val HALF_DIVISOR = 2f
private const val FULL_ROTATION_RADIANS = Math.PI * 2.0
private const val NO_SWEEP_DEGREES = 0f
private const val MILLIS_PER_MINUTE = 60_000L
private const val MILLIS_PER_DAY = 86_400_000L
