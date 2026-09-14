package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp

internal fun DrawScope.drawAutomaticCycleDial(
    state: AutomaticCycleDialState,
    visuals: DeviceLightAutomaticEditorVisuals
) {
    val radius = size.minDimension / HALF_DIVISOR -
        DeviceLightAutomaticEditorGeometry.dialMarkerHaloRadius.toPx()
    drawAutomaticCycleCenterScrim(radius, visuals)
    drawAutomaticCycleTrack(radius, visuals)
    drawAutomaticCycleGuides(radius, visuals)
    drawAutomaticCycleActiveArc(state, radius, visuals)
    state.startTimeMs?.let { time ->
        drawAutomaticCycleMarker(time, radius, visuals.colors.card.warning)
    }
    state.endTimeMs?.let { time ->
        drawAutomaticCycleMarker(time, radius, visuals.colors.shrimp)
    }
    drawAutomaticCycleMoon(state, radius, visuals)
}

private fun DrawScope.drawAutomaticCycleCenterScrim(
    radius: Float,
    visuals: DeviceLightAutomaticEditorVisuals
) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                visuals.colors.card.mediaSurface.copy(
                    alpha = DeviceLightAutomaticEditorAlpha.centerScrim
                ),
                Color.Transparent
            ),
            center = center,
            radius = radius * DeviceLightAutomaticDialSpec.centerScrimRadiusFraction
        ),
        radius = radius * DeviceLightAutomaticDialSpec.centerScrimRadiusFraction
    )
}

private fun DrawScope.drawAutomaticCycleTrack(
    radius: Float,
    visuals: DeviceLightAutomaticEditorVisuals
) {
    drawCircle(
        color = visuals.colors.card.mediaOutline.copy(
            alpha = DeviceLightAutomaticEditorAlpha.neutralDial
        ),
        radius = radius,
        style = Stroke(width = DeviceLightAutomaticEditorGeometry.dialStrokeWidth.toPx())
    )
}

private fun DrawScope.drawAutomaticCycleGuides(
    radius: Float,
    visuals: DeviceLightAutomaticEditorVisuals
) {
    val stroke = DeviceLightAutomaticEditorGeometry.dialGuideStrokeWidth.toPx()
    repeat(DIAL_GUIDE_COUNT) { index ->
        val angle = index.toDouble() * FULL_ROTATION_RADIANS / DIAL_GUIDE_COUNT -
            QUARTER_ROTATION_RADIANS
        val major = index % MAJOR_GUIDE_INTERVAL == 0
        val inset = radius * if (major) MAJOR_GUIDE_INSET else MINOR_GUIDE_INSET
        drawLine(
            color = visuals.colors.card.secondaryText.copy(
                alpha = DeviceLightAutomaticEditorAlpha.dialGuide
            ),
            start = center.automaticCycleRadialOffset(angle, radius - inset),
            end = center.automaticCycleRadialOffset(angle, radius),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawAutomaticCycleActiveArc(
    state: AutomaticCycleDialState,
    radius: Float,
    visuals: DeviceLightAutomaticEditorVisuals
) {
    val start = state.startTimeMs
    val end = state.endTimeMs
    if (start == null || end == null || start == end) return
    val sweep = occupiedAutomaticCycleDuration(start, end).automaticCycleDegrees()
    val segmentSweep = sweep / ACTIVE_ARC_SEGMENTS
    repeat(ACTIVE_ARC_SEGMENTS) { index ->
        val fraction = (index + HALF_SEGMENT).toFloat() / ACTIVE_ARC_SEGMENTS
        drawArc(
            color = automaticCycleGradientColor(fraction, visuals),
            startAngle = start.automaticCycleDegrees() + segmentSweep * index +
                DeviceLightAutomaticDialSpec.topOriginDegrees,
            sweepAngle = segmentSweep + ARC_OVERLAP_DEGREES,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * HALF_DIVISOR, radius * HALF_DIVISOR),
            style = Stroke(
                width = DeviceLightAutomaticEditorGeometry.dialStrokeWidth.toPx(),
                cap = StrokeCap.Butt
            )
        )
    }
}

private fun DrawScope.drawAutomaticCycleMarker(timeMs: Long, radius: Float, color: Color) {
    val markerCenter = automaticCyclePoint(timeMs, radius)
    drawCircle(
        color = color.copy(alpha = DeviceLightAutomaticEditorAlpha.dialMarkerHalo),
        radius = DeviceLightAutomaticEditorGeometry.dialMarkerHaloRadius.toPx(),
        center = markerCenter
    )
    drawCircle(
        color = Color.White,
        radius = DeviceLightAutomaticEditorGeometry.dialMarkerRadius.toPx() +
            DeviceLightAutomaticEditorGeometry.dialMarkerOutlineWidth.toPx(),
        center = markerCenter
    )
    drawCircle(
        color = color,
        radius = DeviceLightAutomaticEditorGeometry.dialMarkerRadius.toPx(),
        center = markerCenter
    )
}

private fun DrawScope.drawAutomaticCycleMoon(
    state: AutomaticCycleDialState,
    radius: Float,
    visuals: DeviceLightAutomaticEditorVisuals
) {
    val moonTime = automaticCycleMoonTime(state.startTimeMs, state.endTimeMs)
    val moonCenter = automaticCyclePoint(
        moonTime,
        radius * DeviceLightAutomaticDialSpec.moonOrbitFraction
    )
    val moonRadius = DeviceLightAutomaticEditorGeometry.dialMoonSize.toPx() / HALF_DIVISOR
    drawCircle(color = visuals.colors.white, radius = moonRadius, center = moonCenter)
    drawCircle(
        color = visuals.colors.card.mediaSurface,
        radius = moonRadius * MOON_CUTOUT_RADIUS_FRACTION,
        center = moonCenter + Offset(
            moonRadius * MOON_CUTOUT_X_FRACTION,
            -moonRadius * MOON_CUTOUT_Y_FRACTION
        )
    )
}

private fun automaticCycleGradientColor(
    fraction: Float,
    visuals: DeviceLightAutomaticEditorVisuals
): Color {
    val colors = listOf(
        visuals.colors.card.warning,
        visuals.colors.green,
        visuals.colors.blue,
        visuals.colors.shrimp
    )
    val scaled = fraction.coerceIn(0f, 1f) * (colors.lastIndex)
    val firstIndex = scaled.toInt().coerceAtMost(colors.lastIndex - 1)
    return lerp(colors[firstIndex], colors[firstIndex + 1], scaled - firstIndex)
}

private fun DrawScope.automaticCyclePoint(timeMs: Long, radius: Float): Offset {
    val angle = Math.toRadians(
        timeMs.automaticCycleDegrees().toDouble() -
            DeviceLightAutomaticDialSpec.markerAngleOffsetDegrees
    )
    return center.automaticCycleRadialOffset(angle, radius)
}

private const val DIAL_GUIDE_COUNT = 24
private const val MAJOR_GUIDE_INTERVAL = 6
private const val ACTIVE_ARC_SEGMENTS = 48
private const val HALF_DIVISOR = 2f
private const val HALF_SEGMENT = 0.5
private const val FULL_ROTATION_RADIANS = Math.PI * 2.0
private const val QUARTER_ROTATION_RADIANS = Math.PI / 2.0
private const val MAJOR_GUIDE_INSET = 0.12f
private const val MINOR_GUIDE_INSET = 0.07f
private const val ARC_OVERLAP_DEGREES = 0.4f
private const val MOON_CUTOUT_RADIUS_FRACTION = 0.84f
private const val MOON_CUTOUT_X_FRACTION = 0.42f
private const val MOON_CUTOUT_Y_FRACTION = 0.12f
