package com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.card

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

internal enum class DosingMetricGlyphType {
    DOSE,
    DAYS
}

@Composable
internal fun DosingMetricGlyph(
    type: DosingMetricGlyphType,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        when (type) {
            DosingMetricGlyphType.DOSE -> drawDoseGlyph(tint)
            DosingMetricGlyphType.DAYS -> drawCalendarGlyph(tint)
        }
    }
}

@Composable
internal fun DosingProgramModeGlyph(
    mode: DosingProgramModeUiState,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        when (mode) {
            DosingProgramModeUiState.SINGLE -> drawSingleModeGlyph(tint)
            DosingProgramModeUiState.HOURLY_24 -> drawClockModeGlyph(tint, showEvents = false)
            DosingProgramModeUiState.CUSTOM_PERIODS -> drawCustomModeGlyph(tint)
            DosingProgramModeUiState.TIMER -> drawClockModeGlyph(tint, showEvents = true)
        }
    }
}

@Composable
internal fun DosingEmptyStateGlyph(
    tint: Color,
    badgeSurface: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        drawDoseGlyph(tint)
        drawPlusBadge(
            color = tint,
            surface = badgeSurface,
            center = Offset(size.width * BADGE_CENTER_X, size.height * BADGE_CENTER_Y),
            radius = size.minDimension * BADGE_RADIUS
        )
    }
}

@Composable
internal fun DosingManualDoseGlyph(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        drawDoseGlyph(tint)
        val center = Offset(size.width * BADGE_CENTER_X, size.height * BADGE_CENTER_Y)
        val arm = size.minDimension * MANUAL_PLUS_ARM
        drawLine(
            color = tint,
            start = Offset(center.x - arm, center.y),
            end = Offset(center.x + arm, center.y),
            strokeWidth = MANUAL_PLUS_WIDTH.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(center.x, center.y - arm),
            end = Offset(center.x, center.y + arm),
            strokeWidth = MANUAL_PLUS_WIDTH.toPx(),
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawDoseGlyph(color: Color) {
    drawPath(
        path = dosingDropPath(),
        color = color,
        style = Stroke(
            width = GLYPH_STROKE.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
    drawCircle(
        color = color.copy(alpha = HIGHLIGHT_ALPHA),
        radius = size.minDimension * HIGHLIGHT_RADIUS,
        center = Offset(size.width * HIGHLIGHT_X, size.height * HIGHLIGHT_Y)
    )
}

private fun DrawScope.dosingDropPath(): Path = Path().apply {
    moveTo(size.width * DROP_CENTER_X, size.height * DROP_TOP_Y)
    cubicTo(
        size.width * DROP_RIGHT_UPPER_X,
        size.height * DROP_UPPER_CONTROL_Y,
        size.width * DROP_RIGHT_X,
        size.height * DROP_MIDDLE_CONTROL_Y,
        size.width * DROP_RIGHT_X,
        size.height * DROP_BODY_Y
    )
    cubicTo(
        size.width * DROP_RIGHT_X,
        size.height * DROP_BOTTOM_CONTROL_Y,
        size.width * DROP_LEFT_X,
        size.height * DROP_BOTTOM_CONTROL_Y,
        size.width * DROP_LEFT_X,
        size.height * DROP_BODY_Y
    )
    cubicTo(
        size.width * DROP_LEFT_X,
        size.height * DROP_MIDDLE_CONTROL_Y,
        size.width * DROP_LEFT_UPPER_X,
        size.height * DROP_UPPER_CONTROL_Y,
        size.width * DROP_CENTER_X,
        size.height * DROP_TOP_Y
    )
    close()
}

private fun DrawScope.drawPlusBadge(
    color: Color,
    surface: Color,
    center: Offset,
    radius: Float
) {
    val arm = size.minDimension * BADGE_PLUS_ARM
    drawCircle(color = surface, radius = radius, center = center)
    drawCircle(
        color = color.copy(alpha = BADGE_OUTLINE_ALPHA),
        radius = radius,
        center = center,
        style = Stroke(width = BADGE_OUTLINE_WIDTH.toPx())
    )
    drawLine(
        color = color,
        start = Offset(center.x - arm, center.y),
        end = Offset(center.x + arm, center.y),
        strokeWidth = BADGE_PLUS_WIDTH.toPx(),
        cap = StrokeCap.Round
    )
    drawLine(
        color = color,
        start = Offset(center.x, center.y - arm),
        end = Offset(center.x, center.y + arm),
        strokeWidth = BADGE_PLUS_WIDTH.toPx(),
        cap = StrokeCap.Round
    )
}

private fun DrawScope.drawCalendarGlyph(color: Color) {
    val stroke = GLYPH_STROKE.toPx()
    val left = size.width * CALENDAR_LEFT_X
    val top = size.height * CALENDAR_TOP_Y
    val width = size.width * CALENDAR_WIDTH
    val height = size.height * CALENDAR_HEIGHT
    drawRoundRect(
        color = color,
        topLeft = Offset(left, top),
        size = Size(width, height),
        cornerRadius = CornerRadius(CALENDAR_CORNER_RADIUS.toPx()),
        style = Stroke(width = stroke)
    )
    drawLine(
        color = color,
        start = Offset(left, size.height * CALENDAR_HEADER_Y),
        end = Offset(left + width, size.height * CALENDAR_HEADER_Y),
        strokeWidth = stroke,
        cap = StrokeCap.Round
    )
    CALENDAR_BINDERS.forEach { x ->
        drawLine(
            color = color,
            start = Offset(size.width * x, size.height * CALENDAR_BINDER_TOP_Y),
            end = Offset(size.width * x, size.height * CALENDAR_BINDER_BOTTOM_Y),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
    CALENDAR_DOTS.forEach { x ->
        drawCircle(
            color = color,
            radius = CALENDAR_DOT_RADIUS.toPx(),
            center = Offset(size.width * x, size.height * CALENDAR_DOT_Y)
        )
    }
}

private fun DrawScope.drawSingleModeGlyph(color: Color) {
    val center = Offset(size.width / 2f, size.height / 2f)
    drawCircle(
        color = color.copy(alpha = MODE_RING_ALPHA),
        radius = size.minDimension * MODE_RING_RADIUS,
        center = center,
        style = Stroke(width = MODE_STROKE.toPx())
    )
    drawCircle(
        color = color,
        radius = size.minDimension * MODE_CORE_RADIUS,
        center = center
    )
}

private fun DrawScope.drawClockModeGlyph(color: Color, showEvents: Boolean) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val radius = size.minDimension * CLOCK_RADIUS
    drawCircle(
        color = color,
        radius = radius,
        center = center,
        style = Stroke(width = MODE_STROKE.toPx())
    )
    if (showEvents) {
        TIMER_EVENT_ANGLES.forEach { angle ->
            val radians = Math.toRadians(angle.toDouble())
            drawCircle(
                color = color,
                radius = TIMER_EVENT_RADIUS.toPx(),
                center = Offset(
                    x = center.x + cos(radians).toFloat() * radius * TIMER_EVENT_DISTANCE,
                    y = center.y + sin(radians).toFloat() * radius * TIMER_EVENT_DISTANCE
                )
            )
        }
    } else {
        CLOCK_TICK_ANGLES.forEach { angle ->
            val radians = Math.toRadians(angle.toDouble())
            val startDistance = radius * CLOCK_TICK_START
            drawLine(
                color = color,
                start = Offset(
                    center.x + cos(radians).toFloat() * startDistance,
                    center.y + sin(radians).toFloat() * startDistance
                ),
                end = Offset(
                    center.x + cos(radians).toFloat() * radius,
                    center.y + sin(radians).toFloat() * radius
                ),
                strokeWidth = CLOCK_TICK_WIDTH.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

private fun DrawScope.drawCustomModeGlyph(color: Color) {
    CUSTOM_SEGMENTS.forEachIndexed { index, segment ->
        drawRoundRect(
            color = color.copy(alpha = CUSTOM_SEGMENT_ALPHAS[index]),
            topLeft = Offset(size.width * segment.first, size.height * CUSTOM_TOP_Y),
            size = Size(size.width * segment.second, size.height * CUSTOM_HEIGHT),
            cornerRadius = CornerRadius(CUSTOM_CORNER_RADIUS.toPx())
        )
    }
}

private const val DROP_CENTER_X = 0.50f
private const val DROP_TOP_Y = 0.05f
private const val DROP_RIGHT_UPPER_X = 0.63f
private const val DROP_LEFT_UPPER_X = 0.37f
private const val DROP_RIGHT_X = 0.84f
private const val DROP_LEFT_X = 0.16f
private const val DROP_UPPER_CONTROL_Y = 0.22f
private const val DROP_MIDDLE_CONTROL_Y = 0.48f
private const val DROP_BODY_Y = 0.66f
private const val DROP_BOTTOM_CONTROL_Y = 0.93f
private const val HIGHLIGHT_ALPHA = 0.72f
private const val HIGHLIGHT_RADIUS = 0.065f
private const val HIGHLIGHT_X = 0.39f
private const val HIGHLIGHT_Y = 0.61f
private const val BADGE_CENTER_X = 0.73f
private const val BADGE_CENTER_Y = 0.72f
private const val BADGE_RADIUS = 0.20f
private const val BADGE_PLUS_ARM = 0.075f
private const val MANUAL_PLUS_ARM = 0.10f
private const val BADGE_OUTLINE_ALPHA = 0.70f
private const val CALENDAR_LEFT_X = 0.10f
private const val CALENDAR_TOP_Y = 0.18f
private const val CALENDAR_WIDTH = 0.80f
private const val CALENDAR_HEIGHT = 0.70f
private const val CALENDAR_HEADER_Y = 0.40f
private const val CALENDAR_BINDER_TOP_Y = 0.08f
private const val CALENDAR_BINDER_BOTTOM_Y = 0.28f
private const val CALENDAR_DOT_Y = 0.62f
private const val MODE_RING_ALPHA = 0.42f
private const val MODE_RING_RADIUS = 0.40f
private const val MODE_CORE_RADIUS = 0.17f
private const val CLOCK_RADIUS = 0.40f
private const val CLOCK_TICK_START = 0.72f
private const val TIMER_EVENT_DISTANCE = 0.68f
private const val CUSTOM_TOP_Y = 0.27f
private const val CUSTOM_HEIGHT = 0.46f
private val CALENDAR_BINDERS = listOf(0.32f, 0.68f)
private val CALENDAR_DOTS = listOf(0.30f, 0.50f, 0.70f)
private val CLOCK_TICK_ANGLES = listOf(0f, 90f, 180f, 270f)
private val TIMER_EVENT_ANGLES = listOf(-90f, 20f, 145f)
private val CUSTOM_SEGMENTS = listOf(0.08f to 0.23f, 0.38f to 0.19f, 0.64f to 0.28f)
private val CUSTOM_SEGMENT_ALPHAS = listOf(0.72f, 1f, 0.84f)
private val GLYPH_STROKE = 1.45.dp
private val BADGE_OUTLINE_WIDTH = 1.10.dp
private val BADGE_PLUS_WIDTH = 1.55.dp
private val MANUAL_PLUS_WIDTH = 1.35.dp
private val CALENDAR_CORNER_RADIUS = 2.5.dp
private val CALENDAR_DOT_RADIUS = 1.1.dp
private val MODE_STROKE = 1.25.dp
private val CLOCK_TICK_WIDTH = 1.dp
private val TIMER_EVENT_RADIUS = 1.25.dp
private val CUSTOM_CORNER_RADIUS = 2.dp
