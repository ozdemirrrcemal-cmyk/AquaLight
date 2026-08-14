package com.aqua.aqualight.ui.tabs.devices.detail.dosing

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

@Composable
internal fun DosingCardMetricGlyph(
    icon: DosingCardMetricIcon,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        when (icon) {
            DosingCardMetricIcon.DOSE -> drawDoseGlyph(tint)
            DosingCardMetricIcon.SCHEDULE -> drawScheduleGlyph(tint)
            DosingCardMetricIcon.RESERVOIR -> drawReservoirGlyph(tint)
            DosingCardMetricIcon.MANUAL -> drawManualGlyph(tint)
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
        drawEmptyStateDoseGlyph(
            color = tint,
            badgeSurface = badgeSurface
        )
    }
}

internal enum class DosingCardMetricIcon {
    DOSE,
    SCHEDULE,
    RESERVOIR,
    MANUAL
}

private fun DrawScope.drawDoseGlyph(color: Color) {
    drawPath(
        path = dosingDropPath(),
        color = color,
        style = Stroke(
            width = DOSE_GLYPH_STROKE.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
    drawCircle(
        color = color.copy(alpha = DOSE_HIGHLIGHT_ALPHA),
        radius = size.minDimension * DOSE_HIGHLIGHT_RADIUS,
        center = Offset(
            x = size.width * DOSE_HIGHLIGHT_X,
            y = size.height * DOSE_HIGHLIGHT_Y
        )
    )
}

private fun DrawScope.dosingDropPath(): Path = Path().apply {
    moveTo(size.width * DOSE_CENTER_X, size.height * DOSE_TOP_Y)
    cubicTo(
        size.width * DOSE_RIGHT_UPPER_X,
        size.height * DOSE_UPPER_CONTROL_Y,
        size.width * DOSE_RIGHT_X,
        size.height * DOSE_MIDDLE_CONTROL_Y,
        size.width * DOSE_RIGHT_X,
        size.height * DOSE_BODY_Y
    )
    cubicTo(
        size.width * DOSE_RIGHT_X,
        size.height * DOSE_BOTTOM_CONTROL_Y,
        size.width * DOSE_LEFT_X,
        size.height * DOSE_BOTTOM_CONTROL_Y,
        size.width * DOSE_LEFT_X,
        size.height * DOSE_BODY_Y
    )
    cubicTo(
        size.width * DOSE_LEFT_X,
        size.height * DOSE_MIDDLE_CONTROL_Y,
        size.width * DOSE_LEFT_UPPER_X,
        size.height * DOSE_UPPER_CONTROL_Y,
        size.width * DOSE_CENTER_X,
        size.height * DOSE_TOP_Y
    )
    close()
}

private fun DrawScope.drawEmptyStateDoseGlyph(
    color: Color,
    badgeSurface: Color
) {
    drawDoseGlyph(color)

    val badgeCenter = Offset(
        x = size.width * EMPTY_BADGE_CENTER_X,
        y = size.height * EMPTY_BADGE_CENTER_Y
    )
    val badgeRadius = size.minDimension * EMPTY_BADGE_RADIUS
    val plusArm = size.minDimension * EMPTY_BADGE_PLUS_ARM

    drawCircle(color = badgeSurface, radius = badgeRadius, center = badgeCenter)
    drawCircle(
        color = color.copy(alpha = EMPTY_BADGE_OUTLINE_ALPHA),
        radius = badgeRadius,
        center = badgeCenter,
        style = Stroke(width = EMPTY_BADGE_OUTLINE_WIDTH.toPx())
    )
    drawLine(
        color = color,
        start = Offset(badgeCenter.x - plusArm, badgeCenter.y),
        end = Offset(badgeCenter.x + plusArm, badgeCenter.y),
        strokeWidth = EMPTY_BADGE_PLUS_WIDTH.toPx(),
        cap = StrokeCap.Round
    )
    drawLine(
        color = color,
        start = Offset(badgeCenter.x, badgeCenter.y - plusArm),
        end = Offset(badgeCenter.x, badgeCenter.y + plusArm),
        strokeWidth = EMPTY_BADGE_PLUS_WIDTH.toPx(),
        cap = StrokeCap.Round
    )
}

private fun DrawScope.drawScheduleGlyph(color: Color) {
    val stroke = SCHEDULE_GLYPH_STROKE.toPx()
    val centerY = size.height * SCHEDULE_CENTER_Y
    val nodeRadius = size.minDimension * SCHEDULE_NODE_RADIUS
    val left = size.width * SCHEDULE_LEFT_X
    val right = size.width * SCHEDULE_RIGHT_X

    drawLine(
        color = color.copy(alpha = SCHEDULE_LINE_ALPHA),
        start = Offset(left, centerY),
        end = Offset(right, centerY),
        strokeWidth = stroke,
        cap = StrokeCap.Round
    )
    SCHEDULE_NODE_X.forEachIndexed { index, x ->
        val filled = index == SCHEDULE_ACTIVE_NODE_INDEX
        drawCircle(
            color = if (filled) color else Color.Transparent,
            radius = nodeRadius,
            center = Offset(size.width * x, centerY)
        )
        drawCircle(
            color = color,
            radius = nodeRadius,
            center = Offset(size.width * x, centerY),
            style = Stroke(width = stroke)
        )
    }
}

private fun DrawScope.drawReservoirGlyph(color: Color) {
    val stroke = RESERVOIR_GLYPH_STROKE.toPx()
    val bodyLeft = size.width * RESERVOIR_LEFT_X
    val bodyTop = size.height * RESERVOIR_TOP_Y
    val bodyWidth = size.width * RESERVOIR_WIDTH
    val bodyHeight = size.height * RESERVOIR_HEIGHT
    val radius = CornerRadius(size.minDimension * RESERVOIR_CORNER_RATIO)

    drawRoundRect(
        color = color,
        topLeft = Offset(bodyLeft, bodyTop),
        size = Size(bodyWidth, bodyHeight),
        cornerRadius = radius,
        style = Stroke(width = stroke)
    )
    drawLine(
        color = color,
        start = Offset(size.width * RESERVOIR_CAP_LEFT_X, size.height * RESERVOIR_CAP_Y),
        end = Offset(size.width * RESERVOIR_CAP_RIGHT_X, size.height * RESERVOIR_CAP_Y),
        strokeWidth = stroke,
        cap = StrokeCap.Round
    )
    drawLine(
        color = color.copy(alpha = RESERVOIR_LEVEL_ALPHA),
        start = Offset(size.width * RESERVOIR_LEVEL_LEFT_X, size.height * RESERVOIR_LEVEL_Y),
        end = Offset(size.width * RESERVOIR_LEVEL_RIGHT_X, size.height * RESERVOIR_LEVEL_Y),
        strokeWidth = RESERVOIR_LEVEL_STROKE.toPx(),
        cap = StrokeCap.Round
    )
}

private fun DrawScope.drawManualGlyph(color: Color) {
    val center = Offset(size.width * MANUAL_CENTER_X, size.height * MANUAL_CENTER_Y)
    val radius = size.minDimension * MANUAL_RADIUS
    val stroke = MANUAL_GLYPH_STROKE.toPx()
    val arm = size.minDimension * MANUAL_PLUS_ARM

    drawCircle(
        color = color,
        radius = radius,
        center = center,
        style = Stroke(width = stroke)
    )
    drawLine(
        color = color,
        start = Offset(center.x - arm, center.y),
        end = Offset(center.x + arm, center.y),
        strokeWidth = stroke,
        cap = StrokeCap.Round
    )
    drawLine(
        color = color,
        start = Offset(center.x, center.y - arm),
        end = Offset(center.x, center.y + arm),
        strokeWidth = stroke,
        cap = StrokeCap.Round
    )
}

private const val DOSE_CENTER_X = 0.50f
private const val DOSE_TOP_Y = 0.05f
private const val DOSE_RIGHT_UPPER_X = 0.63f
private const val DOSE_LEFT_UPPER_X = 0.37f
private const val DOSE_RIGHT_X = 0.84f
private const val DOSE_LEFT_X = 0.16f
private const val DOSE_UPPER_CONTROL_Y = 0.22f
private const val DOSE_MIDDLE_CONTROL_Y = 0.48f
private const val DOSE_BODY_Y = 0.66f
private const val DOSE_BOTTOM_CONTROL_Y = 0.93f
private const val DOSE_HIGHLIGHT_ALPHA = 0.72f
private const val DOSE_HIGHLIGHT_RADIUS = 0.065f
private const val DOSE_HIGHLIGHT_X = 0.39f
private const val DOSE_HIGHLIGHT_Y = 0.61f
private const val EMPTY_BADGE_CENTER_X = 0.73f
private const val EMPTY_BADGE_CENTER_Y = 0.72f
private const val EMPTY_BADGE_RADIUS = 0.20f
private const val EMPTY_BADGE_PLUS_ARM = 0.075f
private const val EMPTY_BADGE_OUTLINE_ALPHA = 0.70f
private const val SCHEDULE_CENTER_Y = 0.52f
private const val SCHEDULE_LEFT_X = 0.12f
private const val SCHEDULE_RIGHT_X = 0.88f
private const val SCHEDULE_NODE_RADIUS = 0.12f
private const val SCHEDULE_LINE_ALPHA = 0.50f
private const val SCHEDULE_ACTIVE_NODE_INDEX = 1
private const val SCHEDULE_NODE_LEFT_X = 0.20f
private const val SCHEDULE_NODE_CENTER_X = 0.50f
private const val SCHEDULE_NODE_RIGHT_X = 0.80f
private const val RESERVOIR_LEFT_X = 0.20f
private const val RESERVOIR_TOP_Y = 0.22f
private const val RESERVOIR_WIDTH = 0.60f
private const val RESERVOIR_HEIGHT = 0.68f
private const val RESERVOIR_CORNER_RATIO = 0.10f
private const val RESERVOIR_CAP_LEFT_X = 0.36f
private const val RESERVOIR_CAP_RIGHT_X = 0.64f
private const val RESERVOIR_CAP_Y = 0.10f
private const val RESERVOIR_LEVEL_LEFT_X = 0.31f
private const val RESERVOIR_LEVEL_RIGHT_X = 0.69f
private const val RESERVOIR_LEVEL_Y = 0.66f
private const val RESERVOIR_LEVEL_ALPHA = 0.72f
private const val MANUAL_CENTER_X = 0.50f
private const val MANUAL_CENTER_Y = 0.50f
private const val MANUAL_RADIUS = 0.33f
private const val MANUAL_PLUS_ARM = 0.15f
private const val DOSE_GLYPH_STROKE_DP = 1.45f
private const val EMPTY_BADGE_OUTLINE_WIDTH_DP = 1.10f
private const val EMPTY_BADGE_PLUS_WIDTH_DP = 1.55f
private const val SCHEDULE_GLYPH_STROKE_DP = 1.35f
private const val RESERVOIR_GLYPH_STROKE_DP = 1.35f
private const val RESERVOIR_LEVEL_STROKE_DP = 1.8f
private const val MANUAL_GLYPH_STROKE_DP = 1.35f
private val SCHEDULE_NODE_X = listOf(
    SCHEDULE_NODE_LEFT_X,
    SCHEDULE_NODE_CENTER_X,
    SCHEDULE_NODE_RIGHT_X
)
private val DOSE_GLYPH_STROKE = DOSE_GLYPH_STROKE_DP.dp
private val EMPTY_BADGE_OUTLINE_WIDTH = EMPTY_BADGE_OUTLINE_WIDTH_DP.dp
private val EMPTY_BADGE_PLUS_WIDTH = EMPTY_BADGE_PLUS_WIDTH_DP.dp
private val SCHEDULE_GLYPH_STROKE = SCHEDULE_GLYPH_STROKE_DP.dp
private val RESERVOIR_GLYPH_STROKE = RESERVOIR_GLYPH_STROKE_DP.dp
private val RESERVOIR_LEVEL_STROKE = RESERVOIR_LEVEL_STROKE_DP.dp
private val MANUAL_GLYPH_STROKE = MANUAL_GLYPH_STROKE_DP.dp
