package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.root

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeroSceneExposureTest {
    @Test
    fun `full WRGB output is an exact identity transform`() {
        assertEquals(HeroSceneExposure(1f, 1f, 1f, 0f), exposure(100, 100, 100, 100))
    }

    @Test
    fun `off keeps only a small neutral ambient silhouette`() {
        assertEquals(HeroSceneExposure(0f, 0f, 0f, 0.035f), exposure(0, 0, 0, 0))
    }

    @Test
    fun `entire sunrise and sunset are monotonic with no on threshold`() {
        var previous = exposure(0, 0, 0, 0)
        for (percent in 1..100) {
            val current = exposure(percent, percent, percent, percent)
            assertTrue(current.red > previous.red)
            assertEquals(current.red, current.green, 0f)
            assertEquals(current.red, current.blue, 0f)
            assertTrue(current.ambient <= previous.ambient)
            previous = current
        }
    }

    @Test
    fun `red-only and blue-only output do not illuminate the other color components`() {
        val red = exposure(100, 0, 0, 0)
        val blue = exposure(0, 0, 100, 0)
        assertTrue(red.red > 0f)
        assertEquals(0f, red.green, 0f)
        assertEquals(0f, red.blue, 0f)
        assertTrue(blue.blue > 0f)
        assertEquals(0f, blue.red, 0f)
        assertEquals(0f, blue.green, 0f)
    }

    @Test
    fun `white contributes equally without normalizing partial output to full`() {
        val white = exposure(0, 0, 0, 50)
        assertEquals(heroDisplayResponse(0.25f), white.red, 0f)
        assertEquals(white.red, white.green, 0f)
        assertEquals(white.red, white.blue, 0f)
        assertTrue(white.red < 1f)
    }

    @Test
    fun `normalization uses installed channels even when some are at zero`() {
        assertEquals(heroDisplayResponse(0.125f), exposure(25, 0, 0, 0).red, 0f)
        assertEquals(heroDisplayResponse(0.25f), exposure(50, 0, 0, 0).red, 0f)
    }

    @Test
    fun `display response rejects nonfinite values and preserves exact endpoints`() {
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -1f, 0f)) {
            assertEquals(0f, heroDisplayResponse(value), 0f)
        }
        assertEquals(1f, heroDisplayResponse(1f), 0f)
        assertEquals(1f, heroDisplayResponse(2f), 0f)
    }

    @Test
    fun `display response stays continuous and bounded through its low-light break`() {
        var previous = 0f
        for (step in 0..10000) {
            val current = heroDisplayResponse(step / 10000f)
            assertTrue(current in 0f..1f)
            assertTrue(current >= previous)
            previous = current
        }
    }

    private fun exposure(red: Int, green: Int, blue: Int, white: Int): HeroSceneExposure {
        val frame = resolveHeroLightFrame(wrgb(red, green, blue, white), true)
        return frame.channels.sceneExposure(frame.profile)
    }
}
