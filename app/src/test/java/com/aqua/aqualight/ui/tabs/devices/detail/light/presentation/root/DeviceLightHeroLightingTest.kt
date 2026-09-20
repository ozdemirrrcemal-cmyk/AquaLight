package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.root

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DeviceLightHeroLightingTest {
    @Test
    fun `effective percentages are preserved without using power or requested output`() {
        val frame = resolveHeroLightFrame(wrgb(60, 53, 61, 62), true)
        assertEquals(HeroLightChannels(0.60f, 0.53f, 0.61f, 0.62f), frame.channels)
    }

    @Test
    fun `off and unknown output never light up a stale positive scene`() {
        for (active in listOf(false, null)) {
            assertEquals(HeroLightChannels(), resolveHeroLightFrame(wrgb(100, 100, 100, 100), active).channels)
        }
    }

    @Test
    fun `cold start with no telemetry is dark`() {
        assertEquals(HeroLightChannels(), resolveHeroLightFrame(emptyMap(), null).channels)
    }

    @Test
    fun `zero effective scene stays dark even when active metadata is true`() {
        assertEquals(HeroLightChannels(), resolveHeroLightFrame(wrgb(0, 0, 0, 0), true).channels)
    }

    @Test
    fun `mapping follows wire keys not channel order`() {
        val ordered = wrgb(60, 53, 61, 62)
        val reversed = ordered.entries.reversed().associate { it.key to it.value }
        assertEquals(resolveHeroLightFrame(ordered, true), resolveHeroLightFrame(reversed, true))
    }

    @Test
    fun `missing white remains absent rather than borrowing another channel`() {
        val frame = resolveHeroLightFrame(mapOf("blue" to 100, "red" to 100, "green" to 100), true)
        assertFalse(frame.profile.white)
        assertEquals(0f, frame.channels.white, 0f)
        assertEquals(HeroSceneExposure(1f, 1f, 1f, 0f), frame.channels.sceneExposure(frame.profile))
    }

    @Test
    fun `effective output is clamped defensively`() {
        val frame = resolveHeroLightFrame(wrgb(-20, 150, Int.MAX_VALUE, Int.MIN_VALUE), true)
        assertEquals(HeroLightChannels(0f, 1f, 1f, 0f), frame.channels)
    }

    @Test
    fun `unrecognized keys cannot impersonate a physical WRGB channel`() {
        val frame = resolveHeroLightFrame(mapOf("Red" to 100, "redPercent" to 100), true)
        assertEquals(HeroLightChannels(), frame.channels)
        assertEquals(HeroSceneExposure(0f, 0f, 0f, 0.035f), frame.channels.sceneExposure(frame.profile))
    }

    @Test
    fun `white-only product has neutral exposure and no fictional RGB emitters`() {
        val frame = resolveHeroLightFrame(mapOf("white" to 100), true)
        assertEquals(HeroLightChannels(white = 1f), frame.channels)
        assertEquals(HeroSceneExposure(1f, 1f, 1f, 0f), frame.channels.sceneExposure(frame.profile))
    }
}

internal fun wrgb(red: Int, green: Int, blue: Int, white: Int): Map<String, Int> = linkedMapOf(
    "red" to red,
    "green" to green,
    "blue" to blue,
    "white" to white
)
