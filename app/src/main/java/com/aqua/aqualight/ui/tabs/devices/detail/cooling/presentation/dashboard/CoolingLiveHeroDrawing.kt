package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.dashboard

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardAlpha
import com.aqua.aqualight.ui.common.cooling.AquaCoolingDashboardPalette
import kotlin.math.sin

internal fun DrawScope.drawCoolingHeroScene(
    motionPhase: Float,
    motionIntensity: Float,
    status: CoolingHeroVisualStatus
) {
    drawCoolingAtmosphere(status)
    drawWaterBody(motionPhase, motionIntensity)
    if (motionIntensity > NO_MOTION) {
        drawAirflow(motionPhase, motionIntensity)
        drawLocalRipples(motionPhase, motionIntensity)
    }
    drawWaterHighlights(motionPhase, motionIntensity)
    drawGlassTank()
}

private fun DrawScope.drawCoolingAtmosphere(status: CoolingHeroVisualStatus) {
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.Black,
                AquaCoolingDashboardPalette.cardSurface,
                AquaCoolingDashboardPalette.insetSurface
            ),
            start = Offset.Zero,
            end = Offset(size.width, size.height)
        )
    )
    val glowColor = when (status) {
        CoolingHeroVisualStatus.ATTENTION -> AquaCoolingDashboardPalette.warning
        CoolingHeroVisualStatus.OFFLINE,
        CoolingHeroVisualStatus.WAITING_FOR_DATA -> AquaCoolingDashboardPalette.secondaryText
        CoolingHeroVisualStatus.COOLING,
        CoolingHeroVisualStatus.STANDBY -> AquaCoolingDashboardPalette.accent
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

private fun DrawScope.drawWaterBody(motionPhase: Float, motionIntensity: Float) {
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
            WATER_GRADIENT_SURFACE_STOP to AquaCoolingDashboardPalette.accent.copy(
                alpha = AquaCoolingDashboardAlpha.liveHeroWater
            ),
            WATER_GRADIENT_TRANSITION_STOP to AquaCoolingDashboardPalette.insetSurface.copy(
                alpha = AquaCoolingDashboardAlpha.liveHeroWaterDepth
            ),
            WATER_GRADIENT_MID_STOP to AquaCoolingDashboardPalette.cardSurface,
            WATER_GRADIENT_BOTTOM_STOP to Color.Black,
            startY = waterTop,
            endY = size.height
        )
    )

    drawWaterSurfaceSheen(motionPhase, motionIntensity)
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

private fun DrawScope.drawWaterSurfaceSheen(motionPhase: Float, motionIntensity: Float) {
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
        color = AquaCoolingDashboardPalette.primaryText.copy(
            alpha = AquaCoolingDashboardAlpha.liveHeroWaterEdge
        ),
        style = Stroke(width = WATER_SURFACE_STROKE, cap = StrokeCap.Round)
    )
}

private fun DrawScope.drawWaterHighlights(motionPhase: Float, motionIntensity: Float) {
    val dynamicAmplitude = BASE_WAVE_AMPLITUDE +
        ACTIVE_WAVE_AMPLITUDE * motionIntensity
    drawWave(
        CoolingWaveSpec(
            baseY = size.height * PRIMARY_WAVE_Y,
            amplitude = size.height * dynamicAmplitude,
            cycles = PRIMARY_WAVE_CYCLES,
            phase = motionPhase,
            color = AquaCoolingDashboardPalette.primaryText.copy(
                alpha = AquaCoolingDashboardAlpha.liveHeroWavePrimary
            ),
            strokeWidth = PRIMARY_WAVE_STROKE
        )
    )
    drawWave(
        CoolingWaveSpec(
            baseY = size.height * SECONDARY_WAVE_Y,
            amplitude = size.height * SECONDARY_WAVE_AMPLITUDE,
            cycles = SECONDARY_WAVE_CYCLES,
            phase = motionPhase + SECONDARY_PHASE_OFFSET,
            color = AquaCoolingDashboardPalette.accent.copy(
                alpha = AquaCoolingDashboardAlpha.liveHeroWaveSecondary
            ),
            strokeWidth = SECONDARY_WAVE_STROKE
        )
    )
    drawWave(
        CoolingWaveSpec(
            baseY = size.height * DEEP_WAVE_Y,
            amplitude = size.height * DEEP_WAVE_AMPLITUDE,
            cycles = DEEP_WAVE_CYCLES,
            phase = motionPhase + DEEP_PHASE_OFFSET,
            color = AquaCoolingDashboardPalette.primaryText.copy(alpha = DEEP_WAVE_ALPHA),
            strokeWidth = DEEP_WAVE_STROKE
        )
    )
}

private fun DrawScope.drawWave(spec: CoolingWaveSpec) {
    val path = Path()
    for (index in 0..WAVE_SEGMENTS) {
        val progress = index.toFloat() / WAVE_SEGMENTS
        val x = size.width * progress
        val radians = progress * spec.cycles * FULL_CIRCLE_RADIANS +
            spec.phase * FULL_CIRCLE_RADIANS
        val secondaryRadians = progress * spec.cycles * WAVE_DETAIL_FREQUENCY *
            FULL_CIRCLE_RADIANS - spec.phase * DOUBLE_PHASE_RADIANS + WAVE_DETAIL_PHASE_OFFSET
        val wave = sin(radians.toDouble()).toFloat() * PRIMARY_WAVE_WEIGHT +
            sin(secondaryRadians.toDouble()).toFloat() * SECONDARY_WAVE_WEIGHT
        val y = spec.baseY + wave * spec.amplitude
        if (index == FIRST_SEGMENT) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(
        path = path,
        color = spec.color,
        style = Stroke(width = spec.strokeWidth, cap = StrokeCap.Round)
    )
}

private fun DrawScope.drawAirflow(motionPhase: Float, motionIntensity: Float) {
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
            color = AquaCoolingDashboardPalette.primaryText.copy(
                alpha = AquaCoolingDashboardAlpha.liveHeroAirflow * motionIntensity
            ),
            style = Stroke(width = AIRFLOW_STROKE, cap = StrokeCap.Round)
        )
    }
}

private fun DrawScope.drawLocalRipples(motionPhase: Float, motionIntensity: Float) {
    repeat(RIPPLE_COUNT) { index ->
        val progress = (motionPhase + index.toFloat() / RIPPLE_COUNT) % UNIT_FLOAT
        val radiusX = size.width * (
            RIPPLE_START_WIDTH + progress * RIPPLE_WIDTH_EXPANSION
            )
        val radiusY = size.height * (
            RIPPLE_START_HEIGHT + progress * RIPPLE_HEIGHT_EXPANSION
            )
        val center = Offset(size.width * RIPPLE_CENTER_X, size.height * RIPPLE_CENTER_Y)
        drawOval(
            color = AquaCoolingDashboardPalette.accent.copy(
                alpha = (UNIT_FLOAT - progress) * RIPPLE_ALPHA * motionIntensity
            ),
            topLeft = Offset(center.x - radiusX, center.y - radiusY),
            size = Size(radiusX * DIAMETER_MULTIPLIER, radiusY * DIAMETER_MULTIPLIER),
            style = Stroke(width = RIPPLE_STROKE)
        )
    }
}

private fun DrawScope.drawGlassTank() {
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
                AquaCoolingDashboardPalette.primaryText.copy(
                    alpha = AquaCoolingDashboardAlpha.liveHeroGlassPane
                )
            ),
            startY = waterTop,
            endY = size.height
        )
    )
    drawPath(
        path = pane,
        color = AquaCoolingDashboardPalette.primaryText.copy(
            alpha = AquaCoolingDashboardAlpha.liveHeroGlassEdge
        ),
        style = Stroke(width = GLASS_EDGE_STROKE, cap = StrokeCap.Round)
    )
    drawLine(
        color = AquaCoolingDashboardPalette.primaryText.copy(
            alpha = AquaCoolingDashboardAlpha.liveHeroWaterEdge
        ),
        start = topLeft,
        end = topRight,
        strokeWidth = GLASS_EDGE_STROKE
    )
    drawLine(
        color = AquaCoolingDashboardPalette.accent.copy(alpha = GLASS_EDGE_GLOW_ALPHA),
        start = topLeft.copy(y = topLeft.y + GLASS_EDGE_GAP),
        end = topRight.copy(y = topRight.y + GLASS_EDGE_GAP),
        strokeWidth = GLASS_EDGE_GLOW_STROKE
    )
}

private data class CoolingWaveSpec(
    val baseY: Float,
    val amplitude: Float,
    val cycles: Float,
    val phase: Float,
    val color: Color,
    val strokeWidth: Float
)

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
private const val WATER_LEFT_DROP_FRACTION = 0.06f
private const val WATER_RIGHT_LIFT_FRACTION = 0.06f
private const val WATER_SURFACE_SEGMENTS = 56
private const val WATER_SURFACE_PRIMARY_CYCLES = 2.6f
private const val WATER_SURFACE_SECONDARY_CYCLES = 5.3f
private const val WATER_SURFACE_PHASE_OFFSET = 1.1f
private const val WATER_SURFACE_BASE_AMPLITUDE = 0.0035f
private const val WATER_SURFACE_ACTIVE_AMPLITUDE = 0.008f
private const val WATER_SURFACE_STROKE = 1.15f
private const val WATER_GRADIENT_SURFACE_STOP = 0f
private const val WATER_GRADIENT_TRANSITION_STOP = 0.16f
private const val WATER_GRADIENT_MID_STOP = 0.58f
private const val WATER_GRADIENT_BOTTOM_STOP = 1f
private const val PRIMARY_WAVE_WEIGHT = 0.72f
private const val SECONDARY_WAVE_WEIGHT = 0.28f
private const val BASE_WAVE_AMPLITUDE = 0.008f
private const val ACTIVE_WAVE_AMPLITUDE = 0.014f
private const val PRIMARY_WAVE_Y = 0.665f
private const val PRIMARY_WAVE_CYCLES = 3.2f
private const val PRIMARY_WAVE_STROKE = 1.7f
private const val SECONDARY_WAVE_Y = 0.735f
private const val SECONDARY_WAVE_AMPLITUDE = 0.012f
private const val SECONDARY_WAVE_CYCLES = 2.4f
private const val SECONDARY_PHASE_OFFSET = 0.38f
private const val SECONDARY_WAVE_STROKE = 1.2f
private const val DEEP_WAVE_Y = 0.84f
private const val DEEP_WAVE_AMPLITUDE = 0.009f
private const val DEEP_WAVE_CYCLES = 4.1f
private const val DEEP_PHASE_OFFSET = 0.67f
private const val DEEP_WAVE_ALPHA = 0.13f
private const val DEEP_WAVE_STROKE = 1f
private const val WAVE_DETAIL_FREQUENCY = 1.85f
private const val WAVE_DETAIL_PHASE_OFFSET = 0.72f
private const val WAVE_SEGMENTS = 48
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
private const val RIPPLE_COUNT = 3
private const val RIPPLE_CENTER_X = 0.45f
private const val RIPPLE_CENTER_Y = 0.70f
private const val RIPPLE_START_WIDTH = 0.035f
private const val RIPPLE_WIDTH_EXPANSION = 0.12f
private const val RIPPLE_START_HEIGHT = 0.009f
private const val RIPPLE_HEIGHT_EXPANSION = 0.028f
private const val RIPPLE_ALPHA = 0.36f
private const val RIPPLE_STROKE = 1.3f
private const val GLASS_EDGE_STROKE = 1.2f
private const val GLASS_EDGE_GAP = 3f
private const val GLASS_EDGE_GLOW_ALPHA = 0.20f
private const val GLASS_EDGE_GLOW_STROKE = 0.8f
private const val GLASS_HORIZONTAL_INSET = 0.018f
private const val GLASS_TOP_GAP_FRACTION = 0.012f
private const val GLASS_BOTTOM_LEFT_Y = 0.975f
private const val GLASS_BOTTOM_RIGHT_Y = 0.94f
