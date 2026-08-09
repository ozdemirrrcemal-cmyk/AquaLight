package com.aqua.aqualight.ui.tabs.devices.detail.dosing

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

internal fun DrawScope.drawPumpIndicator(visualState: DosingPumpVisualState) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val outerRadius = size.minDimension / 2f
    val coreRadius = outerRadius * INDICATOR_CORE_RATIO

    when (visualState) {
        DosingPumpVisualState.IDLE -> Unit
        DosingPumpVisualState.RUNNING -> drawIndicatorGlow(
            center = center,
            outerRadius = outerRadius,
            glowColor = DosingPumpPalette.runningGlow
        )
        DosingPumpVisualState.ERROR -> drawIndicatorGlow(
            center = center,
            outerRadius = outerRadius,
            glowColor = DosingPumpPalette.errorGlow
        )
    }

    val indicatorBrush = when (visualState) {
        DosingPumpVisualState.IDLE -> DosingPumpPalette.idleIndicator
        DosingPumpVisualState.RUNNING -> DosingPumpPalette.runningIndicator
        DosingPumpVisualState.ERROR -> DosingPumpPalette.errorIndicator
    }

    drawCircle(
        brush = indicatorBrush,
        radius = coreRadius,
        center = center
    )
    drawCircle(
        color = DosingPumpPalette.indicatorEdge,
        radius = coreRadius,
        center = center,
        style = Stroke(width = INDICATOR_EDGE_WIDTH.toPx())
    )
}

private fun DrawScope.drawIndicatorGlow(
    center: Offset,
    outerRadius: Float,
    glowColor: Color
) {
    drawCircle(
        color = glowColor.copy(alpha = GLOW_OUTER_ALPHA),
        radius = outerRadius,
        center = center
    )
    drawCircle(
        color = glowColor.copy(alpha = GLOW_MIDDLE_ALPHA),
        radius = outerRadius * GLOW_MIDDLE_RADIUS_RATIO,
        center = center
    )
    drawCircle(
        color = glowColor.copy(alpha = GLOW_INNER_ALPHA),
        radius = outerRadius * GLOW_INNER_RADIUS_RATIO,
        center = center
    )
}

private const val INDICATOR_CORE_RATIO = 0.36f
private const val GLOW_OUTER_ALPHA = 0.10f
private const val GLOW_MIDDLE_ALPHA = 0.20f
private const val GLOW_INNER_ALPHA = 0.34f
private const val GLOW_MIDDLE_RADIUS_RATIO = 0.72f
private const val GLOW_INNER_RADIUS_RATIO = 0.50f
private const val INDICATOR_EDGE_WIDTH_DP = 1
private val INDICATOR_EDGE_WIDTH = INDICATOR_EDGE_WIDTH_DP.dp
