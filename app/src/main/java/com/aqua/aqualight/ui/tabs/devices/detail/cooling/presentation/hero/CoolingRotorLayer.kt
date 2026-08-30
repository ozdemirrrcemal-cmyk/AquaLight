package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.hero

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.design.CoolingHeroGeometry
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.design.CoolingHeroMotion
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.design.CoolingHeroMotionPalette
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

/**
 * Perspective-correct visual cue for a running fan.
 *
 * We never rotate the already-perspective-warped bitmap intake. The reviewed static intake is
 * veiled into a motion-blurred disc, then rotating arc streaks are drawn in a circular rotor plane
 * and projected into the intake ellipse. The original hub pixels are restored last so the center
 * stays physically stable while only the blade field appears to spin.
 */
@Composable
internal fun CoolingRotorLayer(
    artwork: ImageBitmap,
    fanIntensity: Float,
    modifier: Modifier = Modifier
) {
    val normalizedFanIntensity = fanIntensity.coerceIn(0f, 1f)
    val rotation = remember { Animatable(0f) }

    LaunchedEffect(normalizedFanIntensity) {
        if (normalizedFanIntensity < CoolingHeroMotion.minimumRunningFraction) {
            rotation.snapTo(0f)
            return@LaunchedEffect
        }
        while (isActive) {
            val duration = (
                CoolingHeroMotion.rotorSlowDurationMillis -
                    (CoolingHeroMotion.rotorSlowDurationMillis -
                        CoolingHeroMotion.rotorFastDurationMillis) * normalizedFanIntensity
                ).roundToInt()
            rotation.animateTo(
                targetValue = rotation.value + FULL_ROTATION,
                animationSpec = tween(durationMillis = duration, easing = LinearEasing)
            )
            rotation.snapTo(rotation.value % FULL_ROTATION)
        }
    }

    if (normalizedFanIntensity < CoolingHeroMotion.minimumRunningFraction) return

    Canvas(modifier = modifier) {
        val center = Offset(
            x = size.width * CoolingHeroGeometry.rotorCenterXRatio,
            y = size.height * CoolingHeroGeometry.rotorCenterYRatio
        )
        val radiusX = size.width * CoolingHeroGeometry.rotorMotionRadiusXRatio
        val radiusY = size.height * CoolingHeroGeometry.rotorMotionRadiusYRatio
        val clip = Path().apply {
            addOval(
                Rect(
                    left = center.x - radiusX,
                    top = center.y - radiusY,
                    right = center.x + radiusX,
                    bottom = center.y + radiusY
                )
            )
        }
        val veilAlpha = (
            CoolingHeroMotion.rotorVeilBaseAlpha +
                CoolingHeroMotion.rotorVeilFanAlphaGain * normalizedFanIntensity
            ).coerceIn(0f, 1f)
        val projectionScaleY = radiusY / radiusX
        val arcRadius = radiusX * ARC_RADIUS_RATIO
        val arcBoundsTopLeft = Offset(center.x - arcRadius, center.y - arcRadius)
        val arcBoundsSize = Size(arcRadius * 2f, arcRadius * 2f)

        clipPath(clip) {
            drawOval(
                color = CoolingHeroMotionPalette.rotorVeil.copy(alpha = veilAlpha),
                topLeft = Offset(center.x - radiusX, center.y - radiusY),
                size = Size(radiusX * 2f, radiusY * 2f)
            )

            scale(
                scaleX = 1f,
                scaleY = projectionScaleY,
                pivot = center
            ) {
                rotate(degrees = rotation.value, pivot = center) {
                    repeat(PRIMARY_ARC_COUNT) { index ->
                        val startAngle = index * PRIMARY_ARC_STEP_DEGREES
                        drawArc(
                            color = CoolingHeroMotionPalette.rotorPrimary.copy(
                                alpha = CoolingHeroMotion.rotorPrimaryArcAlpha *
                                    (ARC_ALPHA_BASE + normalizedFanIntensity * ARC_ALPHA_GAIN)
                            ),
                            startAngle = startAngle,
                            sweepAngle = PRIMARY_ARC_SWEEP_DEGREES,
                            useCenter = false,
                            topLeft = arcBoundsTopLeft,
                            size = arcBoundsSize,
                            style = Stroke(width = CoolingHeroGeometry.rotorArcStroke.toPx())
                        )
                    }
                    repeat(SECONDARY_ARC_COUNT) { index ->
                        val startAngle = SECONDARY_ARC_OFFSET_DEGREES +
                            index * SECONDARY_ARC_STEP_DEGREES
                        drawArc(
                            color = CoolingHeroMotionPalette.rotorSecondary.copy(
                                alpha = CoolingHeroMotion.rotorSecondaryArcAlpha *
                                    (ARC_ALPHA_BASE + normalizedFanIntensity * ARC_ALPHA_GAIN)
                            ),
                            startAngle = startAngle,
                            sweepAngle = SECONDARY_ARC_SWEEP_DEGREES,
                            useCenter = false,
                            topLeft = arcBoundsTopLeft,
                            size = arcBoundsSize,
                            style = Stroke(
                                width = CoolingHeroGeometry.rotorSecondaryArcStroke.toPx()
                            )
                        )
                    }
                }
            }
        }

        restoreRotorHubFromArtwork(
            artwork = artwork,
            center = center,
            canvasSize = size
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.restoreRotorHubFromArtwork(
    artwork: ImageBitmap,
    center: Offset,
    canvasSize: Size
) {
    val hubRadiusX = canvasSize.width * CoolingHeroGeometry.rotorHubRadiusXRatio
    val hubRadiusY = canvasSize.height * CoolingHeroGeometry.rotorHubRadiusYRatio
    val sourceLeft = (
        artwork.width *
            (CoolingHeroGeometry.rotorCenterXRatio - CoolingHeroGeometry.rotorHubRadiusXRatio)
        ).roundToInt()
    val sourceTop = (
        artwork.height *
            (CoolingHeroGeometry.rotorCenterYRatio - CoolingHeroGeometry.rotorHubRadiusYRatio)
        ).roundToInt()
    val sourceWidth = maxOf(
        1,
        (artwork.width * CoolingHeroGeometry.rotorHubRadiusXRatio * 2f).roundToInt()
    )
    val sourceHeight = maxOf(
        1,
        (artwork.height * CoolingHeroGeometry.rotorHubRadiusYRatio * 2f).roundToInt()
    )

    drawImage(
        image = artwork,
        srcOffset = IntOffset(sourceLeft, sourceTop),
        srcSize = IntSize(sourceWidth, sourceHeight),
        dstOffset = IntOffset(
            x = (center.x - hubRadiusX).roundToInt(),
            y = (center.y - hubRadiusY).roundToInt()
        ),
        dstSize = IntSize(
            width = maxOf(1, (hubRadiusX * 2f).roundToInt()),
            height = maxOf(1, (hubRadiusY * 2f).roundToInt())
        )
    )
}

private const val FULL_ROTATION = 360f
private const val PRIMARY_ARC_COUNT = 9
private const val SECONDARY_ARC_COUNT = 9
private const val PRIMARY_ARC_STEP_DEGREES = 40f
private const val SECONDARY_ARC_STEP_DEGREES = 40f
private const val SECONDARY_ARC_OFFSET_DEGREES = 20f
private const val PRIMARY_ARC_SWEEP_DEGREES = 13f
private const val SECONDARY_ARC_SWEEP_DEGREES = 7f
private const val ARC_RADIUS_RATIO = 0.84f
private const val ARC_ALPHA_BASE = 0.58f
private const val ARC_ALPHA_GAIN = 0.42f
