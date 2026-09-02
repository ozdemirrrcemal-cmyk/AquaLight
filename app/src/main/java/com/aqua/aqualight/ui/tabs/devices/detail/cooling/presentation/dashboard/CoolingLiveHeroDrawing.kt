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
    drawWaterBody()
    if (motionIntensity > NO_MOTION) {
        drawAirflow(motionPhase, motionIntensity)
        drawLocalRipples(motionPhase, motionIntensity)
    }
    drawWaterHighlights(motionPhase, motionIntensity)
    drawGlassEdge()
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

private fun DrawScope.drawWaterBody() {
    val waterTop = size.height * WATER_TOP_FRACTION
    val water = Path().apply {
        moveTo(ORIGIN, waterTop + size.height * WATER_LEFT_DROP_FRACTION)
        cubicTo(
            size.width * WATER_CONTROL_ONE_X,
            waterTop - size.height * WATER_CONTROL_ONE_LIFT,
            size.width * WATER_CONTROL_TWO_X,
            waterTop + size.height * WATER_CONTROL_TWO_DROP,
            size.width,
            waterTop - size.height * WATER_RIGHT_LIFT_FRACTION
        )
        lineTo(size.width, size.height)
        lineTo(ORIGIN, size.height)
        close()
    }
    drawPath(
        path = water,
        brush = Brush.verticalGradient(
            colors = listOf(
                AquaCoolingDashboardPalette.accent.copy(
                    alpha = AquaCoolingDashboardAlpha.liveHeroWater
                ),
                AquaCoolingDashboardPalette.insetSurface,
                Color.Black
            ),
            startY = waterTop,
            endY = size.height
        )
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
        val y = spec.baseY + sin(radians.toDouble()).toFloat() * spec.amplitude
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

private fun DrawScope.drawGlassEdge() {
    val waterTop = size.height * WATER_TOP_FRACTION
    drawLine(
        color = AquaCoolingDashboardPalette.primaryText.copy(
            alpha = AquaCoolingDashboardAlpha.liveHeroWaterEdge
        ),
        start = Offset(ORIGIN, waterTop + size.height * WATER_LEFT_DROP_FRACTION),
        end = Offset(size.width, waterTop - size.height * WATER_RIGHT_LIFT_FRACTION),
        strokeWidth = GLASS_EDGE_STROKE
    )
    drawLine(
        color = AquaCoolingDashboardPalette.accent.copy(alpha = GLASS_EDGE_GLOW_ALPHA),
        start = Offset(ORIGIN, waterTop + size.height * WATER_LEFT_DROP_FRACTION + GLASS_EDGE_GAP),
        end = Offset(
            size.width,
            waterTop - size.height * WATER_RIGHT_LIFT_FRACTION + GLASS_EDGE_GAP
        ),
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
private const val GLOW_CENTER_X = 0.72f
private const val GLOW_CENTER_Y = 0.34f
private const val GLOW_RADIUS = 0.55f
private const val WATER_TOP_FRACTION = 0.63f
private const val WATER_LEFT_DROP_FRACTION = 0.06f
private const val WATER_RIGHT_LIFT_FRACTION = 0.06f
private const val WATER_CONTROL_ONE_X = 0.27f
private const val WATER_CONTROL_ONE_LIFT = 0.04f
private const val WATER_CONTROL_TWO_X = 0.72f
private const val WATER_CONTROL_TWO_DROP = 0.02f
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
