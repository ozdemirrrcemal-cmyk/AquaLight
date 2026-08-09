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
internal fun DosingSummaryGlyph(
    icon: DosingSummaryIcon,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        when (icon) {
            DosingSummaryIcon.DOSE -> drawDoseGlyph(tint)
            DosingSummaryIcon.DAYS -> drawScheduleDaysGlyph(tint)
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

internal enum class DosingSummaryIcon {
    DOSE,
    DAYS
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

    drawCircle(
        color = badgeSurface,
        radius = badgeRadius,
        center = badgeCenter
    )
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

private fun DrawScope.drawScheduleDaysGlyph(color: Color) {
    val strokeWidth = SCHEDULE_GLYPH_STROKE.toPx()
    val bodyTop = size.height * SCHEDULE_BODY_TOP_Y
    val bodyLeft = size.width * SCHEDULE_BODY_LEFT_X
    val bodyWidth = size.width * SCHEDULE_BODY_WIDTH
    val bodyHeight = size.height * SCHEDULE_BODY_HEIGHT

    drawRoundRect(
        color = color,
        topLeft = Offset(bodyLeft, bodyTop),
        size = Size(bodyWidth, bodyHeight),
        cornerRadius = CornerRadius(SCHEDULE_CORNER_RADIUS.toPx()),
        style = Stroke(width = strokeWidth)
    )
    drawLine(
        color = color,
        start = Offset(bodyLeft, size.height * SCHEDULE_HEADER_Y),
        end = Offset(bodyLeft + bodyWidth, size.height * SCHEDULE_HEADER_Y),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
    SCHEDULE_BINDER_X.forEach { xFraction ->
        drawLine(
            color = color,
            start = Offset(size.width * xFraction, size.height * SCHEDULE_BINDER_TOP_Y),
            end = Offset(size.width * xFraction, size.height * SCHEDULE_BINDER_BOTTOM_Y),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
    SCHEDULE_DOT_X.forEach { xFraction ->
        drawCircle(
            color = color,
            radius = SCHEDULE_DOT_RADIUS.toPx(),
            center = Offset(size.width * xFraction, size.height * SCHEDULE_DOT_Y)
        )
    }
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
private const val SCHEDULE_BODY_TOP_Y = 0.18f
private const val SCHEDULE_BODY_LEFT_X = 0.10f
private const val SCHEDULE_BODY_WIDTH = 0.80f
private const val SCHEDULE_BODY_HEIGHT = 0.70f
private const val SCHEDULE_HEADER_Y = 0.40f
private const val SCHEDULE_BINDER_TOP_Y = 0.08f
private const val SCHEDULE_BINDER_BOTTOM_Y = 0.28f
private const val SCHEDULE_DOT_Y = 0.62f
private const val SCHEDULE_BINDER_LEFT_X = 0.32f
private const val SCHEDULE_BINDER_RIGHT_X = 0.68f
private const val SCHEDULE_DOT_LEFT_X = 0.30f
private const val SCHEDULE_DOT_CENTER_X = 0.50f
private const val SCHEDULE_DOT_RIGHT_X = 0.70f
private const val DOSE_GLYPH_STROKE_DP = 1.45f
private const val EMPTY_BADGE_OUTLINE_WIDTH_DP = 1.10f
private const val EMPTY_BADGE_PLUS_WIDTH_DP = 1.55f
private const val SCHEDULE_GLYPH_STROKE_DP = 1.35f
private const val SCHEDULE_CORNER_RADIUS_DP = 2.5f
private const val SCHEDULE_DOT_RADIUS_DP = 1.1f
private val SCHEDULE_BINDER_X = listOf(SCHEDULE_BINDER_LEFT_X, SCHEDULE_BINDER_RIGHT_X)
private val SCHEDULE_DOT_X = listOf(
    SCHEDULE_DOT_LEFT_X,
    SCHEDULE_DOT_CENTER_X,
    SCHEDULE_DOT_RIGHT_X
)
private val DOSE_GLYPH_STROKE = DOSE_GLYPH_STROKE_DP.dp
private val EMPTY_BADGE_OUTLINE_WIDTH = EMPTY_BADGE_OUTLINE_WIDTH_DP.dp
private val EMPTY_BADGE_PLUS_WIDTH = EMPTY_BADGE_PLUS_WIDTH_DP.dp
private val SCHEDULE_GLYPH_STROKE = SCHEDULE_GLYPH_STROKE_DP.dp
private val SCHEDULE_CORNER_RADIUS = SCHEDULE_CORNER_RADIUS_DP.dp
private val SCHEDULE_DOT_RADIUS = SCHEDULE_DOT_RADIUS_DP.dp
