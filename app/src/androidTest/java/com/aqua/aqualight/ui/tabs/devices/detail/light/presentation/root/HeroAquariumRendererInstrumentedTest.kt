package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.root

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aqua.aqualight.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Uses the real hero resource and the production renderer, with no replacement artwork. */
@RunWith(AndroidJUnit4::class)
class HeroAquariumRendererInstrumentedTest {
    private val size = Size(640f, 266f)
    private val profile = HeroLightProfile(red = true, green = true, blue = true, white = true)
    private val artwork by lazy {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        BitmapPainter(checkNotNull(BitmapFactory.decodeResource(context.resources, R.drawable.device_light_hero_card))
            .asImageBitmap())
    }

    @Test
    fun fullOutputIsPixelIdenticalToTheOriginalArtwork() {
        val baseline = bitmap { with(artwork) { draw(size = this@HeroAquariumRendererInstrumentedTest.size) } }
        val actual = render(HeroLightChannels(1f, 1f, 1f, 1f))
        assertTrue("Full output must not change a single source pixel", baseline.sameAs(actual))
    }

    @Test
    fun offOutputRemovesBakedInLedHighlights() {
        val off = render(HeroLightChannels())
        for (center in listOf(0.519f, 0.602f, 0.690f, 0.782f)) {
            assertTrue("An off LED must not retain a luminous hotspot", sample(off, center, 0.34f) < 12)
        }
    }

    @Test
    fun redOnlyDoesNotLeaveGreenBlueOrWhiteEmittersOn() {
        val full = render(HeroLightChannels(1f, 1f, 1f, 1f))
        val red = render(HeroLightChannels(red = 1f))
        assertTrue(sample(red, 0.519f, 0.34f) > sample(full, 0.519f, 0.34f) * 0.9f)
        for (center in listOf(0.602f, 0.690f, 0.782f)) {
            assertTrue(sample(red, center, 0.34f) < sample(full, center, 0.34f) * 0.15f)
        }
    }

    @Test
    fun tankBrightensProgressivelyWithoutChangingBounds() {
        val low = render(HeroLightChannels(0.1f, 0.1f, 0.1f, 0.1f))
        val high = render(HeroLightChannels(0.8f, 0.8f, 0.8f, 0.8f))
        assertEquals(low.width, high.width)
        assertEquals(low.height, high.height)
        assertTrue(sample(low, 0.6f, 0.7f) < sample(high, 0.6f, 0.7f))
    }

    @Test
    fun reusedPainterAndRendererDoNotRetainThePreviousLitFrame() {
        val renderer = HeroAquariumRenderer(size)
        bitmap { renderer.draw(this, artwork, HeroLightChannels(1f, 1f, 1f, 1f), profile) }
        val off = bitmap { renderer.draw(this, artwork, HeroLightChannels(), profile) }
        assertTrue(off.sameAs(render(HeroLightChannels())))
    }

    private fun render(channels: HeroLightChannels): Bitmap = bitmap {
        HeroAquariumRenderer(this@HeroAquariumRendererInstrumentedTest.size).draw(this, artwork, channels, profile)
    }

    private fun bitmap(draw: DrawScope.() -> Unit): Bitmap {
        val bitmap = Bitmap.createBitmap(size.width.toInt(), size.height.toInt(), Bitmap.Config.ARGB_8888)
        CanvasDrawScope().draw(
            density = Density(1f),
            layoutDirection = LayoutDirection.Ltr,
            canvas = Canvas(bitmap.asImageBitmap()),
            size = size,
            block = draw
        )
        return bitmap
    }

    private fun sample(bitmap: Bitmap, x: Float, y: Float): Int {
        val pixel = bitmap.getPixel((bitmap.width * x).toInt(), (bitmap.height * y).toInt())
        return android.graphics.Color.red(pixel) +
            android.graphics.Color.green(pixel) + android.graphics.Color.blue(pixel)
    }
}
