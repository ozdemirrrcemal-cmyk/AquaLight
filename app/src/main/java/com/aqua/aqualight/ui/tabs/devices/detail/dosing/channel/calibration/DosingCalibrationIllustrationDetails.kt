package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.calibration

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import com.aqua.aqualight.ui.common.flow.AquaGuidedFlowColors

internal fun DrawScope.drawCalibrationNameTag(
    colors: AquaGuidedFlowColors,
    bounds: Rect
) {
    drawRoundRect(
        color = colors.surfaceRaised,
        topLeft = bounds.topLeft,
        size = bounds.size,
        cornerRadius = CornerRadius(bounds.height * NAME_TAG_RADIUS_RATIO)
    )
    drawRoundRect(
        color = colors.outline,
        topLeft = bounds.topLeft,
        size = bounds.size,
        cornerRadius = CornerRadius(bounds.height * NAME_TAG_RADIUS_RATIO),
        style = Stroke(width = size.minDimension * STROKE_NORMAL_RATIO)
    )
    drawNameTagLine(colors, bounds, NAME_TAG_FIRST_LINE_Y, NAME_TAG_LINE_END)
    drawNameTagLine(colors, bounds, NAME_TAG_SECOND_LINE_Y, NAME_TAG_LINE_END * VERIFICATION_FILL_RATIO)
}

private fun DrawScope.drawNameTagLine(
    colors: AquaGuidedFlowColors,
    bounds: Rect,
    yRatio: Float,
    endRatio: Float
) {
    val y = bounds.top + bounds.height * yRatio
    drawLine(
        color = colors.textSecondary,
        start = Offset(bounds.left + bounds.width * NAME_TAG_LINE_START, y),
        end = Offset(bounds.left + bounds.width * endRatio, y),
        strokeWidth = size.minDimension * STROKE_NORMAL_RATIO,
        cap = StrokeCap.Round
    )
}

internal fun DrawScope.drawCalibrationWasteCup(
    colors: AquaGuidedFlowColors,
    bounds: Rect,
    active: Boolean,
    flowPhase: Float,
    outletEnd: Offset
) {
    val liquidRatio = if (active) {
        WASTE_LIQUID_ACTIVE + flowPhase * WASTE_FLOW_DELTA
    } else {
        WASTE_LIQUID_IDLE
    }
    drawRoundRect(
        color = colors.surfaceRaised.copy(alpha = CYLINDER_GLASS_ALPHA),
        topLeft = bounds.topLeft,
        size = bounds.size,
        cornerRadius = CornerRadius(bounds.width * RESERVOIR_RADIUS_RATIO)
    )
    val liquidTop = bounds.bottom - bounds.height * liquidRatio
    clipRect(bounds.left, bounds.top, bounds.right, bounds.bottom) {
        drawRect(
            color = colors.accent.copy(alpha = RESERVOIR_LIQUID_ALPHA),
            topLeft = Offset(bounds.left, liquidTop),
            size = Size(bounds.width, bounds.bottom - liquidTop)
        )
    }
    drawRoundRect(
        color = colors.outline,
        topLeft = bounds.topLeft,
        size = bounds.size,
        cornerRadius = CornerRadius(bounds.width * RESERVOIR_RADIUS_RATIO),
        style = Stroke(width = size.minDimension * STROKE_NORMAL_RATIO)
    )
    if (active) {
        drawCalibrationDrops(
            colors = colors,
            flowPhase = flowPhase,
            outletEnd = outletEnd,
            destinationY = bounds.top
        )
    }
}

internal fun DrawScope.drawCalibrationDrops(
    colors: AquaGuidedFlowColors,
    flowPhase: Float,
    outletEnd: Offset,
    destinationY: Float
) {
    val distance = (destinationY - outletEnd.y).coerceAtLeast(
        size.height * DROP_MIN_DISTANCE_RATIO
    )
    repeat(DROP_COUNT) { index ->
        val progress = (flowPhase + index * DROP_PHASE_OFFSET) % ONE
        drawCalibrationDrop(
            colors = colors,
            center = Offset(outletEnd.x, outletEnd.y + distance * progress),
            radius = size.minDimension * DROP_RADIUS_RATIO,
            alpha = DROP_ALPHA - progress * RESERVOIR_HIGHLIGHT_ALPHA
        )
    }
}

private fun DrawScope.drawCalibrationDrop(
    colors: AquaGuidedFlowColors,
    center: Offset,
    radius: Float,
    alpha: Float
) {
    val drop = Path().apply {
        moveTo(center.x, center.y - radius)
        lineTo(center.x - radius * HALF, center.y)
        quadraticBezierTo(center.x - radius * HALF, center.y + radius, center.x, center.y + radius)
        quadraticBezierTo(center.x + radius * HALF, center.y + radius, center.x + radius * HALF, center.y)
        close()
    }
    drawPath(path = drop, color = colors.accent.copy(alpha = alpha))
}

internal fun DrawScope.drawCalibrationEyeGuide(
    colors: AquaGuidedFlowColors,
    eyeCenter: Offset,
    lineStartX: Float,
    meniscusY: Float
) {
    val eyeWidth = size.width * EYE_WIDTH_RATIO
    val eyeHeight = size.height * EYE_HEIGHT_RATIO
    drawLine(
        color = colors.accent,
        start = Offset(lineStartX, meniscusY),
        end = Offset(eyeCenter.x - eyeWidth * HALF, meniscusY),
        strokeWidth = size.minDimension * STROKE_NORMAL_RATIO,
        cap = StrokeCap.Round,
        pathEffect = PathEffect.dashPathEffect(
            floatArrayOf(size.minDimension * STROKE_BOLD_RATIO, size.minDimension * STROKE_NORMAL_RATIO)
        )
    )
    val eye = Path().apply {
        moveTo(eyeCenter.x - eyeWidth * HALF, eyeCenter.y)
        quadraticBezierTo(eyeCenter.x, eyeCenter.y - eyeHeight * HALF, eyeCenter.x + eyeWidth * HALF, eyeCenter.y)
        quadraticBezierTo(eyeCenter.x, eyeCenter.y + eyeHeight * HALF, eyeCenter.x - eyeWidth * HALF, eyeCenter.y)
        close()
    }
    drawPath(path = eye, color = colors.surfaceRaised)
    drawPath(
        path = eye,
        color = colors.outline,
        style = Stroke(width = size.minDimension * STROKE_NORMAL_RATIO)
    )
    drawCircle(
        color = colors.accent,
        radius = eyeHeight * EYE_IRIS_RATIO,
        center = eyeCenter
    )
    drawCircle(
        color = colors.background,
        radius = eyeHeight * EYE_PUPIL_RATIO,
        center = eyeCenter
    )
}

internal fun DrawScope.drawCalibrationToleranceBadge(
    colors: AquaGuidedFlowColors,
    center: Offset
) {
    val radius = size.minDimension * BADGE_RADIUS_RATIO
    drawCircle(
        color = colors.accent.copy(alpha = BADGE_GLOW_ALPHA),
        radius = radius * BADGE_GLOW_SCALE,
        center = center
    )
    drawCircle(color = colors.accent, radius = radius, center = center)
    drawCircle(
        color = colors.onAccent.copy(alpha = CYLINDER_GLASS_ALPHA),
        radius = radius,
        center = center,
        style = Stroke(width = size.minDimension * STROKE_NORMAL_RATIO)
    )
    val check = Path().apply {
        moveTo(
            center.x + radius * CHECK_LEFT_X,
            center.y + radius * CHECK_LEFT_Y
        )
        lineTo(
            center.x + radius * CHECK_MIDDLE_X,
            center.y + radius * CHECK_MIDDLE_Y
        )
        lineTo(
            center.x + radius * CHECK_RIGHT_X,
            center.y + radius * CHECK_RIGHT_Y
        )
    }
    drawPath(
        path = check,
        color = colors.onAccent,
        style = Stroke(width = size.minDimension * STROKE_BOLD_RATIO, cap = StrokeCap.Round)
    )
}
