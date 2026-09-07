package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.dashboard

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardAlpha
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import kotlin.math.sin

internal fun DrawScope.drawCoolingHeroScene(
    motionPhase: Float,
    motionIntensity: Float,
    status: CoolingHeroVisualStatus,
    colors: AquaDeviceCardColors
) {
    drawCoolingAtmosphere(status, colors)
    clipPath(glassTankPath(-WATER_CLIP_HEADROOM_FRACTION)) {
        drawWaterBody(motionPhase, motionIntensity, colors)
        if (motionIntensity > NO_MOTION) {
            drawWaterReflection(motionPhase, motionIntensity, colors)
        }
    }
    if (motionIntensity > NO_MOTION) {
        drawAirflow(motionPhase, motionIntensity, colors)
    }
    drawGlassTank(colors)
}

private fun DrawScope.drawCoolingAtmosphere(
    status: CoolingHeroVisualStatus,
    colors: AquaDeviceCardColors
) {
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.Black,
                colors.surface,
                colors.mediaSurface
            ),
            start = Offset.Zero,
            end = Offset(size.width, size.height)
        )
    )
    val glowColor = when (status) {
        // Alarm state is already communicated by the status card. Keeping the media scene blue
        // prevents the warning tone from becoming a yellow patch behind the transparent fan art.
        CoolingHeroVisualStatus.ATTENTION -> colors.accent
        CoolingHeroVisualStatus.OFFLINE,
        CoolingHeroVisualStatus.WAITING_FOR_DATA -> colors.secondaryText
        CoolingHeroVisualStatus.COOLING,
        CoolingHeroVisualStatus.STANDBY -> colors.accent
    }
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                glowColor.copy(alpha = AquaCoolingDashboardAlpha.liveHeroAtmosphere),
                Color.Transparent
            ),
            center = Offset(size.width * GLOW_CENTER_X, size.height * GLOW_CENTER_Y),
            radius = size.width * GLOW_RADIUS
        ),
        radius = size.width * GLOW_RADIUS,
        center = Offset(size.width * GLOW_CENTER_X, size.height * GLOW_CENTER_Y)
    )
}

private fun DrawScope.drawWaterBody(
    motionPhase: Float,
    motionIntensity: Float,
    colors: AquaDeviceCardColors
) {
    val waterTop = size.height * WATER_TOP_FRACTION
    val water = Path().apply {
        repeat(WATER_SURFACE_SEGMENTS + 1) { index ->
            val progress = index.toFloat() / WATER_SURFACE_SEGMENTS
            val point = Offset(
                x = size.width * progress,
                y = waterSurfaceY(progress, motionPhase, motionIntensity)
            )
            if (index == FIRST_SEGMENT) moveTo(point.x, point.y) else lineTo(point.x, point.y)
        }
        lineTo(size.width, size.height)
        lineTo(ORIGIN, size.height)
        close()
    }
    drawPath(
        path = water,
        brush = Brush.verticalGradient(
            WATER_GRADIENT_SURFACE_STOP to colors.accent.copy(
                alpha = AquaCoolingDashboardAlpha.liveHeroWater
            ),
            WATER_GRADIENT_SHALLOW_STOP to colors.accent.copy(
                alpha = AquaCoolingDashboardAlpha.liveHeroWaterDepth
            ),
            WATER_GRADIENT_TRANSITION_STOP to colors.mediaSurface,
            WATER_GRADIENT_MID_STOP to colors.surface,
            WATER_GRADIENT_BOTTOM_STOP to Color.Black,
            startY = waterTop,
            endY = size.height
        )
    )
    drawPath(
        path = water,
        brush = Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                colors.primaryText.copy(
                    alpha = AquaCoolingDashboardAlpha.liveHeroWaterReflection *
                        WATER_VOLUME_HIGHLIGHT_ALPHA_MULTIPLIER
                ),
                colors.accent.copy(
                    alpha = AquaCoolingDashboardAlpha.liveHeroWaterReflection *
                        WATER_VOLUME_ACCENT_ALPHA_MULTIPLIER
                ),
                Color.Transparent
            ),
            start = Offset(size.width * WATER_HIGHLIGHT_START_X, waterTop),
            end = Offset(size.width * WATER_HIGHLIGHT_END_X, size.height)
        )
    )
    drawWaterSurfaceSheen(motionPhase, motionIntensity, colors)
}

private fun DrawScope.waterSurfaceY(
    progress: Float,
    motionPhase: Float,
    motionIntensity: Float
): Float {
    val perspective = WATER_LEFT_DROP_FRACTION * (UNIT_FLOAT - progress) -
        WATER_RIGHT_LIFT_FRACTION * progress
    val primaryWave = sin(
        (progress * WATER_SURFACE_PRIMARY_CYCLES * FULL_CIRCLE_RADIANS +
            motionPhase * FULL_CIRCLE_RADIANS).toDouble()
    ).toFloat()
    val secondaryWave = sin(
        (progress * WATER_SURFACE_SECONDARY_CYCLES * FULL_CIRCLE_RADIANS -
            motionPhase * DOUBLE_PHASE_RADIANS + WATER_SURFACE_PHASE_OFFSET).toDouble()
    ).toFloat()
    val amplitude = WATER_SURFACE_BASE_AMPLITUDE +
        WATER_SURFACE_ACTIVE_AMPLITUDE * motionIntensity
    return size.height * (
        WATER_TOP_FRACTION + perspective +
            amplitude * (primaryWave * PRIMARY_WAVE_WEIGHT + secondaryWave * SECONDARY_WAVE_WEIGHT)
        )
}

private fun DrawScope.drawWaterSurfaceSheen(
    motionPhase: Float,
    motionIntensity: Float,
    colors: AquaDeviceCardColors
) {
    val surface = Path()
    repeat(WATER_SURFACE_SEGMENTS + 1) { index ->
        val progress = index.toFloat() / WATER_SURFACE_SEGMENTS
        val y = waterSurfaceY(progress, motionPhase, motionIntensity)
        if (index == FIRST_SEGMENT) surface.moveTo(ORIGIN, y) else {
            surface.lineTo(size.width * progress, y)
        }
    }
    drawPath(
        path = surface,
        color = colors.accent.copy(
            alpha = AquaCoolingDashboardAlpha.liveHeroWaterSurface *
                WATER_SURFACE_GLOW_ALPHA_MULTIPLIER
        ),
        style = Stroke(width = WATER_SURFACE_GLOW_STROKE, cap = StrokeCap.Round)
    )
    drawPath(
        path = surface,
        color = colors.primaryText.copy(
            alpha = AquaCoolingDashboardAlpha.liveHeroWaterSurface
        ),
        style = Stroke(width = WATER_SURFACE_STROKE, cap = StrokeCap.Round)
    )
}

private fun DrawScope.drawAirflow(
    motionPhase: Float,
    motionIntensity: Float,
    colors: AquaDeviceCardColors
) {
    repeat(AIRFLOW_LINE_COUNT) { index ->
        val offset = index * AIRFLOW_LINE_SPACING
        val drift = sin(
            (motionPhase * FULL_CIRCLE_RADIANS + index * AIRFLOW_PHASE_SPACING).toDouble()
        ).toFloat() * size.width * AIRFLOW_DRIFT_FRACTION
        val airflow = Path().apply {
            moveTo(
                size.width * AIRFLOW_ORIGIN_X,
                size.height * (AIRFLOW_ORIGIN_Y + offset)
            )
            cubicTo(
                size.width * AIRFLOW_CONTROL_ONE_X + drift,
                size.height * (AIRFLOW_CONTROL_ONE_Y + offset),
                size.width * AIRFLOW_CONTROL_TWO_X - drift,
                size.height * (AIRFLOW_CONTROL_TWO_Y + offset),
                size.width * AIRFLOW_TARGET_X,
                size.height * (AIRFLOW_TARGET_Y + offset)
            )
        }
        drawPath(
            path = airflow,
            color = colors.primaryText.copy(
                alpha = AquaCoolingDashboardAlpha.liveHeroAirflow * motionIntensity
            ),
            style = Stroke(width = AIRFLOW_STROKE, cap = StrokeCap.Round)
        )
    }
}

private fun DrawScope.drawWaterReflection(
    motionPhase: Float,
    motionIntensity: Float,
    colors: AquaDeviceCardColors
) {
    val pulse = REFLECTION_PULSE_BASE + REFLECTION_PULSE_RANGE *
        (sin((motionPhase * FULL_CIRCLE_RADIANS).toDouble()).toFloat() + UNIT_FLOAT) /
        DIAMETER_MULTIPLIER
    val radiusX = size.width * REFLECTION_RADIUS_X * pulse
    val radiusY = size.height * REFLECTION_RADIUS_Y * pulse
    val center = Offset(size.width * REFLECTION_CENTER_X, size.height * REFLECTION_CENTER_Y)
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(
                colors.primaryText.copy(
                    alpha = AquaCoolingDashboardAlpha.liveHeroWaterReflection * motionIntensity
                ),
                colors.accent.copy(
                    alpha = AquaCoolingDashboardAlpha.liveHeroWaterReflection *
                        REFLECTION_ACCENT_ALPHA_MULTIPLIER * motionIntensity
                ),
                Color.Transparent
            ),
            center = center,
            radius = radiusX
        ),
        topLeft = Offset(center.x - radiusX, center.y - radiusY),
        size = Size(radiusX * DIAMETER_MULTIPLIER, radiusY * DIAMETER_MULTIPLIER)
    )
}

private fun DrawScope.drawGlassTank(colors: AquaDeviceCardColors) {
    val waterTop = size.height * WATER_TOP_FRACTION
    val topLeft = Offset(
        size.width * GLASS_HORIZONTAL_INSET,
        waterTop + size.height * (WATER_LEFT_DROP_FRACTION + GLASS_TOP_GAP_FRACTION)
    )
    val topRight = Offset(
        size.width * (UNIT_FLOAT - GLASS_HORIZONTAL_INSET),
        waterTop - size.height * (WATER_RIGHT_LIFT_FRACTION - GLASS_TOP_GAP_FRACTION)
    )
    val bottomRight = Offset(
        size.width * (UNIT_FLOAT - GLASS_HORIZONTAL_INSET),
        size.height * GLASS_BOTTOM_RIGHT_Y
    )
    val bottomLeft = Offset(
        size.width * GLASS_HORIZONTAL_INSET,
        size.height * GLASS_BOTTOM_LEFT_Y
    )
    val pane = Path().apply {
        moveTo(topLeft.x, topLeft.y)
        lineTo(topRight.x, topRight.y)
        lineTo(bottomRight.x, bottomRight.y)
        lineTo(bottomLeft.x, bottomLeft.y)
        close()
    }
    drawPath(
        path = pane,
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                colors.primaryText.copy(
                    alpha = AquaCoolingDashboardAlpha.liveHeroGlassPane
                )
            ),
            startY = waterTop,
            endY = size.height
        )
    )
    drawLine(
        color = colors.primaryText.copy(
            alpha = AquaCoolingDashboardAlpha.liveHeroGlassEdge
        ),
        start = topLeft,
        end = topRight,
        strokeWidth = GLASS_EDGE_STROKE
    )
}

private fun DrawScope.glassTankPath(topGapFraction: Float): Path {
    val waterTop = size.height * WATER_TOP_FRACTION
    return Path().apply {
        moveTo(
            size.width * GLASS_HORIZONTAL_INSET,
            waterTop + size.height * (WATER_LEFT_DROP_FRACTION + topGapFraction)
        )
        lineTo(
            size.width * (UNIT_FLOAT - GLASS_HORIZONTAL_INSET),
            waterTop - size.height * (WATER_RIGHT_LIFT_FRACTION - topGapFraction)
        )
        lineTo(
            size.width * (UNIT_FLOAT - GLASS_HORIZONTAL_INSET),
            size.height * GLASS_BOTTOM_RIGHT_Y
        )
        lineTo(
            size.width * GLASS_HORIZONTAL_INSET,
            size.height * GLASS_BOTTOM_LEFT_Y
        )
        close()
    }
}

private const val ORIGIN = 0f
private const val NO_MOTION = 0f
private const val UNIT_FLOAT = 1f
private const val DIAMETER_MULTIPLIER = 2f
private const val FULL_CIRCLE_RADIANS = 6.2831855f
private const val DOUBLE_PHASE_RADIANS = FULL_CIRCLE_RADIANS * 2f
private const val GLOW_CENTER_X = 0.72f
private const val GLOW_CENTER_Y = 0.34f
private const val GLOW_RADIUS = 0.55f
private const val WATER_TOP_FRACTION = 0.63f
private const val WATER_LEFT_DROP_FRACTION = 0.018f
private const val WATER_RIGHT_LIFT_FRACTION = 0.018f
private const val WATER_CLIP_HEADROOM_FRACTION = 0.014f
private const val WATER_SURFACE_SEGMENTS = 56
private const val WATER_SURFACE_PRIMARY_CYCLES = 2.6f
private const val WATER_SURFACE_SECONDARY_CYCLES = 5.3f
private const val WATER_SURFACE_PHASE_OFFSET = 1.1f
private const val WATER_SURFACE_BASE_AMPLITUDE = 0.0042f
private const val WATER_SURFACE_ACTIVE_AMPLITUDE = 0.0092f
private const val WATER_SURFACE_STROKE = 1.05f
private const val WATER_SURFACE_GLOW_STROKE = 3.4f
private const val WATER_SURFACE_GLOW_ALPHA_MULTIPLIER = 0.42f
private const val WATER_GRADIENT_SURFACE_STOP = 0f
private const val WATER_GRADIENT_SHALLOW_STOP = 0.10f
private const val WATER_GRADIENT_TRANSITION_STOP = 0.29f
private const val WATER_GRADIENT_MID_STOP = 0.74f
private const val WATER_GRADIENT_BOTTOM_STOP = 1f
private const val WATER_HIGHLIGHT_START_X = 0.12f
private const val WATER_HIGHLIGHT_END_X = 0.88f
private const val WATER_VOLUME_HIGHLIGHT_ALPHA_MULTIPLIER = 0.72f
private const val WATER_VOLUME_ACCENT_ALPHA_MULTIPLIER = 0.48f
private const val PRIMARY_WAVE_WEIGHT = 0.72f
private const val SECONDARY_WAVE_WEIGHT = 0.28f
private const val FIRST_SEGMENT = 0
private const val AIRFLOW_LINE_COUNT = 4
private const val AIRFLOW_LINE_SPACING = 0.019f
private const val AIRFLOW_PHASE_SPACING = 0.9f
private const val AIRFLOW_DRIFT_FRACTION = 0.006f
private const val AIRFLOW_ORIGIN_X = 0.57f
private const val AIRFLOW_ORIGIN_Y = 0.42f
private const val AIRFLOW_CONTROL_ONE_X = 0.53f
private const val AIRFLOW_CONTROL_ONE_Y = 0.50f
private const val AIRFLOW_CONTROL_TWO_X = 0.47f
private const val AIRFLOW_CONTROL_TWO_Y = 0.59f
private const val AIRFLOW_TARGET_X = 0.45f
private const val AIRFLOW_TARGET_Y = 0.67f
private const val AIRFLOW_STROKE = 1.1f
private const val REFLECTION_CENTER_X = 0.46f
private const val REFLECTION_CENTER_Y = 0.70f
private const val REFLECTION_RADIUS_X = 0.13f
private const val REFLECTION_RADIUS_Y = 0.026f
private const val REFLECTION_PULSE_BASE = 0.92f
private const val REFLECTION_PULSE_RANGE = 0.08f
private const val REFLECTION_ACCENT_ALPHA_MULTIPLIER = 0.55f
private const val GLASS_EDGE_STROKE = 1.2f
private const val GLASS_HORIZONTAL_INSET = 0.018f
private const val GLASS_TOP_GAP_FRACTION = 0.012f
private const val GLASS_BOTTOM_LEFT_Y = 0.975f
private const val GLASS_BOTTOM_RIGHT_Y = 0.94f
