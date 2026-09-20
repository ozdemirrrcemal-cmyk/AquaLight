package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.root

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import androidx.test.platform.app.InstrumentationRegistry
import com.aqua.aqualight.R
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exercises the same Canvas renderer on real Android, without a mock graphics implementation. */
class DeviceLightHeroRelighterInstrumentedTest {
    @Test
    fun fullOutputIsPixelIdenticalToTheUnmodifiedArtwork() = withSource(realArtwork = true) { source ->
        assertArrayEquals(pixels(source), render(source, DeviceLightHeroIllumination.full))
    }

    @Test
    fun offRemovesBrightEmittersAndPreservesArtworkAlpha() = withSource(realArtwork = true) { source ->
        val original = pixels(source)
        val dark = render(source, DeviceLightHeroIllumination.dark)
        dark.indices.forEach { index ->
            assertEquals(Color.alpha(original[index]), Color.alpha(dark[index]))
            assertTrue(Color.red(dark[index]) <= 11)
            assertTrue(Color.green(dark[index]) <= 11)
            assertTrue(Color.blue(dark[index]) <= 11)
        }
    }

    @Test
    fun eachEmitterFollowsOnlyItsOwnEffectiveChannel() = withSource(realArtwork = false) { source ->
        DeviceLightHeroEmitterChannel.entries.forEach { active ->
            val output = DeviceLightHeroIllumination(
                red = if (active == DeviceLightHeroEmitterChannel.RED) 1f else 0f,
                green = if (active == DeviceLightHeroEmitterChannel.GREEN) 1f else 0f,
                blue = if (active == DeviceLightHeroEmitterChannel.BLUE) 1f else 0f,
                white = if (active == DeviceLightHeroEmitterChannel.WHITE) 1f else 0f
            )
            val rendered = render(source, output)
            deviceLightHeroEmitterRegions.forEach { emitter ->
                val pixel = rendered[(emitter.centerY * source.height).toInt() * source.width +
                    (emitter.centerX * source.width).toInt()]
                val expected = if (emitter.channel == active) 255 else 10
                assertEquals(expected.toDouble(), Color.red(pixel).toDouble(), 1.0)
                assertEquals(expected.toDouble(), Color.green(pixel).toDouble(), 1.0)
                assertEquals(expected.toDouble(), Color.blue(pixel).toDouble(), 1.0)
            }
        }
    }

    @Test
    fun sceneColorAndRepeatedFramesRemainStable() = withSource(realArtwork = false) { source ->
        val output = DeviceLightHeroIllumination(1f, 0f, 0f, 0f)
        val relighter = DeviceLightHeroRelighter(source.width.toFloat(), source.height.toFloat())
        val first = render(source, output, relighter)
        val second = render(source, output, relighter)
        assertArrayEquals(first, second)
        val tankPixel = first[(source.height * 0.75f).toInt() * source.width + source.width / 2]
        assertTrue(Color.red(tankPixel) > 180)
        assertTrue(Color.green(tankPixel) <= 11)
        assertTrue(Color.blue(tankPixel) <= 11)
    }

    private fun withSource(realArtwork: Boolean, check: (Bitmap) -> Unit) {
        val source = Bitmap.createBitmap(640, 264, Bitmap.Config.ARGB_8888)
        try {
            if (realArtwork) {
                val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
                val original = requireNotNull(
                    BitmapFactory.decodeResource(resources, R.drawable.device_light_hero_card)
                )
                try {
                    Canvas(source).drawBitmap(
                        original,
                        null,
                        Rect(0, 0, source.width, source.height),
                        Paint(Paint.FILTER_BITMAP_FLAG)
                    )
                } finally {
                    original.recycle()
                }
            } else {
                source.eraseColor(Color.WHITE)
            }
            check(source)
        } finally {
            source.recycle()
        }
    }

    private fun render(
        source: Bitmap,
        output: DeviceLightHeroIllumination,
        relighter: DeviceLightHeroRelighter = DeviceLightHeroRelighter(source.width.toFloat(), source.height.toFloat())
    ): IntArray {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(result)
            val paint = Paint()
            val saveCount = canvas.saveCount
            relighter.draw(canvas, output) { canvas.drawBitmap(source, 0f, 0f, paint) }
            assertEquals(saveCount, canvas.saveCount)
            return pixels(result)
        } finally {
            result.recycle()
        }
    }

    private fun pixels(bitmap: Bitmap): IntArray = IntArray(bitmap.width * bitmap.height).also { pixels ->
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }
}
