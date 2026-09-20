package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.root

import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightChannelOutputSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceLightAquariumSceneTest {

    @Test
    fun mapsEffectiveRgbwChannelsToNormalizedVisualOutput() {
        val result = deviceLightAquariumOutput(
            channels = listOf(
                channel("red", 28),
                channel("green", 37),
                channel("blue", 57),
                channel("white", 80)
            ),
            outputActive = true
        )

        assertEquals(0.28f, result.red, 0.0001f)
        assertEquals(0.37f, result.green, 0.0001f)
        assertEquals(0.57f, result.blue, 0.0001f)
        assertEquals(0.80f, result.white, 0.0001f)
    }

    @Test
    fun explicitInactiveOutputForcesDarkScene() {
        val result = deviceLightAquariumOutput(
            channels = listOf(
                channel("red", 100),
                channel("green", 100),
                channel("blue", 100),
                channel("white", 100)
            ),
            outputActive = false
        )

        assertEquals(DeviceLightAquariumOutput(), result)
    }

    @Test
    fun clampsOutOfRangeValuesAndIgnoresUnknownChannels() {
        val result = deviceLightAquariumOutput(
            channels = listOf(
                channel("r", 140),
                channel("g", -20),
                channel("b", 50),
                channel("w", 65),
                channel("uv", 100)
            ),
            outputActive = null
        )

        assertEquals(1.00f, result.red, 0.0001f)
        assertEquals(0.00f, result.green, 0.0001f)
        assertEquals(0.50f, result.blue, 0.0001f)
        assertEquals(0.65f, result.white, 0.0001f)
    }

    private fun channel(
        key: String,
        percent: Int
    ) = DeviceLightChannelOutputSnapshot(
        key = key,
        displayName = key,
        displayColorRgb = 0,
        effectivePercent = percent
    )
}
