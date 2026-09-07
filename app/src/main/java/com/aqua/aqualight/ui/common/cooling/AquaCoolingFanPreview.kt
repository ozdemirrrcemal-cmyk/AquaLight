package com.aqua.aqualight.ui.common.cooling

import android.animation.ValueAnimator
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.aqua.aqualight.ui.common.devicecard.AquaDeviceCardColors
import kotlin.math.min
import kotlinx.coroutines.isActive

/**
 * AquaLight's shared target-speed preview for fan controls.
 *
 * The housing remains stationary while the seven-blade rotor follows the supplied target percent.
 * Motion is frame-clock driven so changing the slider adjusts velocity without resetting the rotor
 * angle. Android's reduced-motion setting is respected and zero output never schedules frames.
 */
@Composable
internal fun AquaCoolingFanPreview(
    percent: Int,
    colors: AquaDeviceCardColors,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val normalizedPercent = percent.coerceIn(
        AquaCoolingGaugeSpec.minimumPercent,
        AquaCoolingGaugeSpec.maximumPercent
    ).toFloat() / AquaCoolingGaugeSpec.maximumPercent
    val rotation = rememberFanRotation(normalizedPercent)

    Canvas(
        modifier = modifier.semantics {
            this.contentDescription = contentDescription
        }
    ) {
        drawFanPreview(
            rotationDegrees = rotation.value,
            intensity = normalizedPercent,
            colors = colors
        )
    }
}

@Composable
private fun rememberFanRotation(intensity: Float): State<Float> {
    val rotation = remember { mutableFloatStateOf(NO_ROTATION_DEGREES) }
    val currentSpeed = rememberUpdatedState(fanMotionDegreesPerSecond(intensity))
    val motionActive = intensity > NO_OUTPUT_INTENSITY && ValueAnimator.areAnimatorsEnabled()

    LaunchedEffect(motionActive) {
        if (!motionActive) return@LaunchedEffect
        var previousFrameNanos = withFrameNanos { frameNanos -> frameNanos }
        while (isActive) {
            val frameNanos = withFrameNanos { currentFrameNanos -> currentFrameNanos }
            val elapsedNanos = (frameNanos - previousFrameNanos)
                .coerceIn(NO_ELAPSED_NANOS, MAX_FRAME_GAP_NANOS)
            val elapsedSeconds = elapsedNanos * NANOSECONDS_TO_SECONDS
            rotation.floatValue = (
                rotation.floatValue + currentSpeed.value * elapsedSeconds
                ) % FULL_ROTATION_DEGREES
            previousFrameNanos = frameNanos
        }
    }
    return rotation
}

private fun DrawScope.drawFanPreview(
    rotationDegrees: Float,
    intensity: Float,
    colors: AquaDeviceCardColors
) {
    val geometry = FanPreviewGeometry(
        center = center,
        outerRadius = min(size.width, size.height) * OUTER_RADIUS_FRACTION
    )
    drawFanAtmosphere(geometry, intensity, colors)
    drawFanHousing(geometry, intensity, colors)
    drawFanRotor(geometry, rotationDegrees, colors)
    drawFanHub(geometry, colors)
}

private fun DrawScope.drawFanAtmosphere(
    geometry: FanPreviewGeometry,
    intensity: Float,
    colors: AquaDeviceCardColors
) {
    val activeRange = AquaCoolingDashboardAlpha.manualFanAmbientActive -
        AquaCoolingDashboardAlpha.manualFanAmbientIdle
    val glowAlpha = AquaCoolingDashboardAlpha.manualFanAmbientIdle + activeRange * intensity
    val glowRadius = geometry.outerRadius * GLOW_RADIUS_SCALE
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(colors.accent.copy(alpha = glowAlpha), Color.Transparent),
            center = geometry.center,
            radius = glowRadius
        ),
        radius = glowRadius,
        center = geometry.center
    )
}

private fun DrawScope.drawFanHousing(
    geometry: FanPreviewGeometry,
    intensity: Float,
    colors: AquaDeviceCardColors
) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                colors.mediaSurface.copy(
                    alpha = AquaCoolingDashboardAlpha.manualFanHousingCenter
                ),
                colors.surface.copy(alpha = AquaCoolingDashboardAlpha.manualFanHousingEdge)
            ),
            center = geometry.center,
            radius = geometry.outerRadius
        ),
        radius = geometry.outerRadius,
        center = geometry.center
    )
    drawCircle(
        color = colors.outline,
        radius = geometry.outerRadius,
        center = geometry.center,
        style = Stroke(width = AquaCoolingDashboardGeometry.manualFanHousingStrokeWidth.toPx())
    )
    drawFanAccentRing(geometry, intensity, colors)
    drawCircle(
        color = colors.surface.copy(alpha = AquaCoolingDashboardAlpha.manualFanShroud),
        radius = geometry.shroudRadius,
        center = geometry.center
    )
    drawCircle(
        color = colors.mediaOutline.copy(alpha = AquaCoolingDashboardAlpha.manualFanRing),
        radius = geometry.shroudRadius,
        center = geometry.center,
        style = Stroke(width = AquaCoolingDashboardGeometry.manualFanShroudStrokeWidth.toPx())
    )
}

private fun DrawScope.drawFanAccentRing(
    geometry: FanPreviewGeometry,
    intensity: Float,
    colors: AquaDeviceCardColors
) {
    val alpha = AquaCoolingDashboardAlpha.manualFanRing +
        intensity * AquaCoolingDashboardAlpha.manualFanAmbientActive
    val radius = geometry.accentRadius
    val bounds = Size(radius * DIAMETER_MULTIPLIER, radius * DIAMETER_MULTIPLIER)
    val topLeft = Offset(geometry.center.x - radius, geometry.center.y - radius)
    repeat(ACCENT_SEGMENT_COUNT) { index ->
        drawArc(
            color = colors.accent.copy(alpha = alpha),
            startAngle = ACCENT_START_ANGLE + index * ACCENT_SEGMENT_STEP,
            sweepAngle = ACCENT_SEGMENT_SWEEP,
            useCenter = false,
            topLeft = topLeft,
            size = bounds,
            style = Stroke(
                width = AquaCoolingDashboardGeometry.manualFanAccentStrokeWidth.toPx(),
                cap = StrokeCap.Round
            )
        )
    }
}

private fun DrawScope.drawFanRotor(
    geometry: FanPreviewGeometry,
    rotationDegrees: Float,
    colors: AquaDeviceCardColors
) {
    val blade = fanBladePath(geometry)
    val bladeBrush = Brush.linearGradient(
        colors = listOf(
            colors.primaryText.copy(
                alpha = AquaCoolingDashboardAlpha.manualFanBladeHighlight
            ),
            colors.accent.copy(alpha = AquaCoolingDashboardAlpha.manualFanBladeAccent),
            colors.secondaryText.copy(alpha = AquaCoolingDashboardAlpha.manualFanBladeShade)
        ),
        start = Offset(
            geometry.center.x + geometry.hubRadius,
            geometry.center.y - geometry.bladeRadius
        ),
        end = Offset(
            geometry.center.x + geometry.bladeRadius,
            geometry.center.y + geometry.hubRadius
        )
    )
    rotate(rotationDegrees, pivot = geometry.center) {
        repeat(FAN_BLADE_COUNT) { index ->
            rotate(index * FAN_BLADE_ANGLE, pivot = geometry.center) {
                drawPath(path = blade, brush = bladeBrush)
                drawPath(
                    path = blade,
                    color = colors.mediaOutline,
                    style = Stroke(
                        width = AquaCoolingDashboardGeometry.manualFanHousingStrokeWidth.toPx()
                    )
                )
            }
        }
    }
}

private fun fanBladePath(geometry: FanPreviewGeometry): Path {
    val center = geometry.center
    val hub = geometry.hubRadius
    val blade = geometry.bladeRadius
    return Path().apply {
        moveTo(center.x + hub * BLADE_START_X, center.y + hub * BLADE_START_Y)
        cubicTo(
            center.x + blade * BLADE_CONTROL_ONE_X,
            center.y + blade * BLADE_CONTROL_ONE_Y,
            center.x + blade * BLADE_CONTROL_TWO_X,
            center.y + blade * BLADE_CONTROL_TWO_Y,
            center.x + blade * BLADE_TIP_X,
            center.y + blade * BLADE_TIP_Y
        )
        cubicTo(
            center.x + blade * BLADE_RETURN_ONE_X,
            center.y + blade * BLADE_RETURN_ONE_Y,
            center.x + blade * BLADE_RETURN_TWO_X,
            center.y + blade * BLADE_RETURN_TWO_Y,
            center.x + hub * BLADE_END_X,
            center.y + hub * BLADE_END_Y
        )
        cubicTo(
            center.x + hub * BLADE_INNER_ONE_X,
            center.y + hub * BLADE_INNER_ONE_Y,
            center.x + hub * BLADE_INNER_TWO_X,
            center.y + hub * BLADE_INNER_TWO_Y,
            center.x + hub * BLADE_START_X,
            center.y + hub * BLADE_START_Y
        )
        close()
    }
}

private fun DrawScope.drawFanHub(
    geometry: FanPreviewGeometry,
    colors: AquaDeviceCardColors
) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                colors.primaryText.copy(
                    alpha = AquaCoolingDashboardAlpha.manualFanHubHighlight
                ),
                colors.accent.copy(alpha = AquaCoolingDashboardAlpha.manualFanBladeAccent),
                colors.mediaSurface.copy(alpha = AquaCoolingDashboardAlpha.manualFanHubShade)
            ),
            center = geometry.center,
            radius = geometry.hubRadius
        ),
        radius = geometry.hubRadius,
        center = geometry.center
    )
    drawCircle(
        color = colors.mediaOutline,
        radius = geometry.hubRadius,
        center = geometry.center,
        style = Stroke(width = AquaCoolingDashboardGeometry.manualFanHubStrokeWidth.toPx())
    )
    drawCircle(
        color = colors.primaryText.copy(alpha = AquaCoolingDashboardAlpha.manualFanRing),
        radius = geometry.hubRadius * HUB_GLINT_RADIUS_SCALE,
        center = Offset(
            geometry.center.x - geometry.hubRadius * HUB_GLINT_OFFSET_SCALE,
            geometry.center.y - geometry.hubRadius * HUB_GLINT_OFFSET_SCALE
        )
    )
}

private data class FanPreviewGeometry(
    val center: Offset,
    val outerRadius: Float
) {
    val accentRadius = outerRadius * ACCENT_RADIUS_SCALE
    val shroudRadius = outerRadius * SHROUD_RADIUS_SCALE
    val bladeRadius = outerRadius * BLADE_RADIUS_SCALE
    val hubRadius = outerRadius * HUB_RADIUS_SCALE
}

private const val NO_ROTATION_DEGREES = 0f
private const val NO_OUTPUT_INTENSITY = 0f
private const val FULL_ROTATION_DEGREES = 360f
private const val NO_ELAPSED_NANOS = 0L
private const val MAX_FRAME_GAP_NANOS = 100_000_000L
private const val NANOSECONDS_TO_SECONDS = 0.000000001f
private const val OUTER_RADIUS_FRACTION = 0.42f
private const val GLOW_RADIUS_SCALE = 1.17f
private const val ACCENT_RADIUS_SCALE = 0.91f
private const val SHROUD_RADIUS_SCALE = 0.77f
private const val BLADE_RADIUS_SCALE = 0.72f
private const val HUB_RADIUS_SCALE = 0.19f
private const val DIAMETER_MULTIPLIER = 2f
private const val ACCENT_SEGMENT_COUNT = 4
private const val ACCENT_START_ANGLE = -69f
private const val ACCENT_SEGMENT_STEP = 90f
private const val ACCENT_SEGMENT_SWEEP = 42f
private const val FAN_BLADE_COUNT = 7
private const val FAN_BLADE_ANGLE = FULL_ROTATION_DEGREES / FAN_BLADE_COUNT
private const val BLADE_START_X = 0.48f
private const val BLADE_START_Y = -0.72f
private const val BLADE_CONTROL_ONE_X = 0.40f
private const val BLADE_CONTROL_ONE_Y = -0.58f
private const val BLADE_CONTROL_TWO_X = 0.82f
private const val BLADE_CONTROL_TWO_Y = -0.48f
private const val BLADE_TIP_X = 0.91f
private const val BLADE_TIP_Y = -0.14f
private const val BLADE_RETURN_ONE_X = 0.79f
private const val BLADE_RETURN_ONE_Y = -0.01f
private const val BLADE_RETURN_TWO_X = 0.40f
private const val BLADE_RETURN_TWO_Y = 0.12f
private const val BLADE_END_X = 0.72f
private const val BLADE_END_Y = 0.42f
private const val BLADE_INNER_ONE_X = 0.48f
private const val BLADE_INNER_ONE_Y = 0.28f
private const val BLADE_INNER_TWO_X = 0.30f
private const val BLADE_INNER_TWO_Y = -0.22f
private const val HUB_GLINT_RADIUS_SCALE = 0.17f
private const val HUB_GLINT_OFFSET_SCALE = 0.28f
