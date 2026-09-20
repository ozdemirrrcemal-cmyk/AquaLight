package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.root

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.withSaveLayer

/**
 * Relights the existing texture; never generates, resizes, or copies a bitmap per frame.
 * The source already contains bright LEDs, so diffuse exposure alone is insufficient:
 * a small feathered lamp pass replaces those baked-in highlights with per-channel output.
 * Both passes use the same full-bounds mapping as the original hero's FillBounds image.
 */
internal class HeroAquariumRenderer(private val artworkSize: Size) {
    private val lamp = HeroLampGeometry(artworkSize)
    private val layerPaint = Paint()

    fun draw(
        scope: DrawScope,
        artwork: Painter,
        channels: HeroLightChannels,
        profile: HeroLightProfile
    ) = with(scope) {
        if (channels == fullyLitHeroChannels) {
            // No filter, blend, or overlay: the full-output artwork is pixel-identical.
            with(artwork) { draw(size = artworkSize) }
        } else {
            val exposure = channels.sceneExposure(profile)
            with(artwork) { draw(size = artworkSize, colorFilter = exposure.toColorFilter()) }
            drawLamp(artwork, channels, exposure)
        }
    }

    private fun DrawScope.drawLamp(
        artwork: Painter,
        channels: HeroLightChannels,
        exposure: HeroSceneExposure
    ) {
        val bounds = lamp.bounds
        clipRect(bounds.left, bounds.top, bounds.right, bounds.bottom) {
            drawIntoCanvas { canvas ->
                // Restrict the intermediate surface to the light bar, not the whole card.
                canvas.withSaveLayer(bounds, layerPaint) {
                    // Off emitters retain dim reflected light, not black cut-out rectangles.
                    with(artwork) { draw(size = artworkSize, colorFilter = exposure.toLampReflectionFilter()) }
                    drawEmission(artwork, channels)
                    drawRect(
                        brush = lamp.verticalFeather,
                        topLeft = bounds.topLeft,
                        size = bounds.size,
                        blendMode = BlendMode.DstIn
                    )
                    drawRect(
                        brush = lamp.horizontalFeather,
                        topLeft = bounds.topLeft,
                        size = bounds.size,
                        blendMode = BlendMode.DstIn
                    )
                }
            }
        }
    }

    private fun DrawScope.drawEmission(artwork: Painter, channels: HeroLightChannels) {
        drawIntoCanvas { canvas ->
            canvas.withSaveLayer(lamp.bounds, layerPaint) {
                with(artwork) { draw(size = artworkSize) }
                drawRect(
                    brush = lamp.outputBrush(channels),
                    topLeft = lamp.bounds.topLeft,
                    size = lamp.bounds.size,
                    blendMode = BlendMode.DstIn
                )
            }
        }
    }
}

private fun HeroSceneExposure.toColorFilter(): ColorFilter {
    val ambientRed = ambient * LUMA_RED
    val ambientGreen = ambient * LUMA_GREEN
    val ambientBlue = ambient * LUMA_BLUE
    return ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                red + ambientRed, ambientGreen, ambientBlue, 0f, 0f,
                ambientRed, green + ambientGreen, ambientBlue, 0f, 0f,
                ambientRed, ambientGreen, blue + ambientBlue, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    )
}

/** Neutral surface reflection is distinct from a channel's self-emitted colored hotspot. */
private fun HeroSceneExposure.toLampReflectionFilter(): ColorFilter {
    val reflectedRed = red * LAMP_REFLECTION_GAIN + ambient * LAMP_AMBIENT_GAIN
    val reflectedGreen = green * LAMP_REFLECTION_GAIN + ambient * LAMP_AMBIENT_GAIN
    val reflectedBlue = blue * LAMP_REFLECTION_GAIN + ambient * LAMP_AMBIENT_GAIN
    return ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                reflectedRed * LUMA_RED, reflectedRed * LUMA_GREEN, reflectedRed * LUMA_BLUE, 0f, 0f,
                reflectedGreen * LUMA_RED, reflectedGreen * LUMA_GREEN, reflectedGreen * LUMA_BLUE, 0f, 0f,
                reflectedBlue * LUMA_RED, reflectedBlue * LUMA_GREEN, reflectedBlue * LUMA_BLUE, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    )
}

private val fullyLitHeroChannels = HeroLightChannels(1f, 1f, 1f, 1f)
private const val LUMA_RED = 0.2126f
private const val LUMA_GREEN = 0.7152f
private const val LUMA_BLUE = 0.0722f
private const val LAMP_REFLECTION_GAIN = 0.28f
private const val LAMP_AMBIENT_GAIN = 0.12f
