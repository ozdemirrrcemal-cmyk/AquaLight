package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.root

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntSize
import com.aqua.aqualight.R
import kotlin.math.roundToInt

/**
 * GPU-only projection of the authoritative RGBW output onto the aquarium artwork.
 *
 * The underlying bitmap is decoded once by Compose. Lighting changes are expressed as draw
 * operations and color matrices, so sunrise/sunset ramps do not allocate frame sequences, GIFs,
 * videos, or a second Light state owner.
 */
@Composable
internal fun DeviceLightDynamicAquarium(
    lighting: DeviceLightAquariumLighting,
    modifier: Modifier = Modifier
) {
    val transition = tween<Float>(
        durationMillis = LIGHTING_TRANSITION_MS,
        easing = LinearEasing
    )
    val red by animateFloatAsState(
        targetValue = lighting.red,
        animationSpec = transition,
        label = "lightHeroRed"
    )
    val green by animateFloatAsState(
        targetValue = lighting.green,
        animationSpec = transition,
        label = "lightHeroGreen"
    )
    val blue by animateFloatAsState(
        targetValue = lighting.blue,
        animationSpec = transition,
        label = "lightHeroBlue"
    )
    val white by animateFloatAsState(
        targetValue = lighting.white,
        animationSpec = transition,
        label = "lightHeroWhite"
    )
    val intensity by animateFloatAsState(
        targetValue = lighting.intensity,
        animationSpec = transition,
        label = "lightHeroIntensity"
    )

    val artwork = ImageBitmap.imageResource(R.drawable.device_light_hero_card)
    val illuminatedFilter = remember(red, green, blue, white) {
        ColorFilter.colorMatrix(
            aquariumIlluminationMatrix(
                red = red,
                green = green,
                blue = blue,
                white = white
            )
        )
    }
    val illuminationColor = remember(red, green, blue, white) {
        aquariumIlluminationColor(
            red = red,
            green = green,
            blue = blue,
            white = white
        )
    }

    Canvas(modifier = modifier) {
        val destination = IntSize(
            width = size.width.roundToInt().coerceAtLeast(1),
            height = size.height.roundToInt().coerceAtLeast(1)
        )

        drawImage(
            image = artwork,
            dstSize = destination,
            colorFilter = DARK_AQUARIUM_FILTER
        )

        if (intensity > MIN_VISIBLE_LIGHT) {
            drawImage(
                image = artwork,
                dstSize = destination,
                alpha = (MIN_ILLUMINATED_PLATE_ALPHA +
                    intensity * ILLUMINATED_PLATE_ALPHA_RANGE).coerceIn(0f, 1f),
                colorFilter = illuminatedFilter
            )
            drawAquariumLightSpill(
                illuminationColor = illuminationColor,
                intensity = intensity
            )
            drawFixtureChannelGlows(
                red = red,
                green = green,
                blue = blue,
                white = white
            )
        }

        // The original commercial artwork had fixed UI chrome at the edges. These continuous
        // vignettes turn it back into scene artwork without introducing another heavyweight asset.
        drawLegacyChromeOcclusion()
    }
}

private fun DrawScope.drawAquariumLightSpill(
    illuminationColor: Color,
    intensity: Float
) {
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                illuminationColor.copy(alpha = 0.34f * intensity),
                illuminationColor.copy(alpha = 0.17f * intensity),
                Color.Transparent
            ),
            startY = size.height * 0.18f,
            endY = size.height * 0.92f
        ),
        topLeft = Offset(0f, size.height * 0.14f),
        size = Size(size.width, size.height * 0.78f),
        blendMode = BlendMode.Screen
    )

    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                illuminationColor.copy(alpha = 0.30f * intensity),
                Color.Transparent
            ),
            startY = size.height * 0.29f,
            endY = size.height * 0.47f
        ),
        topLeft = Offset(size.width * 0.32f, size.height * 0.27f),
        size = Size(size.width * 0.61f, size.height * 0.22f),
        blendMode = BlendMode.Screen
    )
}

private fun DrawScope.drawFixtureChannelGlows(
    red: Float,
    green: Float,
    blue: Float,
    white: Float
) {
    drawChannelGlow(
        xFraction = 0.49f,
        level = red,
        color = LED_RED
    )
    drawChannelGlow(
        xFraction = 0.59f,
        level = green,
        color = LED_GREEN
    )
    drawChannelGlow(
        xFraction = 0.70f,
        level = blue,
        color = LED_BLUE
    )
    drawChannelGlow(
        xFraction = 0.81f,
        level = white,
        color = Color.White
    )
}

private fun DrawScope.drawChannelGlow(
    xFraction: Float,
    level: Float,
    color: Color
) {
    if (level <= MIN_VISIBLE_LIGHT) return

    val center = Offset(
        x = size.width * xFraction,
        y = size.height * 0.31f
    )
    val radius = size.height * 0.25f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                color.copy(alpha = 0.33f * level),
                color.copy(alpha = 0.12f * level),
                Color.Transparent
            ),
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center,
        blendMode = BlendMode.Screen
    )
}

private fun DrawScope.drawLegacyChromeOcclusion() {
    val edge = AQUARIUM_EDGE_COLOR

    drawRect(
        brush = Brush.horizontalGradient(
            colors = listOf(
                edge.copy(alpha = 0.94f),
                edge.copy(alpha = 0.76f),
                Color.Transparent
            ),
            startX = 0f,
            endX = size.width * 0.35f
        ),
        topLeft = Offset.Zero,
        size = Size(size.width * 0.35f, size.height)
    )

    drawRect(
        brush = Brush.horizontalGradient(
            colors = listOf(
                Color.Transparent,
                edge.copy(alpha = 0.80f),
                edge.copy(alpha = 0.95f)
            ),
            startX = size.width * 0.78f,
            endX = size.width
        ),
        topLeft = Offset(size.width * 0.78f, 0f),
        size = Size(size.width * 0.22f, size.height)
    )
}

private fun aquariumIlluminationMatrix(
    red: Float,
    green: Float,
    blue: Float,
    white: Float
): ColorMatrix {
    val whiteLift = white * 0.36f
    val redGain = (0.62f + red * 0.72f + whiteLift).coerceIn(0f, 1.55f)
    val greenGain = (0.62f + green * 0.72f + whiteLift).coerceIn(0f, 1.55f)
    val blueGain = (0.62f + blue * 0.72f + whiteLift).coerceIn(0f, 1.55f)

    return ColorMatrix(
        floatArrayOf(
            redGain, 0f, 0f, 0f, 0f,
            0f, greenGain, 0f, 0f, 0f,
            0f, 0f, blueGain, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    )
}

private fun aquariumIlluminationColor(
    red: Float,
    green: Float,
    blue: Float,
    white: Float
): Color {
    val neutralWhite = white * WHITE_TO_RGB_VISUAL_MIX
    val rawRed = red + neutralWhite + COLOR_FLOOR
    val rawGreen = green + neutralWhite + COLOR_FLOOR
    val rawBlue = blue + neutralWhite + COLOR_FLOOR
    val peak = maxOf(rawRed, rawGreen, rawBlue, COLOR_FLOOR)

    return Color(
        red = (rawRed / peak).coerceIn(COLOR_MINIMUM_COMPONENT, 1f),
        green = (rawGreen / peak).coerceIn(COLOR_MINIMUM_COMPONENT, 1f),
        blue = (rawBlue / peak).coerceIn(COLOR_MINIMUM_COMPONENT, 1f)
    )
}

private val DARK_AQUARIUM_FILTER = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            0.14f, 0f, 0f, 0f, 0f,
            0f, 0.16f, 0f, 0f, 0f,
            0f, 0f, 0.20f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    )
)

private val AQUARIUM_EDGE_COLOR = Color(0xFF061726)
private val LED_RED = Color(0xFFFF3B30)
private val LED_GREEN = Color(0xFF35E76F)
private val LED_BLUE = Color(0xFF2F6BFF)

private const val LIGHTING_TRANSITION_MS = 900
private const val MIN_VISIBLE_LIGHT = 0.002f
private const val MIN_ILLUMINATED_PLATE_ALPHA = 0.12f
private const val ILLUMINATED_PLATE_ALPHA_RANGE = 0.88f
private const val WHITE_TO_RGB_VISUAL_MIX = 0.86f
private const val COLOR_FLOOR = 0.04f
private const val COLOR_MINIMUM_COMPONENT = 0.08f
