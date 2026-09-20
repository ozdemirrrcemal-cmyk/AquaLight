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
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightChannelOutputSnapshot
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/**
 * Photo-based real-time visual twin of the physical aquarium output.
 *
 * The base artwork is a lights-off derivative of the aquarium supplied for this screen. UI labels,
 * health status and telemetry stay outside the artwork. RGBW illumination is rendered from the
 * authoritative effective channel output; there is no independent fake sunrise animation.
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
    val red by animatedAquariumChannel(target.red, "aquarium-red")
    val green by animatedAquariumChannel(target.green, "aquarium-green")
    val blue by animatedAquariumChannel(target.blue, "aquarium-blue")
    val white by animatedAquariumChannel(target.white, "aquarium-white")
    val output = DeviceLightAquariumOutput(
        red = red,
        green = green,
        blue = blue,
        white = white
    )
    val shimmerPhase = rememberAquariumShimmerPhase(output.peak > MIN_VISIBLE_OUTPUT)

    Box(modifier = modifier) {
        Image(
            painter = painterResource(R.drawable.device_light_aquarium_base_dark),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize()
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawPhotoIllumination(output)
            drawFixtureEmission(output)
            drawWaterShimmer(output, shimmerPhase)
            drawPhotoLegibilityVeils()
        }
    }
}

@Composable
private fun animatedAquariumChannel(
    target: Float,
    label: String
) = animateFloatAsState(
    targetValue = target,
    animationSpec = tween(
        durationMillis = CHANNEL_TRANSITION_DURATION_MS,
        easing = FastOutSlowInEasing
    ),
    label = label
)

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

private fun DrawScope.drawPhotoIllumination(output: DeviceLightAquariumOutput) {
    if (output.peak <= MIN_VISIBLE_OUTPUT) return

    val left = size.width * TANK_LEFT
    val right = size.width * TANK_RIGHT
    val waterTop = size.height * WATER_TOP
    val bottom = size.height * TANK_BOTTOM
    val width = right - left

    clipRect(left = left, top = waterTop, right = right, bottom = bottom) {
        drawPhotoLightPass(
            centerX = size.width * RED_CENTER_X,
            level = output.red,
            color = RED_LIGHT,
            top = waterTop,
            bottom = bottom,
            tankWidth = width
        )
        drawPhotoLightPass(
            centerX = size.width * GREEN_CENTER_X,
            level = output.green,
            color = GREEN_LIGHT,
            top = waterTop,
            bottom = bottom,
            tankWidth = width
        )
        drawPhotoLightPass(
            centerX = size.width * BLUE_CENTER_X,
            level = output.blue,
            color = BLUE_LIGHT,
            top = waterTop,
            bottom = bottom,
            tankWidth = width
        )
        drawPhotoLightPass(
            centerX = size.width * WHITE_CENTER_X,
            level = output.white,
            color = WHITE_LIGHT,
            top = waterTop,
            bottom = bottom,
            tankWidth = width,
            whitePass = true
        )

        val whiteGain = output.white.perceptual()
        if (whiteGain > MIN_VISIBLE_OUTPUT) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.18f * whiteGain),
                        Color.White.copy(alpha = 0.055f * whiteGain),
                        Color.Transparent
                    ),
                    startY = waterTop,
                    endY = bottom
                ),
                blendMode = BlendMode.Screen
            )
        }
    }
}

private fun DrawScope.drawPhotoLightPass(
    centerX: Float,
    level: Float,
    color: Color,
    top: Float,
    bottom: Float,
    tankWidth: Float,
    whitePass: Boolean = false
) {
    if (level <= MIN_VISIBLE_OUTPUT) return

    val gain = level.perceptual()
    val topHalfWidth = tankWidth * if (whitePass) 0.115f else 0.072f
    val bottomHalfWidth = tankWidth * if (whitePass) 0.31f else 0.20f
    val path = Path().apply {
        moveTo(centerX - topHalfWidth, top)
        lineTo(centerX + topHalfWidth, top)
        lineTo(centerX + bottomHalfWidth, bottom)
        lineTo(centerX - bottomHalfWidth, bottom)
        close()
    }

    val peakAlpha = if (whitePass) 0.28f else 0.22f
    drawPath(
        path = path,
        brush = Brush.verticalGradient(
            colors = listOf(
                color.copy(alpha = peakAlpha * gain),
                color.copy(alpha = peakAlpha * 0.48f * gain),
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
                color.copy(alpha = (if (whitePass) 0.24f else 0.20f) * gain),
                color.copy(alpha = 0.055f * gain),
                Color.Transparent
            ),
            center = Offset(centerX, top),
            radius = tankWidth * if (whitePass) 0.30f else 0.22f
        ),
        radius = tankWidth * if (whitePass) 0.30f else 0.22f,
        center = Offset(centerX, top),
        blendMode = BlendMode.Screen
    )
}

private fun DrawScope.drawFixtureEmission(output: DeviceLightAquariumOutput) {
    if (output.peak <= MIN_VISIBLE_OUTPUT) return

    val stripY = size.height * LED_STRIP_Y
    val stripHeight = size.height * LED_STRIP_HEIGHT
    val tankWidth = size.width * (TANK_RIGHT - TANK_LEFT)
    val passes = listOf(
        Triple(RED_CENTER_X, RED_LIGHT, output.red),
        Triple(GREEN_CENTER_X, GREEN_LIGHT, output.green),
        Triple(BLUE_CENTER_X, BLUE_LIGHT, output.blue),
        Triple(WHITE_CENTER_X, WHITE_LIGHT, output.white)
    )

    passes.forEach { (centerFraction, color, level) ->
        if (level <= MIN_VISIBLE_OUTPUT) return@forEach
        val gain = level.perceptual()
        val centerX = size.width * centerFraction
        drawLine(
            color = color.copy(alpha = 0.70f * gain),
            start = Offset(centerX - tankWidth * 0.055f, stripY),
            end = Offset(centerX + tankWidth * 0.055f, stripY),
            strokeWidth = stripHeight,
            cap = StrokeCap.Round,
            blendMode = BlendMode.Screen
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    color.copy(alpha = 0.34f * gain),
                    Color.Transparent
                ),
                center = Offset(centerX, stripY + stripHeight),
                radius = tankWidth * 0.13f
            ),
            center = Offset(centerX, stripY + stripHeight),
            radius = tankWidth * 0.13f,
            blendMode = BlendMode.Screen
        )
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
    val bottom = size.height * (WATER_TOP + 0.23f)
    val width = right - left
    val gain = output.peak.perceptual()
    val shimmer = mixedLightColor(output).copy(alpha = 0.045f * gain)

    clipRect(left = left, top = top, right = right, bottom = bottom) {
        repeat(4) { index ->
            val baseY = top + (bottom - top) * (0.15f + index * 0.21f)
            val shift = sin(phase * 2.0 * PI + index * 0.85).toFloat() * width * 0.030f
            val path = Path().apply {
                moveTo(left - width * 0.08f + shift, baseY)
                cubicTo(
                    left + width * 0.22f + shift,
                    baseY - size.height * 0.012f,
                    left + width * 0.42f + shift,
                    baseY + size.height * 0.012f,
                    left + width * 0.62f + shift,
                    baseY
                )
                cubicTo(
                    left + width * 0.78f + shift,
                    baseY - size.height * 0.010f,
                    left + width * 0.92f + shift,
                    baseY + size.height * 0.010f,
                    right + width * 0.08f + shift,
                    baseY
                )
            }
            drawPath(
                path = path,
                color = shimmer,
                style = Stroke(
                    width = max(1f, size.width * 0.0012f),
                    cap = StrokeCap.Round
                ),
                blendMode = BlendMode.Screen
            )
        }
    }
}

private fun DrawScope.drawPhotoLegibilityVeils() {
    drawRect(
        brush = Brush.horizontalGradient(
            0.00f to Color(0xB8071724),
            0.27f to Color(0x7A071724),
            0.43f to Color.Transparent
        )
    )
    drawRect(
        brush = Brush.horizontalGradient(
            0.78f to Color.Transparent,
            0.90f to Color(0x45071724),
            1.00f to Color(0x9E071724)
        )
    )
}

private fun mixedLightColor(output: DeviceLightAquariumOutput): Color {
    val sum = output.red + output.green + output.blue + output.white
    if (sum <= MIN_VISIBLE_OUTPUT) return Color.Transparent
    return Color(
        red = ((output.red + output.white) / sum).coerceIn(0f, 1f),
        green = ((output.green + output.white) / sum).coerceIn(0f, 1f),
        blue = ((output.blue + output.white) / sum).coerceIn(0f, 1f),
        alpha = 1f
    )
}

private fun Float.perceptual(): Float =
    coerceIn(0f, 1f).toDouble().pow(INVERSE_GAMMA).toFloat()

private val RED_LIGHT = Color(0xFFFF3350)
private val GREEN_LIGHT = Color(0xFF35ED6C)
private val BLUE_LIGHT = Color(0xFF356CFF)
private val WHITE_LIGHT = Color(0xFFF5FAFF)

private const val CHANNEL_TRANSITION_DURATION_MS = 360
private const val SHIMMER_DURATION_MS = 5_800
private const val MIN_VISIBLE_OUTPUT = 0.004f
private const val INVERSE_GAMMA = 1.0 / 2.2

private const val TANK_LEFT = 0.345f
private const val TANK_RIGHT = 0.915f
private const val WATER_TOP = 0.215f
private const val TANK_BOTTOM = 0.965f

private const val RED_CENTER_X = 0.462f
private const val GREEN_CENTER_X = 0.565f
private const val BLUE_CENTER_X = 0.675f
private const val WHITE_CENTER_X = 0.805f

private const val LED_STRIP_Y = 0.225f
private const val LED_STRIP_HEIGHT = 5.2f / 300f
