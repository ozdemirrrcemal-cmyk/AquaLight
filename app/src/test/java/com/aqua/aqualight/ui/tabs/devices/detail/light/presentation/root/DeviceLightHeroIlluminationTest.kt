package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.root

import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightChannelOutputSnapshot
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlMode
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightHeroSnapshot
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightOutputCondition
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceLightHeroIlluminationTest {
    @Test
    fun `every mode uses effective channels rather than power or calibration`() {
        val channels = listOf(channel("red", 60), channel("green", 53), channel("blue", 61), channel("white", 62))
        DeviceLightControlMode.entries.forEach { mode ->
            val hero = DeviceLightHeroSnapshot(mode = mode, outputActive = true)
            val expected = DeviceLightHeroIllumination(0.60f, 0.53f, 0.61f, 0.62f)
            assertEquals(expected, hero.toHeroIllumination(channels))
            assertEquals(expected, hero.copy(estimatedPowerWatts = 200.0, estimatedColorTemperatureKelvin = 9000)
                .toHeroIllumination(channels))
        }
    }

    @Test
    fun `wire keys not list position or translated labels identify emitters`() {
        val channels = listOf(channel("white", 80), channel("blue", 30), channel("red", 10), channel("green", 20))
        assertEquals(
            DeviceLightHeroIllumination(0.1f, 0.2f, 0.3f, 0.8f),
            DeviceLightHeroSnapshot(outputActive = true).toHeroIllumination(channels)
        )
    }

    @Test
    fun `off and unavailable cannot show retained nonzero channel values as light`() {
        val channels = listOf(channel("red", 100), channel("white", 100))
        listOf(false, null).forEach { active ->
            assertEquals(
                DeviceLightHeroIllumination.dark,
                DeviceLightHeroSnapshot(outputActive = active).toHeroIllumination(channels)
            )
        }
    }

    @Test
    fun `empty missing and unknown channels do not invent illumination`() {
        val hero = DeviceLightHeroSnapshot(outputActive = true)
        assertEquals(DeviceLightHeroIllumination.dark, hero.toHeroIllumination(emptyList()))
        assertEquals(DeviceLightHeroIllumination.dark, hero.toHeroIllumination(listOf(channel("uv", 100))))
        assertEquals(
            DeviceLightHeroIllumination(0f, 0f, 0f, 0.5f),
            hero.toHeroIllumination(listOf(channel("white", 50)))
        )
    }

    @Test
    fun `an ambiguous duplicated key cannot light its emitter`() {
        val channels = listOf(channel("red", 100), channel("red", 25), channel("blue", 50))
        assertEquals(
            DeviceLightHeroIllumination(0f, 0f, 0.5f, 0f),
            DeviceLightHeroSnapshot(outputActive = true).toHeroIllumination(channels)
        )
    }

    @Test
    fun `out of range values are bounded and zero is exactly dark`() {
        val hero = DeviceLightHeroSnapshot(outputActive = true)
        assertEquals(
            DeviceLightHeroIllumination(0f, 1f, 0f, 0f),
            hero.toHeroIllumination(listOf(channel("red", -1), channel("green", 101)))
        )
        assertEquals(DeviceLightHeroIllumination.dark, hero.toHeroIllumination(listOf(channel("red", 0))))
    }

    @Test
    fun `power limitation uses effective output without treating unhealthy as off`() {
        val hero = DeviceLightHeroSnapshot(
            outputActive = true,
            outputCondition = DeviceLightOutputCondition.POWER_LIMITED,
            outputHealthy = false
        )
        val channels = listOf(channel("red", 20))
        assertEquals(DeviceLightHeroIllumination(0.2f, 0f, 0f, 0f), hero.toHeroIllumination(channels))
        assertEquals(
            DeviceLightHeroIllumination.dark,
            hero.copy(outputActive = false, outputCondition = DeviceLightOutputCondition.THERMAL_PROTECTION)
                .toHeroIllumination(channels)
        )
    }

    private fun channel(key: String, percent: Int) = DeviceLightChannelOutputSnapshot(
        key = key,
        displayName = "Translated label",
        displayColorRgb = 0,
        effectivePercent = percent
    )
}
