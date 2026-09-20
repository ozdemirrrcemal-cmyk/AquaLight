package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.root

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader

/**
 * Size-scoped renderer using hardware-accelerated operations available on every supported API.
 * The original artwork is decoded by Image once. No bitmap is created, copied or recolored here.
 * Only small color filters change during a transition; paints and spatial masks are reused.
 */
internal class DeviceLightHeroRelighter(width: Float, height: Float) {
    private val bounds = RectF(0f, 0f, width, height)
    private val scenePaint = Paint()
    private val colorMatrix = ColorMatrix()
    private val emitters = deviceLightHeroEmitterRegions.map { region ->
        HeroEmitterLayer(region, width, height)
    }
    private var previousIllumination: DeviceLightHeroIllumination? = null

    fun draw(
        canvas: Canvas,
        illumination: DeviceLightHeroIllumination,
        drawArtwork: () -> Unit
    ) {
        // Reference endpoint: bypass even filtering/compositing so the original pixels are exact.
        if (illumination.isFull) {
            drawArtwork()
            return
        }
        updateFilters(illumination)
        canvas.withHeroLayer(bounds, scenePaint, drawArtwork)
        // Equal WRGB levels need no spatial correction, including a completely dark fixture.
        if (!illumination.isUniform) {
            emitters.forEach { emitter -> emitter.draw(canvas, drawArtwork) }
        }
    }

    private fun updateFilters(illumination: DeviceLightHeroIllumination) {
        if (previousIllumination == illumination) return
        val scene = illumination.sceneGains()
        colorMatrix.setScale(scene.red, scene.green, scene.blue, 1f)
        scenePaint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        emitters.forEach { emitter ->
            val gain = heroDisplayGain(illumination.level(emitter.channel))
            colorMatrix.setScale(gain, gain, gain, 1f)
            emitter.artworkPaint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        }
        previousIllumination = illumination
    }
}

private class HeroEmitterLayer(
    region: DeviceLightHeroEmitterRegion,
    width: Float,
    height: Float
) {
    val channel = region.channel
    val artworkPaint = Paint()
    private val centerX = region.centerX * width
    private val centerY = region.centerY * height
    private val radiusX = region.radiusX * width
    private val radiusY = region.radiusY * height
    private val bounds = RectF(
        centerX - radiusX,
        centerY - radiusY,
        centerX + radiusX,
        centerY + radiusY
    )
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            0f,
            0f,
            1f,
            intArrayOf(Color.WHITE, Color.WHITE, Color.TRANSPARENT),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        ).apply {
            setLocalMatrix(Matrix().apply {
                setScale(radiusX, radiusY)
                postTranslate(centerX, centerY)
            })
        }
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }

    fun draw(canvas: Canvas, drawArtwork: () -> Unit) {
        // Replacement, not an additive glow: a zero channel must hide the baked-in bright lamp
        // even when another channel is illuminating the aquarium. The soft mask retains detail.
        canvas.withHeroLayer(bounds, artworkPaint) {
            drawArtwork()
            canvas.drawRect(bounds, maskPaint)
        }
    }
}

private inline fun Canvas.withHeroLayer(bounds: RectF, paint: Paint, draw: () -> Unit) {
    val checkpoint = save()
    try {
        // saveLayer bounds alone are only a size hint. Clip explicitly to bound GPU overdraw.
        clipRect(bounds)
        saveLayer(bounds, paint)
        draw()
    } finally {
        restoreToCount(checkpoint)
    }
}
