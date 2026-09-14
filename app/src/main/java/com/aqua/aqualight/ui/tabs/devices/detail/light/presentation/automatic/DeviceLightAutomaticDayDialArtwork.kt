package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import kotlin.math.cos
import kotlin.math.sin

internal fun DrawScope.drawColorDialTrack(
    visuals: DeviceLightAutomaticEditorVisuals,
    strokeWidth: Float
) {
    val alpha = DeviceLightAutomaticEditorAlpha.neutralDial
    drawCircle(
        brush = Brush.sweepGradient(
            listOf(
                visuals.colors.card.warning.copy(alpha = alpha),
                visuals.colors.green.copy(alpha = alpha),
                visuals.colors.blue.copy(alpha = alpha),
                visuals.colors.shrimp.copy(alpha = alpha),
                visuals.colors.card.warning.copy(alpha = alpha)
            )
        ),
        style = Stroke(width = strokeWidth)
    )
}

internal fun DrawScope.drawTransitionArc(arc: DayDialTransitionArc) {
    if (arc.sweepDegrees <= NO_SWEEP_DEGREES) return
    val segmentSweep = arc.sweepDegrees / TRANSITION_SEGMENT_COUNT
    repeat(TRANSITION_SEGMENT_COUNT) { index ->
        val fraction = (index + HALF_SEGMENT).toFloat() / TRANSITION_SEGMENT_COUNT
        drawArc(
            color = lerp(arc.startColor, arc.endColor, fraction),
            startAngle = arc.startDegrees + segmentSweep * index +
                DeviceLightAutomaticDialSpec.topOriginDegrees,
            sweepAngle = segmentSweep + TRANSITION_OVERLAP_DEGREES,
            useCenter = false,
            style = Stroke(width = arc.strokeWidth, cap = StrokeCap.Butt)
        )
    }
}

@Composable
internal fun DayDialEventIcon(kind: DayDialEventKind, color: Color) {
    Canvas(Modifier.size(DeviceLightAutomaticEditorGeometry.dialEventIconSize)) {
        val centerPoint = Offset(size.width / HALF_DIVISOR, size.height / HALF_DIVISOR)
        val radius = size.minDimension * EVENT_CORE_RADIUS_FRACTION
        when (kind) {
            DayDialEventKind.SUNRISE -> drawSunriseIcon(centerPoint, radius, color)
            DayDialEventKind.SUNSET -> drawSunsetIcon(centerPoint, radius, color)
        }
    }
}

private fun DrawScope.drawSunriseIcon(centerPoint: Offset, radius: Float, color: Color) {
    drawCircle(color = color, radius = radius, center = centerPoint)
    repeat(EVENT_RAY_COUNT) { index ->
        val angle = index.toDouble() * FULL_ROTATION_RADIANS / EVENT_RAY_COUNT
        drawLine(
            color = color,
            start = centerPoint.dialRadialOffset(angle, radius * EVENT_RAY_START_SCALE),
            end = centerPoint.dialRadialOffset(angle, radius * EVENT_RAY_END_SCALE),
            strokeWidth = DeviceLightAutomaticEditorGeometry.dialEventStrokeWidth.toPx(),
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawSunsetIcon(centerPoint: Offset, radius: Float, color: Color) {
    drawArc(
        color = color,
        startAngle = SUNSET_ARC_START_DEGREES,
        sweepAngle = SUNSET_ARC_SWEEP_DEGREES,
        useCenter = false,
        topLeft = Offset(centerPoint.x - radius, centerPoint.y - radius),
        size = Size(radius * HALF_DIVISOR, radius * HALF_DIVISOR),
        style = Stroke(
            width = DeviceLightAutomaticEditorGeometry.dialEventStrokeWidth.toPx(),
            cap = StrokeCap.Round
        )
    )
    drawLine(
        color = color,
        start = Offset(0f, centerPoint.y),
        end = Offset(size.width, centerPoint.y),
        strokeWidth = DeviceLightAutomaticEditorGeometry.dialEventStrokeWidth.toPx(),
        cap = StrokeCap.Round
    )
}

internal fun Offset.dialRadialOffset(angle: Double, radius: Float): Offset = this + Offset(
    x = (cos(angle) * radius).toFloat(),
    y = (sin(angle) * radius).toFloat()
)

internal enum class DayDialEventKind { SUNRISE, SUNSET }

internal data class DayDialTransitionArc(
    val startDegrees: Float,
    val sweepDegrees: Float,
    val startColor: Color,
    val endColor: Color,
    val strokeWidth: Float
)

private const val NO_SWEEP_DEGREES = 0f
private const val TRANSITION_SEGMENT_COUNT = 12
private const val HALF_SEGMENT = 0.5
private const val TRANSITION_OVERLAP_DEGREES = 0.35f
private const val HALF_DIVISOR = 2f
private const val FULL_ROTATION_RADIANS = Math.PI * 2.0
private const val EVENT_CORE_RADIUS_FRACTION = 0.18f
private const val EVENT_RAY_COUNT = 8
private const val EVENT_RAY_START_SCALE = 1.55f
private const val EVENT_RAY_END_SCALE = 2.20f
private const val SUNSET_ARC_START_DEGREES = 180f
private const val SUNSET_ARC_SWEEP_DEGREES = 180f
