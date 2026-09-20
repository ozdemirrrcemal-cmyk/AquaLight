package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.root

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightChannelOutputSnapshot
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/**
 * Real-time visual twin of the physical aquarium light output.
 *
 * The scene never owns an independent "sunrise" animation. It renders the effective channel
 * percentages reported by the device, so manual, automatic and custom transitions all share the
 * same source of truth as the hardware.
 */
@Composable
internal fun DeviceLightAquariumScene(
    channels: List<DeviceLightChannelOutputSnapshot>,
    outputActive: Boolean?,
    modifier: Modifier = Modifier
) {
    val target = remember(channels, outputActive) {
        deviceLightAquariumOutput(channels, outputActive)
    }
    val red by animateFloatAsState(
        targetValue = target.red,
        animationSpec = tween(
            durationMillis = CHANNEL_TRANSITION_DURATION_MS,
            easing = FastOutSlowInEasing
        ),
        label = "aquarium-red"
    )
    val green by animateFloatAsState(
        targetValue = target.green,
        animationSpec = tween(
            durationMillis = CHANNEL_TRANSITION_DURATION_MS,
            easing = FastOutSlowInEasing
        ),
        label = "aquarium-green"
    )
    val blue by animateFloatAsState(
        targetValue = target.blue,
        animationSpec = tween(
            durationMillis = CHANNEL_TRANSITION_DURATION_MS,
            easing = FastOutSlowInEasing
        ),
        label = "aquarium-blue"
    )
    val white by animateFloatAsState(
        targetValue = target.white,
        animationSpec = tween(
            durationMillis = CHANNEL_TRANSITION_DURATION_MS,
            easing = FastOutSlowInEasing
        ),
        label = "aquarium-white"
    )
    val animatedOutput = DeviceLightAquariumOutput(
        red = red,
        green = green,
        blue = blue,
        white = white
    )
    val shimmerPhase = rememberAquariumShimmerPhase(animatedOutput.peak > MIN_VISIBLE_OUTPUT)

    Canvas(modifier = modifier) {
        drawDarkAquariumBase()
        drawDynamicIllumination(animatedOutput)
        drawFixtureEmission(animatedOutput)
        drawWaterShimmer(animatedOutput, shimmerPhase)
        drawHeroLegibilityVeils()
    }
}

@Immutable
internal data class DeviceLightAquariumOutput(
    val red: Float = 0f,
    val green: Float = 0f,
    val blue: Float = 0f,
    val white: Float = 0f
) {
    val peak: Float
        get() = max(max(red, green), max(blue, white))
}

internal fun deviceLightAquariumOutput(
    channels: List<DeviceLightChannelOutputSnapshot>,
    outputActive: Boolean?
): DeviceLightAquariumOutput {
    if (outputActive == false) return DeviceLightAquariumOutput()

    var red = 0f
    var green = 0f
    var blue = 0f
    var white = 0f
    channels.forEach { channel ->
        val level = channel.effectivePercent.coerceIn(0, 100) / 100f
        when (channel.key.trim().lowercase()) {
            "red", "r" -> red = max(red, level)
            "green", "g" -> green = max(green, level)
            "blue", "b" -> blue = max(blue, level)
            "white", "w" -> white = max(white, level)
        }
    }
    return DeviceLightAquariumOutput(
        red = red,
        green = green,
        blue = blue,
        white = white
    )
}

@Composable
private fun rememberAquariumShimmerPhase(enabled: Boolean): Float {
    if (!enabled) return 0f
    val transition = rememberInfiniteTransition(label = "aquarium-shimmer")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = SHIMMER_DURATION_MS,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "aquarium-shimmer-phase"
    )
    return phase
}

private fun DrawScope.drawDarkAquariumBase() {
    val width = size.width
    val height = size.height
    val tankLeft = width * TANK_LEFT
    val tankRight = width * TANK_RIGHT
    val tankTop = height * TANK_TOP
    val tankBottom = height * TANK_BOTTOM
    val waterTop = height * WATER_TOP
    val substrateTop = height * SUBSTRATE_TOP
    val tankWidth = tankRight - tankLeft
    val tankHeight = tankBottom - tankTop

    drawRect(
        brush = Brush.horizontalGradient(
            colors = listOf(
                Color(0xFF071725),
                Color(0xFF0A2030),
                Color(0xFF06131F)
            )
        )
    )

    drawRoundRect(
        color = Color(0xFF041219),
        topLeft = Offset(tankLeft, tankTop),
        size = Size(tankWidth, tankHeight),
        cornerRadius = CornerRadius(width * 0.010f, width * 0.010f)
    )
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF0B2730),
                Color(0xFF071C23),
                Color(0xFF051319)
            ),
            startY = waterTop,
            endY = tankBottom
        ),
        topLeft = Offset(tankLeft, waterTop),
        size = Size(tankWidth, tankBottom - waterTop)
    )

    val substrate = Path().apply {
        moveTo(tankLeft, substrateTop + height * 0.018f)
        cubicTo(
            tankLeft + tankWidth * 0.22f,
            substrateTop - height * 0.006f,
            tankLeft + tankWidth * 0.46f,
            substrateTop + height * 0.028f,
            tankLeft + tankWidth * 0.67f,
            substrateTop + height * 0.004f
        )
        cubicTo(
            tankLeft + tankWidth * 0.82f,
            substrateTop - height * 0.008f,
            tankRight - tankWidth * 0.07f,
            substrateTop + height * 0.010f,
            tankRight,
            substrateTop
        )
        lineTo(tankRight, tankBottom)
        lineTo(tankLeft, tankBottom)
        close()
    }
    drawPath(
        path = substrate,
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFF263027), Color(0xFF121B18)),
            startY = substrateTop,
            endY = tankBottom
        )
    )

    val wood = Path().apply {
        moveTo(tankLeft + tankWidth * 0.41f, tankBottom - height * 0.050f)
        cubicTo(
            tankLeft + tankWidth * 0.48f,
            tankBottom - height * 0.17f,
            tankLeft + tankWidth * 0.54f,
            waterTop + height * 0.10f,
            tankLeft + tankWidth * 0.64f,
            waterTop + height * 0.16f
        )
        cubicTo(
            tankLeft + tankWidth * 0.59f,
            waterTop + height * 0.20f,
            tankLeft + tankWidth * 0.58f,
            tankBottom - height * 0.16f,
            tankLeft + tankWidth * 0.52f,
            tankBottom - height * 0.04f
        )
        close()
    }
    drawPath(wood, color = Color(0xFF33281D))
    drawPath(
        path = wood,
        color = Color(0xFF5A4330).copy(alpha = 0.32f),
        style = Stroke(width = width * 0.003f, cap = StrokeCap.Round)
    )

    drawPlantCluster(
        centerX = tankLeft + tankWidth * 0.10f,
        baseY = tankBottom - height * 0.035f,
        height = height * 0.34f,
        leafColor = Color(0xFF345A38)
    )
    drawPlantCluster(
        centerX = tankLeft + tankWidth * 0.21f,
        baseY = tankBottom - height * 0.030f,
        height = height * 0.29f,
        leafColor = Color(0xFF6A3B38)
    )
    drawPlantCluster(
        centerX = tankLeft + tankWidth * 0.33f,
        baseY = tankBottom - height * 0.028f,
        height = height * 0.22f,
        leafColor = Color(0xFF3E6A3D)
    )
    drawPlantCluster(
        centerX = tankLeft + tankWidth * 0.70f,
        baseY = tankBottom - height * 0.032f,
        height = height * 0.26f,
        leafColor = Color(0xFF466E38)
    )
    drawPlantCluster(
        centerX = tankLeft + tankWidth * 0.83f,
        baseY = tankBottom - height * 0.030f,
        height = height * 0.33f,
        leafColor = Color(0xFF71423B)
    )

    repeat(11) { index ->
        val x = tankLeft + tankWidth * (0.06f + index * 0.081f)
        val radius = width * (0.0028f + (index % 3) * 0.0008f)
        drawCircle(
            color = Color(0xFF69715B).copy(alpha = 0.48f),
            radius = radius,
            center = Offset(
                x,
                tankBottom - height * (0.020f + (index % 2) * 0.009f)
            )
        )
    }

    drawLine(
        color = Color(0xFF8AA7AE).copy(alpha = 0.20f),
        start = Offset(tankLeft + width * 0.004f, waterTop),
        end = Offset(tankRight - width * 0.004f, waterTop),
        strokeWidth = max(1f, width * 0.0012f)
    )
    drawRoundRect(
        color = Color(0xFFA9C1C8).copy(alpha = 0.20f),
        topLeft = Offset(tankLeft, tankTop),
        size = Size(tankWidth, tankHeight),
        cornerRadius = CornerRadius(width * 0.010f, width * 0.010f),
        style = Stroke(width = max(1f, width * 0.0016f))
    )

    val fixtureLeft = width * FIXTURE_LEFT
    val fixtureRight = width * FIXTURE_RIGHT
    val fixtureTop = height * FIXTURE_TOP
    val fixtureBottom = height * FIXTURE_BOTTOM
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFF18222A), Color(0xFF070B0E))
        ),
        topLeft = Offset(fixtureLeft, fixtureTop),
        size = Size(fixtureRight - fixtureLeft, fixtureBottom - fixtureTop),
        cornerRadius = CornerRadius(width * 0.009f, width * 0.009f)
    )
    drawLine(
        color = Color.White.copy(alpha = 0.18f),
        start = Offset(fixtureLeft + width * 0.008f, fixtureTop + height * 0.010f),
        end = Offset(fixtureRight - width * 0.008f, fixtureTop + height * 0.010f),
        strokeWidth = max(1f, width * 0.0012f)
    )
}

private fun DrawScope.drawPlantCluster(
    centerX: Float,
    baseY: Float,
    height: Float,
    leafColor: Color
) {
    repeat(7) { index ->
        val normalized = index / 6f
        val lateral = (index - 3) * height * 0.050f
        val tipX = centerX + lateral
        val tipY = baseY - height * (0.52f + normalized * 0.40f)
        drawLine(
            color = leafColor.copy(alpha = 0.72f),
            start = Offset(centerX, baseY),
            end = Offset(tipX, tipY),
            strokeWidth = max(1f, size.width * 0.0013f),
            cap = StrokeCap.Round
        )
        drawOval(
            color = leafColor.copy(alpha = 0.88f),
            topLeft = Offset(
                tipX - height * 0.048f,
                tipY - height * 0.055f
            ),
            size = Size(height * 0.096f, height * 0.12f)
        )
        if (index % 2 == 0) {
            drawOval(
                color = leafColor.copy(alpha = 0.64f),
                topLeft = Offset(
                    centerX + lateral * 0.54f - height * 0.040f,
                    baseY - height * (0.40f + normalized * 0.25f)
                ),
                size = Size(height * 0.080f, height * 0.10f)
            )
        }
    }
}

private fun DrawScope.drawDynamicIllumination(output: DeviceLightAquariumOutput) {
    if (output.peak <= MIN_VISIBLE_OUTPUT) return

    val left = size.width * TANK_LEFT
    val right = size.width * TANK_RIGHT
    val top = size.height * WATER_TOP
    val bottom = size.height * TANK_BOTTOM
    val tankWidth = right - left

    clipRect(left = left, top = top, right = right, bottom = bottom) {
        drawLightPass(
            centerX = left + tankWidth * 0.20f,
            level = output.red,
            color = Color(0xFFFF3048)
        )
        drawLightPass(
            centerX = left + tankWidth * 0.40f,
            level = output.green,
            color = Color(0xFF39E86A)
        )
        drawLightPass(
            centerX = left + tankWidth * 0.62f,
            level = output.blue,
            color = Color(0xFF2E67FF)
        )
        drawLightPass(
            centerX = left + tankWidth * 0.80f,
            level = output.white,
            color = Color(0xFFF1F7FF),
            whitePass = true
        )

        val peak = output.peak.perceptual()
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.16f * peak),
                    Color.Transparent
                ),
                startY = top,
                endY = top + (bottom - top) * 0.34f
            ),
            topLeft = Offset(left, top),
            size = Size(right - left, bottom - top),
            blendMode = BlendMode.Screen
        )
    }
}

private fun DrawScope.drawLightPass(
    centerX: Float,
    level: Float,
    color: Color,
    whitePass: Boolean = false
) {
    if (level <= MIN_VISIBLE_OUTPUT) return

    val gain = level.perceptual()
    val left = size.width * TANK_LEFT
    val right = size.width * TANK_RIGHT
    val top = size.height * WATER_TOP
    val bottom = size.height * TANK_BOTTOM
    val tankWidth = right - left
    val coneTopHalfWidth = tankWidth * if (whitePass) 0.12f else 0.09f
    val coneBottomHalfWidth = tankWidth * if (whitePass) 0.28f else 0.22f
    val cone = Path().apply {
        moveTo(centerX - coneTopHalfWidth, top)
        lineTo(centerX + coneTopHalfWidth, top)
        lineTo(centerX + coneBottomHalfWidth, bottom)
        lineTo(centerX - coneBottomHalfWidth, bottom)
        close()
    }

    val passAlpha = if (whitePass) 0.42f else 0.34f
    drawPath(
        path = cone,
        brush = Brush.verticalGradient(
            colors = listOf(
                color.copy(alpha = passAlpha * gain),
                color.copy(alpha = passAlpha * 0.42f * gain),
                Color.Transparent
            ),
            startY = top,
            endY = bottom
        ),
        blendMode = BlendMode.Screen
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                color.copy(alpha = (if (whitePass) 0.38f else 0.30f) * gain),
                color.copy(alpha = 0.08f * gain),
                Color.Transparent
            ),
            center = Offset(centerX, top),
            radius = tankWidth * 0.36f
        ),
        radius = tankWidth * 0.36f,
        center = Offset(centerX, top),
        blendMode = BlendMode.Screen
    )
}

private fun DrawScope.drawFixtureEmission(output: DeviceLightAquariumOutput) {
    val fixtureLeft = size.width * FIXTURE_LEFT
    val fixtureRight = size.width * FIXTURE_RIGHT
    val stripY = size.height * LED_STRIP_Y
    val stripHeight = size.height * LED_STRIP_HEIGHT
    val stripWidth = fixtureRight - fixtureLeft
    val segmentGap = stripWidth * 0.018f
    val segmentWidth = (stripWidth - segmentGap * 5f) / 4f
    val segments = listOf(
        Color(0xFFFF3048) to output.red,
        Color(0xFF39E86A) to output.green,
        Color(0xFF2E67FF) to output.blue,
        Color(0xFFF1F7FF) to output.white
    )

    segments.forEachIndexed { index, (color, level) ->
        val left = fixtureLeft + segmentGap + index * (segmentWidth + segmentGap)
        val gain = level.perceptual()
        drawRoundRect(
            color = if (gain > MIN_VISIBLE_OUTPUT) {
                color.copy(alpha = 0.24f + 0.76f * gain)
            } else {
                Color(0xFF22303A)
            },
            topLeft = Offset(left, stripY),
            size = Size(segmentWidth, stripHeight),
            cornerRadius = CornerRadius(stripHeight, stripHeight)
        )
        if (gain > MIN_VISIBLE_OUTPUT) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        color.copy(alpha = 0.28f * gain),
                        Color.Transparent
                    ),
                    center = Offset(left + segmentWidth / 2f, stripY + stripHeight),
                    radius = segmentWidth * 1.15f
                ),
                radius = segmentWidth * 1.15f,
                center = Offset(left + segmentWidth / 2f, stripY + stripHeight),
                blendMode = BlendMode.Screen
            )
        }
    }
}

private fun DrawScope.drawWaterShimmer(
    output: DeviceLightAquariumOutput,
    phase: Float
) {
    if (output.peak <= MIN_VISIBLE_OUTPUT) return

    val left = size.width * TANK_LEFT
    val right = size.width * TANK_RIGHT
    val top = size.height * WATER_TOP
    val bottom = size.height * TANK_BOTTOM
    val width = right - left
    val gain = output.peak.perceptual()
    val shimmerColor = Color(
        red = (output.red * 0.70f + output.white).coerceIn(0f, 1f),
        green = (output.green * 0.70f + output.white).coerceIn(0f, 1f),
        blue = (output.blue * 0.70f + output.white).coerceIn(0f, 1f),
        alpha = 1f
    )

    clipRect(left = left, top = top, right = right, bottom = bottom) {
        repeat(5) { index ->
            val baseY = top + (bottom - top) * (0.10f + index * 0.13f)
            val waveShift = sin(
                phase * 2.0 * PI + index * 0.9
            ).toFloat() * width * 0.035f
            val path = Path().apply {
                moveTo(left - width * 0.08f + waveShift, baseY)
                cubicTo(
                    left + width * 0.19f + waveShift,
                    baseY - size.height * 0.018f,
                    left + width * 0.36f + waveShift,
                    baseY + size.height * 0.020f,
                    left + width * 0.54f + waveShift,
                    baseY
                )
                cubicTo(
                    left + width * 0.70f + waveShift,
                    baseY - size.height * 0.016f,
                    left + width * 0.88f + waveShift,
                    baseY + size.height * 0.018f,
                    right + width * 0.08f + waveShift,
                    baseY
                )
            }
            drawPath(
                path = path,
                color = shimmerColor.copy(alpha = 0.040f * gain),
                style = Stroke(
                    width = max(1f, size.width * 0.0011f),
                    cap = StrokeCap.Round
                ),
                blendMode = BlendMode.Screen
            )
        }
    }
}

private fun DrawScope.drawHeroLegibilityVeils() {
    drawRect(
        brush = Brush.horizontalGradient(
            0.00f to Color(0xF2071724),
            0.30f to Color(0xD9071724),
            0.46f to Color.Transparent
        )
    )
    drawRect(
        brush = Brush.horizontalGradient(
            0.76f to Color.Transparent,
            0.88f to Color(0xA8071724),
            1.00f to Color(0xE6071724)
        )
    )
}

private fun Float.perceptual(): Float =
    coerceIn(0f, 1f).toDouble().pow(INVERSE_GAMMA).toFloat()

private const val CHANNEL_TRANSITION_DURATION_MS = 360
private const val SHIMMER_DURATION_MS = 5_600
private const val MIN_VISIBLE_OUTPUT = 0.004f
private const val INVERSE_GAMMA = 1.0 / 2.2

private const val TANK_LEFT = 0.315f
private const val TANK_RIGHT = 0.885f
private const val TANK_TOP = 0.285f
private const val TANK_BOTTOM = 0.975f
private const val WATER_TOP = 0.355f
private const val SUBSTRATE_TOP = 0.825f

private const val FIXTURE_LEFT = 0.365f
private const val FIXTURE_RIGHT = 0.835f
private const val FIXTURE_TOP = 0.125f
private const val FIXTURE_BOTTOM = 0.285f
private const val LED_STRIP_Y = 0.245f
private const val LED_STRIP_HEIGHT = 0.018f
