package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.calibration

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import com.aqua.aqualight.ui.common.flow.AquaGuidedFlowColors
import kotlin.math.cos
import kotlin.math.sin

internal fun DrawScope.drawCalibrationReservoir(
    colors: AquaGuidedFlowColors,
    bounds: Rect,
    liquidRatio: Float = RESERVOIR_LIQUID_RATIO
) {
    drawOval(
        color = colors.background.copy(alpha = RESERVOIR_SHADOW_ALPHA),
        topLeft = Offset(bounds.left, bounds.bottom - bounds.height * SHADOW_HEIGHT_RATIO),
        size = Size(bounds.width * SHADOW_WIDTH_SCALE, bounds.height * SHADOW_HEIGHT_RATIO)
    )
    drawRoundRect(
        color = colors.surfaceRaised.copy(alpha = PUMP_BODY_ALPHA),
        topLeft = bounds.topLeft,
        size = bounds.size,
        cornerRadius = CornerRadius(bounds.width * RESERVOIR_RADIUS_RATIO)
    )
    val inset = bounds.width * RESERVOIR_RADIUS_RATIO
    val inner = Rect(
        left = bounds.left + inset,
        top = bounds.top + inset,
        right = bounds.right - inset,
        bottom = bounds.bottom - inset
    )
    val liquidTop = inner.bottom - inner.height * liquidRatio.coerceIn(0f, ONE)
    clipRect(inner.left, inner.top, inner.right, inner.bottom) {
        drawRect(
            color = colors.accent.copy(alpha = RESERVOIR_LIQUID_ALPHA),
            topLeft = Offset(inner.left, liquidTop),
            size = Size(inner.width, inner.bottom - liquidTop)
        )
        drawOval(
            color = colors.accent,
            topLeft = Offset(inner.left, liquidTop - inner.height * STROKE_NORMAL_RATIO),
            size = Size(inner.width, inner.height * STROKE_BOLD_RATIO)
        )
    }
    drawRoundRect(
        color = colors.outline.copy(alpha = RESERVOIR_OUTLINE_ALPHA),
        topLeft = bounds.topLeft,
        size = bounds.size,
        cornerRadius = CornerRadius(bounds.width * RESERVOIR_RADIUS_RATIO),
        style = Stroke(width = size.minDimension * STROKE_NORMAL_RATIO)
    )
    drawRoundRect(
        color = colors.textSecondary.copy(alpha = RESERVOIR_HIGHLIGHT_ALPHA),
        topLeft = Offset(bounds.left + inset * HALF, bounds.top + inset),
        size = Size(inset * HALF, bounds.height - inset * TWO),
        cornerRadius = CornerRadius(inset * HALF)
    )
}

internal fun DrawScope.drawCalibrationTube(
    path: Path,
    colors: AquaGuidedFlowColors,
    active: Boolean,
    flowPhase: Float
) {
    val tubeWidth = size.minDimension * TUBE_WIDTH_RATIO
    drawPath(
        path = path,
        color = colors.background.copy(alpha = RESERVOIR_SHADOW_ALPHA),
        style = Stroke(width = tubeWidth * TUBE_BACKGROUND_SCALE, cap = StrokeCap.Round)
    )
    drawPath(
        path = path,
        color = colors.surfaceRaised,
        style = Stroke(width = tubeWidth, cap = StrokeCap.Round)
    )
    drawPath(
        path = path,
        color = colors.surface,
        style = Stroke(width = tubeWidth * TUBE_INNER_SCALE, cap = StrokeCap.Round)
    )
    if (active) {
        val shimmer = smoothCalibrationFlowWave(flowPhase)
        drawPath(
            path = path,
            color = colors.accent.copy(alpha = TUBE_ACTIVE_ALPHA),
            style = Stroke(
                width = tubeWidth * TUBE_ACTIVE_SCALE,
                cap = StrokeCap.Round
            )
        )
        drawPath(
            path = path,
            color = colors.onAccent.copy(
                alpha = TUBE_HIGHLIGHT_MIN_ALPHA + shimmer * TUBE_HIGHLIGHT_ALPHA_DELTA
            ),
            style = Stroke(
                width = tubeWidth * TUBE_HIGHLIGHT_SCALE,
                cap = StrokeCap.Round
            )
        )
    } else {
        drawPath(
            path = path,
            color = colors.textSecondary.copy(alpha = TUBE_IDLE_ALPHA),
            style = Stroke(width = tubeWidth * STROKE_NORMAL_RATIO, cap = StrokeCap.Round)
        )
    }
}

internal fun smoothCalibrationFlowWave(flowPhase: Float): Float =
    (sin(flowPhase * FULL_WAVE_RADIANS).toFloat() + ONE) * HALF

internal fun DrawScope.drawCalibrationPump(
    colors: AquaGuidedFlowColors,
    center: Offset,
    radius: Float,
    rotorAngle: Float,
    active: Boolean
) {
    drawCircle(
        color = colors.background.copy(alpha = RESERVOIR_SHADOW_ALPHA),
        radius = radius * PUMP_BODY_SCALE,
        center = Offset(center.x, center.y + radius * STROKE_BOLD_RATIO)
    )
    drawCircle(
        color = colors.surfaceRaised.copy(alpha = PUMP_BODY_ALPHA),
        radius = radius * PUMP_BODY_SCALE,
        center = center
    )
    drawCircle(
        color = colors.surface,
        radius = radius * PUMP_FACE_SCALE,
        center = center
    )
    drawCircle(
        color = colors.outline.copy(alpha = PUMP_OUTLINE_ALPHA),
        radius = radius * PUMP_FACE_SCALE,
        center = center,
        style = Stroke(width = size.minDimension * STROKE_NORMAL_RATIO)
    )
    drawCircle(
        color = if (active) {
            colors.accent.copy(alpha = PUMP_ACTIVE_GLOW_ALPHA)
        } else {
            colors.textSecondary.copy(alpha = PUMP_IDLE_GLOW_ALPHA)
        },
        radius = radius * PUMP_ROTOR_SCALE,
        center = center
    )
    repeat(ROTOR_COUNT) { index ->
        val angle = (rotorAngle + index * ROTOR_STEP_DEGREES) * DEGREES_TO_RADIANS
        val rollerCenter = Offset(
            x = center.x + cos(angle).toFloat() * radius * PUMP_ROLLER_DISTANCE,
            y = center.y + sin(angle).toFloat() * radius * PUMP_ROLLER_DISTANCE
        )
        drawCircle(
            color = if (active) colors.accent else colors.outline,
            radius = radius * PUMP_ROLLER_RADIUS,
            center = rollerCenter
        )
        drawCircle(
            color = colors.surfaceRaised,
            radius = radius * PUMP_ROLLER_RADIUS * HALF,
            center = rollerCenter
        )
    }
    drawCircle(
        color = colors.textSecondary,
        radius = radius * PUMP_ROLLER_RADIUS,
        center = center
    )
}

internal fun DrawScope.drawCalibrationCylinder(
    colors: AquaGuidedFlowColors,
    bounds: Rect,
    liquidRatio: Float,
    showTarget: Boolean
) {
    val stem = Rect(
        left = bounds.left + bounds.width * CYLINDER_STEM_INSET,
        top = bounds.top + bounds.height * CYLINDER_TOP_INSET,
        right = bounds.right - bounds.width * CYLINDER_STEM_INSET,
        bottom = bounds.bottom - bounds.height * CYLINDER_BOTTOM_INSET
    )
    val innerInset = stem.width * CYLINDER_INNER_INSET
    val inner = Rect(
        left = stem.left + innerInset,
        top = stem.top + innerInset,
        right = stem.right - innerInset,
        bottom = stem.bottom - innerInset
    )
    drawRoundRect(
        color = colors.surfaceRaised.copy(alpha = CYLINDER_GLASS_ALPHA),
        topLeft = stem.topLeft,
        size = stem.size,
        cornerRadius = CornerRadius(stem.width * CYLINDER_STEM_INSET)
    )
    drawCylinderLiquid(colors, inner, liquidRatio)
    drawCylinderTicks(colors, stem, inner)
    if (showTarget) drawCylinderTarget(colors, stem, inner)
    drawRoundRect(
        color = colors.outline.copy(alpha = CYLINDER_OUTLINE_ALPHA),
        topLeft = stem.topLeft,
        size = stem.size,
        cornerRadius = CornerRadius(stem.width * CYLINDER_STEM_INSET),
        style = Stroke(width = size.minDimension * STROKE_NORMAL_RATIO)
    )
}

private fun DrawScope.drawCylinderLiquid(
    colors: AquaGuidedFlowColors,
    inner: Rect,
    liquidRatio: Float
) {
    val safeRatio = liquidRatio.coerceIn(0f, ONE)
    if (safeRatio <= 0f) return
    val liquidY = inner.bottom - inner.height * safeRatio
    clipRect(inner.left, inner.top, inner.right, inner.bottom) {
        drawRect(
            color = colors.accent.copy(alpha = RESERVOIR_LIQUID_ALPHA),
            topLeft = Offset(inner.left, liquidY),
            size = Size(inner.width, inner.bottom - liquidY)
        )
    }
    drawOval(
        color = colors.accent.copy(alpha = CYLINDER_MENISCUS_ALPHA),
        topLeft = Offset(inner.left, liquidY - inner.height * STROKE_NORMAL_RATIO),
        size = Size(inner.width, inner.height * STROKE_BOLD_RATIO)
    )
}

private fun DrawScope.drawCylinderTicks(
    colors: AquaGuidedFlowColors,
    stem: Rect,
    inner: Rect
) {
    repeat(CYLINDER_TICK_COUNT) { index ->
        val progress = index / (CYLINDER_TICK_COUNT - ONE)
        val y = inner.bottom - inner.height * progress
        val major = index % CYLINDER_MAJOR_TICK_INTERVAL == 0
        val tickLength = stem.width * if (major) {
            CYLINDER_MAJOR_TICK_RATIO
        } else {
            CYLINDER_MINOR_TICK_RATIO
        }
        drawLine(
            color = colors.textSecondary,
            start = Offset(stem.right - tickLength, y),
            end = Offset(stem.right, y),
            strokeWidth = size.minDimension * if (major) STROKE_NORMAL_RATIO else STROKE_THIN_RATIO,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawCylinderTarget(
    colors: AquaGuidedFlowColors,
    stem: Rect,
    inner: Rect
) {
    val targetY = inner.bottom - inner.height * VERIFICATION_FILL_RATIO
    drawLine(
        color = colors.accent.copy(alpha = CYLINDER_TARGET_ALPHA),
        start = Offset(stem.left, targetY),
        end = Offset(stem.right, targetY),
        strokeWidth = size.minDimension * STROKE_NORMAL_RATIO,
        cap = StrokeCap.Round
    )
}
