package com.aqua.aqualight.ui.tabs.devices.detail.dosing.calibration

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.ceil

internal data class DosingCalibrationArtColors(
    val primary: Color,
    val secondary: Color,
    val accent: Color,
    val surface: Color,
    val outline: Color
)

internal fun DrawScope.drawCalibrationBottle(
    colors: DosingCalibrationArtColors,
    liquidFraction: Float = DEFAULT_BOTTLE_LIQUID_FRACTION
) {
    val left = size.width * BOTTLE_LEFT_RATIO
    val top = size.height * BOTTLE_TOP_RATIO
    val width = size.width * BOTTLE_WIDTH_RATIO
    val height = size.height * BOTTLE_HEIGHT_RATIO
    val neckWidth = width * BOTTLE_NECK_WIDTH_RATIO
    val neckLeft = left + (width - neckWidth) / HALF_DIVISOR
    val outlineWidth = size.minDimension * ART_STROKE_RATIO
    val bottlePath = Path().apply {
        moveTo(neckLeft, top)
        lineTo(neckLeft + neckWidth, top)
        lineTo(neckLeft + neckWidth, top + height * BOTTLE_NECK_HEIGHT_RATIO)
        cubicTo(
            left + width,
            top + height * BOTTLE_SHOULDER_RATIO,
            left + width,
            top + height * BOTTLE_SHOULDER_RATIO,
            left + width,
            top + height * BOTTLE_BODY_TOP_RATIO
        )
        lineTo(left + width, top + height)
        quadraticBezierTo(
            left + width,
            top + height + height * BOTTLE_CORNER_RATIO,
            left + width * BOTTLE_CORNER_RATIO,
            top + height + height * BOTTLE_CORNER_RATIO
        )
        lineTo(left + width * BOTTLE_CORNER_RATIO, top + height + height * BOTTLE_CORNER_RATIO)
        quadraticBezierTo(
            left,
            top + height + height * BOTTLE_CORNER_RATIO,
            left,
            top + height
        )
        lineTo(left, top + height * BOTTLE_BODY_TOP_RATIO)
        cubicTo(
            left,
            top + height * BOTTLE_SHOULDER_RATIO,
            left,
            top + height * BOTTLE_SHOULDER_RATIO,
            neckLeft,
            top + height * BOTTLE_NECK_HEIGHT_RATIO
        )
        close()
    }
    drawPath(
        path = bottlePath,
        brush = Brush.verticalGradient(
            colors = listOf(
                colors.primary.copy(alpha = GLASS_TOP_ALPHA),
                colors.secondary.copy(alpha = GLASS_BOTTOM_ALPHA)
            )
        )
    )
    drawPath(
        path = bottlePath,
        color = colors.outline.copy(alpha = GLASS_OUTLINE_ALPHA),
        style = Stroke(width = outlineWidth, cap = StrokeCap.Round)
    )

    val liquidHeight = height * BOTTLE_LIQUID_BODY_RATIO * liquidFraction.coerceIn(0f, 1f)
    val liquidTop = top + height - liquidHeight
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                colors.accent.copy(alpha = LIQUID_TOP_ALPHA),
                colors.accent.copy(alpha = LIQUID_BOTTOM_ALPHA)
            )
        ),
        topLeft = Offset(left + width * BOTTLE_LIQUID_INSET_RATIO, liquidTop),
        size = Size(
            width = width * BOTTLE_LIQUID_WIDTH_RATIO,
            height = liquidHeight
        ),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(
            width * BOTTLE_LIQUID_CORNER_RATIO
        )
    )
    drawLine(
        color = colors.accent.copy(alpha = MENISCUS_ALPHA),
        start = Offset(left + width * BOTTLE_LIQUID_INSET_RATIO, liquidTop),
        end = Offset(left + width * BOTTLE_LIQUID_END_RATIO, liquidTop),
        strokeWidth = outlineWidth,
        cap = StrokeCap.Round
    )
}

internal fun DrawScope.drawCalibrationTube(
    colors: DosingCalibrationArtColors,
    phase: Float,
    active: Boolean
) {
    val path = Path().apply {
        moveTo(size.width * TUBE_START_X_RATIO, size.height * TUBE_START_Y_RATIO)
        cubicTo(
            size.width * TUBE_CONTROL_1_X_RATIO,
            size.height * TUBE_CONTROL_1_Y_RATIO,
            size.width * TUBE_CONTROL_2_X_RATIO,
            size.height * TUBE_CONTROL_2_Y_RATIO,
            size.width * TUBE_END_X_RATIO,
            size.height * TUBE_END_Y_RATIO
        )
    }
    val strokeWidth = size.minDimension * TUBE_STROKE_RATIO
    drawPath(
        path = path,
        color = colors.outline.copy(alpha = TUBE_SHELL_ALPHA),
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )
    drawPath(
        path = path,
        color = colors.surface.copy(alpha = TUBE_CORE_ALPHA),
        style = Stroke(width = strokeWidth * TUBE_CORE_WIDTH_RATIO, cap = StrokeCap.Round)
    )
    if (active) {
        repeat(FLOW_PARTICLE_COUNT) { index ->
            val progress = (phase + index.toFloat() / FLOW_PARTICLE_COUNT.toFloat()) % 1f
            val point = calibrationTubePoint(progress)
            drawCircle(
                color = colors.accent.copy(alpha = FLOW_PARTICLE_ALPHA),
                radius = strokeWidth * FLOW_PARTICLE_RADIUS_RATIO,
                center = Offset(size.width * point.x, size.height * point.y)
            )
        }
    }
}

internal fun DrawScope.drawCalibrationCylinder(
    colors: DosingCalibrationArtColors,
    fillFraction: Float,
    targetFraction: Float? = null
) {
    val width = size.width * CYLINDER_WIDTH_RATIO
    val height = size.height * CYLINDER_HEIGHT_RATIO
    val left = size.width * CYLINDER_LEFT_RATIO
    val top = size.height * CYLINDER_TOP_RATIO
    val strokeWidth = size.minDimension * ART_STROKE_RATIO
    val bottom = top + height
    val right = left + width

    val glassPath = Path().apply {
        moveTo(left, top)
        lineTo(right, top)
        lineTo(right - width * CYLINDER_LIP_RATIO, bottom)
        lineTo(left + width * CYLINDER_LIP_RATIO, bottom)
        close()
    }
    drawPath(
        path = glassPath,
        brush = Brush.horizontalGradient(
            colors = listOf(
                colors.primary.copy(alpha = CYLINDER_GLASS_EDGE_ALPHA),
                colors.surface.copy(alpha = CYLINDER_GLASS_CENTER_ALPHA),
                colors.primary.copy(alpha = CYLINDER_GLASS_EDGE_ALPHA)
            )
        )
    )
    drawPath(
        path = glassPath,
        color = colors.outline.copy(alpha = GLASS_OUTLINE_ALPHA),
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )

    val clampedFill = fillFraction.coerceIn(0f, 1f)
    val innerBottom = bottom - height * CYLINDER_BOTTOM_INSET_RATIO
    val innerTop = top + height * CYLINDER_TOP_INSET_RATIO
    val liquidHeight = (innerBottom - innerTop) * clampedFill
    val liquidTop = innerBottom - liquidHeight
    if (liquidHeight > 0f) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    colors.accent.copy(alpha = LIQUID_TOP_ALPHA),
                    colors.accent.copy(alpha = LIQUID_BOTTOM_ALPHA)
                ),
                startY = liquidTop,
                endY = innerBottom
            ),
            topLeft = Offset(
                left + width * CYLINDER_LIQUID_HORIZONTAL_INSET_RATIO,
                liquidTop
            ),
            size = Size(
                width = width * CYLINDER_LIQUID_WIDTH_RATIO,
                height = liquidHeight
            )
        )
        drawLine(
            color = colors.accent.copy(alpha = MENISCUS_ALPHA),
            start = Offset(
                left + width * CYLINDER_LIQUID_HORIZONTAL_INSET_RATIO,
                liquidTop
            ),
            end = Offset(
                right - width * CYLINDER_LIQUID_HORIZONTAL_INSET_RATIO,
                liquidTop
            ),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }

    repeat(CYLINDER_TICK_COUNT) { index ->
        val fraction = index.toFloat() / (CYLINDER_TICK_COUNT - 1).toFloat()
        val y = innerBottom - (innerBottom - innerTop) * fraction
        val tickLength = if (index % CYLINDER_MAJOR_TICK_DIVISOR == 0) {
            width * CYLINDER_MAJOR_TICK_LENGTH_RATIO
        } else {
            width * CYLINDER_MINOR_TICK_LENGTH_RATIO
        }
        drawLine(
            color = colors.secondary.copy(alpha = CYLINDER_TICK_ALPHA),
            start = Offset(right - tickLength, y),
            end = Offset(right, y),
            strokeWidth = strokeWidth * CYLINDER_TICK_WIDTH_RATIO,
            cap = StrokeCap.Round
        )
    }

    targetFraction?.coerceIn(0f, 1f)?.let { fraction ->
        val y = innerBottom - (innerBottom - innerTop) * fraction
        drawLine(
            color = colors.accent.copy(alpha = TARGET_LINE_ALPHA),
            start = Offset(left - width * TARGET_LINE_EXTENSION_RATIO, y),
            end = Offset(right + width * TARGET_LINE_EXTENSION_RATIO, y),
            strokeWidth = strokeWidth * TARGET_LINE_WIDTH_RATIO,
            cap = StrokeCap.Round
        )
        drawCircle(
            color = colors.accent,
            radius = strokeWidth * TARGET_DOT_RADIUS_RATIO,
            center = Offset(right + width * TARGET_LINE_EXTENSION_RATIO, y)
        )
    }

    drawLine(
        color = colors.outline.copy(alpha = GLASS_OUTLINE_ALPHA),
        start = Offset(left - width * CYLINDER_BASE_EXTENSION_RATIO, bottom),
        end = Offset(right + width * CYLINDER_BASE_EXTENSION_RATIO, bottom),
        strokeWidth = strokeWidth * CYLINDER_BASE_WIDTH_RATIO,
        cap = StrokeCap.Round
    )
}

internal fun DrawScope.drawFallingDrops(
    colors: DosingCalibrationArtColors,
    phase: Float,
    active: Boolean
) {
    if (!active) return
    repeat(DROP_COUNT) { index ->
        val progress = (phase + index.toFloat() / DROP_COUNT.toFloat()) % 1f
        val x = size.width * DROP_X_RATIO
        val startY = size.height * DROP_START_Y_RATIO
        val endY = size.height * DROP_END_Y_RATIO
        val y = startY + (endY - startY) * progress
        val radius = size.minDimension * DROP_RADIUS_RATIO * (DROP_MIN_SCALE + progress * DROP_SCALE_RANGE)
        drawCircle(
            color = colors.accent.copy(alpha = DROP_ALPHA),
            radius = radius,
            center = Offset(x, y)
        )
    }
}

internal fun DrawScope.drawCalibrationSuccessSeal(colors: DosingCalibrationArtColors) {
    val center = Offset(size.width * SUCCESS_CENTER_X_RATIO, size.height * SUCCESS_CENTER_Y_RATIO)
    val radius = size.minDimension * SUCCESS_RADIUS_RATIO
    val strokeWidth = size.minDimension * SUCCESS_STROKE_RATIO
    drawCircle(
        color = colors.accent.copy(alpha = SUCCESS_GLOW_ALPHA),
        radius = radius * SUCCESS_GLOW_RADIUS_RATIO,
        center = center
    )
    drawCircle(
        color = colors.accent.copy(alpha = SUCCESS_RING_ALPHA),
        radius = radius,
        center = center,
        style = Stroke(width = strokeWidth)
    )
    val checkPath = Path().apply {
        moveTo(center.x - radius * SUCCESS_CHECK_LEFT_X_RATIO, center.y)
        lineTo(
            center.x - radius * SUCCESS_CHECK_MIDDLE_X_RATIO,
            center.y + radius * SUCCESS_CHECK_MIDDLE_Y_RATIO
        )
        lineTo(
            center.x + radius * SUCCESS_CHECK_RIGHT_X_RATIO,
            center.y - radius * SUCCESS_CHECK_RIGHT_Y_RATIO
        )
    }
    drawPath(
        path = checkPath,
        color = colors.accent,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )
}

internal fun calibrationVolumeFraction(volumeMl: Double?): Float {
    if (volumeMl == null || !volumeMl.isFinite() || volumeMl <= 0.0) return 0f
    val visualScaleMl = ceil(volumeMl * VOLUME_SCALE_HEADROOM).coerceAtLeast(MIN_VISUAL_SCALE_ML)
    return (volumeMl / visualScaleMl).toFloat().coerceIn(0f, 1f)
}

private fun calibrationTubePoint(progress: Float): NormalizedPoint {
    val t = progress.coerceIn(0f, 1f)
    return if (t < TUBE_SEGMENT_SPLIT) {
        val local = t / TUBE_SEGMENT_SPLIT
        NormalizedPoint(
            x = lerp(TUBE_START_X_RATIO, TUBE_MIDDLE_X_RATIO, local),
            y = lerp(TUBE_START_Y_RATIO, TUBE_MIDDLE_Y_RATIO, local)
        )
    } else {
        val local = (t - TUBE_SEGMENT_SPLIT) / (1f - TUBE_SEGMENT_SPLIT)
        NormalizedPoint(
            x = lerp(TUBE_MIDDLE_X_RATIO, TUBE_END_X_RATIO, local),
            y = lerp(TUBE_MIDDLE_Y_RATIO, TUBE_END_Y_RATIO, local)
        )
    }
}

private fun lerp(start: Float, end: Float, fraction: Float): Float =
    start + (end - start) * fraction

private data class NormalizedPoint(val x: Float, val y: Float)

private const val HALF_DIVISOR = 2f
private const val DEFAULT_BOTTLE_LIQUID_FRACTION = 0.72f
private const val ART_STROKE_RATIO = 0.008f
private const val BOTTLE_LEFT_RATIO = 0.10f
private const val BOTTLE_TOP_RATIO = 0.20f
private const val BOTTLE_WIDTH_RATIO = 0.23f
private const val BOTTLE_HEIGHT_RATIO = 0.54f
private const val BOTTLE_NECK_WIDTH_RATIO = 0.38f
private const val BOTTLE_NECK_HEIGHT_RATIO = 0.15f
private const val BOTTLE_SHOULDER_RATIO = 0.22f
private const val BOTTLE_BODY_TOP_RATIO = 0.30f
private const val BOTTLE_CORNER_RATIO = 0.08f
private const val BOTTLE_LIQUID_BODY_RATIO = 0.62f
private const val BOTTLE_LIQUID_INSET_RATIO = 0.09f
private const val BOTTLE_LIQUID_END_RATIO = 0.91f
private const val BOTTLE_LIQUID_WIDTH_RATIO = 0.82f
private const val BOTTLE_LIQUID_CORNER_RATIO = 0.06f
private const val GLASS_TOP_ALPHA = 0.08f
private const val GLASS_BOTTOM_ALPHA = 0.18f
private const val GLASS_OUTLINE_ALPHA = 0.62f
private const val LIQUID_TOP_ALPHA = 0.38f
private const val LIQUID_BOTTOM_ALPHA = 0.72f
private const val MENISCUS_ALPHA = 0.92f
private const val TUBE_START_X_RATIO = 0.27f
private const val TUBE_START_Y_RATIO = 0.32f
private const val TUBE_MIDDLE_X_RATIO = 0.58f
private const val TUBE_MIDDLE_Y_RATIO = 0.20f
private const val TUBE_END_X_RATIO = 0.78f
private const val TUBE_END_Y_RATIO = 0.34f
private const val TUBE_CONTROL_1_X_RATIO = 0.42f
private const val TUBE_CONTROL_1_Y_RATIO = 0.10f
private const val TUBE_CONTROL_2_X_RATIO = 0.67f
private const val TUBE_CONTROL_2_Y_RATIO = 0.18f
private const val TUBE_STROKE_RATIO = 0.028f
private const val TUBE_CORE_WIDTH_RATIO = 0.52f
private const val TUBE_SHELL_ALPHA = 0.52f
private const val TUBE_CORE_ALPHA = 0.72f
private const val FLOW_PARTICLE_COUNT = 4
private const val FLOW_PARTICLE_ALPHA = 0.92f
private const val FLOW_PARTICLE_RADIUS_RATIO = 0.34f
private const val TUBE_SEGMENT_SPLIT = 0.62f
private const val CYLINDER_LEFT_RATIO = 0.68f
private const val CYLINDER_TOP_RATIO = 0.31f
private const val CYLINDER_WIDTH_RATIO = 0.18f
private const val CYLINDER_HEIGHT_RATIO = 0.52f
private const val CYLINDER_LIP_RATIO = 0.08f
private const val CYLINDER_TOP_INSET_RATIO = 0.05f
private const val CYLINDER_BOTTOM_INSET_RATIO = 0.05f
private const val CYLINDER_LIQUID_HORIZONTAL_INSET_RATIO = 0.10f
private const val CYLINDER_LIQUID_WIDTH_RATIO = 0.80f
private const val CYLINDER_GLASS_EDGE_ALPHA = 0.12f
private const val CYLINDER_GLASS_CENTER_ALPHA = 0.05f
private const val CYLINDER_TICK_COUNT = 11
private const val CYLINDER_MAJOR_TICK_DIVISOR = 2
private const val CYLINDER_MAJOR_TICK_LENGTH_RATIO = 0.28f
private const val CYLINDER_MINOR_TICK_LENGTH_RATIO = 0.17f
private const val CYLINDER_TICK_ALPHA = 0.58f
private const val CYLINDER_TICK_WIDTH_RATIO = 0.64f
private const val CYLINDER_BASE_EXTENSION_RATIO = 0.30f
private const val CYLINDER_BASE_WIDTH_RATIO = 1.15f
private const val TARGET_LINE_ALPHA = 0.88f
private const val TARGET_LINE_EXTENSION_RATIO = 0.30f
private const val TARGET_LINE_WIDTH_RATIO = 1.15f
private const val TARGET_DOT_RADIUS_RATIO = 1.35f
private const val DROP_COUNT = 3
private const val DROP_X_RATIO = 0.78f
private const val DROP_START_Y_RATIO = 0.36f
private const val DROP_END_Y_RATIO = 0.51f
private const val DROP_RADIUS_RATIO = 0.012f
private const val DROP_MIN_SCALE = 0.72f
private const val DROP_SCALE_RANGE = 0.28f
private const val DROP_ALPHA = 0.92f
private const val SUCCESS_CENTER_X_RATIO = 0.52f
private const val SUCCESS_CENTER_Y_RATIO = 0.48f
private const val SUCCESS_RADIUS_RATIO = 0.16f
private const val SUCCESS_STROKE_RATIO = 0.018f
private const val SUCCESS_GLOW_ALPHA = 0.10f
private const val SUCCESS_GLOW_RADIUS_RATIO = 1.35f
private const val SUCCESS_RING_ALPHA = 0.74f
private const val SUCCESS_CHECK_LEFT_X_RATIO = 0.48f
private const val SUCCESS_CHECK_MIDDLE_X_RATIO = 0.12f
private const val SUCCESS_CHECK_MIDDLE_Y_RATIO = 0.34f
private const val SUCCESS_CHECK_RIGHT_X_RATIO = 0.54f
private const val SUCCESS_CHECK_RIGHT_Y_RATIO = 0.44f
private const val VOLUME_SCALE_HEADROOM = 1.25
private const val MIN_VISUAL_SCALE_ML = 1.0
