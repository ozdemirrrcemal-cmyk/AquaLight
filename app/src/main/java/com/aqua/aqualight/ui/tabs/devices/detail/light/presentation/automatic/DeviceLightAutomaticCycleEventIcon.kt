package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun AutomaticCycleEventIcon(
    kind: AutomaticCycleEventKind,
    color: Color,
    modifier: Modifier
) {
    Canvas(modifier) {
        val centerPoint = Offset(
            size.width * EVENT_CENTER_X,
            size.height * kind.centerYFraction
        )
        val radius = size.minDimension * EVENT_RADIUS_FRACTION
        drawEventHorizon(color)
        drawEventSun(centerPoint, radius, color)
        drawEventRays(centerPoint, radius, color, kind)
    }
}

private fun DrawScope.drawEventHorizon(color: Color) {
    val stroke = DeviceLightAutomaticEditorGeometry.dialEventStrokeWidth.toPx()
    drawLine(
        color = color,
        start = Offset(size.width * HORIZON_LEFT_X, size.height * HORIZON_Y),
        end = Offset(size.width * HORIZON_RIGHT_X, size.height * HORIZON_Y),
        strokeWidth = stroke,
        cap = StrokeCap.Round
    )
    drawLine(
        color = color,
        start = Offset(size.width * LOWER_HORIZON_LEFT_X, size.height * LOWER_HORIZON_Y),
        end = Offset(size.width * LOWER_HORIZON_RIGHT_X, size.height * LOWER_HORIZON_Y),
        strokeWidth = stroke,
        cap = StrokeCap.Round
    )
}

private fun DrawScope.drawEventSun(centerPoint: Offset, radius: Float, color: Color) {
    drawArc(
        color = color,
        startAngle = SUN_ARC_START_DEGREES,
        sweepAngle = SUN_ARC_SWEEP_DEGREES,
        useCenter = false,
        topLeft = Offset(centerPoint.x - radius, centerPoint.y - radius),
        size = Size(radius * HALF_DIVISOR, radius * HALF_DIVISOR),
        style = Stroke(
            width = DeviceLightAutomaticEditorGeometry.dialEventStrokeWidth.toPx(),
            cap = StrokeCap.Round
        )
    )
}

private fun DrawScope.drawEventRays(
    centerPoint: Offset,
    radius: Float,
    color: Color,
    kind: AutomaticCycleEventKind
) {
    val stroke = DeviceLightAutomaticEditorGeometry.dialEventStrokeWidth.toPx()
    EVENT_RAY_ANGLES_DEGREES.forEach { degrees ->
        val adjusted = degrees + kind.rayAngleOffsetDegrees
        val angle = Math.toRadians(adjusted)
        drawLine(
            color = color,
            start = centerPoint.eventRadialOffset(angle, radius * RAY_START_SCALE),
            end = centerPoint.eventRadialOffset(angle, radius * RAY_END_SCALE),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

private fun Offset.eventRadialOffset(angle: Double, radius: Float): Offset = this + Offset(
    x = (cos(angle) * radius).toFloat(),
    y = (sin(angle) * radius).toFloat()
)

internal enum class AutomaticCycleEventKind(
    val centerYFraction: Float,
    val rayAngleOffsetDegrees: Double
) {
    SUNRISE(SUNRISE_CENTER_Y, SUNRISE_RAY_OFFSET),
    SUNSET(SUNSET_CENTER_Y, SUNSET_RAY_OFFSET)
}

private val EVENT_RAY_ANGLES_DEGREES = listOf(
    EVENT_RAY_FIRST_DEGREES,
    EVENT_RAY_SECOND_DEGREES,
    EVENT_RAY_CENTER_DEGREES,
    EVENT_RAY_FOURTH_DEGREES,
    EVENT_RAY_LAST_DEGREES
)
private const val EVENT_RAY_FIRST_DEGREES = 200.0
private const val EVENT_RAY_SECOND_DEGREES = 235.0
private const val EVENT_RAY_CENTER_DEGREES = 270.0
private const val EVENT_RAY_FOURTH_DEGREES = 305.0
private const val EVENT_RAY_LAST_DEGREES = 340.0
private const val EVENT_CENTER_X = 0.50f
private const val SUNRISE_CENTER_Y = 0.61f
private const val SUNSET_CENTER_Y = 0.58f
private const val SUNRISE_RAY_OFFSET = 0.0
private const val SUNSET_RAY_OFFSET = 0.0
private const val EVENT_RADIUS_FRACTION = 0.21f
private const val HORIZON_LEFT_X = 0.08f
private const val HORIZON_RIGHT_X = 0.92f
private const val HORIZON_Y = 0.62f
private const val LOWER_HORIZON_LEFT_X = 0.22f
private const val LOWER_HORIZON_RIGHT_X = 0.78f
private const val LOWER_HORIZON_Y = 0.76f
private const val SUN_ARC_START_DEGREES = 180f
private const val SUN_ARC_SWEEP_DEGREES = 180f
private const val HALF_DIVISOR = 2f
private const val RAY_START_SCALE = 1.35f
private const val RAY_END_SCALE = 1.72f
