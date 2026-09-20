package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.root

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceLightHeroExposureTest {
    @Test
    fun `dark and full are exact stable endpoints`() {
        assertEquals(HERO_DARK_GAIN, heroDisplayGain(0f), 0f)
        assertEquals(1f, heroDisplayGain(1f), 0f)
        assertTrue(DeviceLightHeroIllumination.full.isFull)
        assertFalse(DeviceLightHeroIllumination.dark.isFull)
        assertEquals(DeviceLightHeroSceneGains(1f, 1f, 1f), DeviceLightHeroIllumination.full.sceneGains())
    }

    @Test
    fun `sunrise and sunset exposure are bounded and monotonic through all levels`() {
        var previous = heroDisplayGain(0f)
        for (step in 1..10_000) {
            val gain = heroDisplayGain(step / 10_000f)
            assertTrue(gain in HERO_DARK_GAIN..1f)
            assertTrue(gain >= previous)
            previous = gain
        }
    }

    @Test
    fun `invalid floating values never reach graphics filters`() {
        listOf(Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, -1f).forEach { invalid ->
            assertEquals(HERO_DARK_GAIN, heroDisplayGain(invalid), 0f)
        }
        assertEquals(1f, heroDisplayGain(2f), 0f)
    }

    @Test
    fun `RGB channels change their own scene components independently`() {
        val red = DeviceLightHeroIllumination(1f, 0f, 0f, 0f).sceneGains()
        val green = DeviceLightHeroIllumination(0f, 1f, 0f, 0f).sceneGains()
        val blue = DeviceLightHeroIllumination(0f, 0f, 1f, 0f).sceneGains()
        assertEquals(DeviceLightHeroSceneGains(heroDisplayGain(0.5f), HERO_DARK_GAIN, HERO_DARK_GAIN), red)
        assertEquals(DeviceLightHeroSceneGains(HERO_DARK_GAIN, heroDisplayGain(0.5f), HERO_DARK_GAIN), green)
        assertEquals(DeviceLightHeroSceneGains(HERO_DARK_GAIN, HERO_DARK_GAIN, heroDisplayGain(0.5f)), blue)
    }

    @Test
    fun `white is neutral and uniform output needs no spatial correction`() {
        val white = DeviceLightHeroIllumination(0f, 0f, 0f, 1f).sceneGains()
        assertEquals(white.red, white.green, 0f)
        assertEquals(white.green, white.blue, 0f)
        for (step in 0..100) {
            val level = step / 100f
            val output = DeviceLightHeroIllumination(level, level, level, level)
            assertTrue(output.isUniform)
            val gain = heroDisplayGain(level)
            assertEquals(DeviceLightHeroSceneGains(gain, gain, gain), output.sceneGains())
        }
    }

    @Test
    fun `artwork masks cover each emitter once and never overlap another emitter center`() {
        assertEquals(DeviceLightHeroEmitterChannel.entries.toSet(), deviceLightHeroEmitterRegions.map { it.channel }.toSet())
        assertEquals(4, deviceLightHeroEmitterRegions.size)
        deviceLightHeroEmitterRegions.forEach { region ->
            assertTrue(region.centerX - region.radiusX >= 0f)
            assertTrue(region.centerX + region.radiusX <= 1f)
            assertTrue(region.centerY - region.radiusY >= 0f)
            assertTrue(region.centerY + region.radiusY <= 1f)
            deviceLightHeroEmitterRegions.filter { it != region }.forEach { other ->
                val dx = (other.centerX - region.centerX) / region.radiusX
                val dy = (other.centerY - region.centerY) / region.radiusY
                assertTrue(dx * dx + dy * dy > 1f)
            }
        }
    }
}
