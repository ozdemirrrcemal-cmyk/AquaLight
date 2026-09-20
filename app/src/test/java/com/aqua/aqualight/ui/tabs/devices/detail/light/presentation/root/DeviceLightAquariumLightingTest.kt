package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.root

import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightChannelOutputSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceLightAquariumLightingTest {

    @Test
    fun projectsAuthoritativeRgbwChannels() {
        val lighting = resolveDeviceLightAquariumLighting(
            channels = listOf(
                channel("red", 28),
                channel("green", 37),
                channel("blue", 57),
                channel("white", 80)
            ),
            outputActive = true
        )

        assertEquals(0.28f, lighting.red, DELTA)
        assertEquals(0.37f, lighting.green, DELTA)
        assertEquals(0.57f, lighting.blue, DELTA)
        assertEquals(0.80f, lighting.white, DELTA)
        assertTrue(lighting.intensity > 0.70f)
    }

    @Test
    fun forcesDarkSceneWhenFirmwareOutputIsOff() {
        val lighting = resolveDeviceLightAquariumLighting(
            channels = listOf(
                channel("red", 100),
                channel("green", 100),
                channel("blue", 100),
                channel("white", 100)
            ),
            outputActive = false
        )

        assertEquals(DeviceLightAquariumLighting.Off, lighting)
    }

    @Test
    fun zeroChannelsProduceDarkScene() {
        val lighting = resolveDeviceLightAquariumLighting(
            channels = listOf(
                channel("red", 0),
                channel("green", 0),
                channel("blue", 0),
                channel("white", 0)
            ),
            outputActive = true
        )

        assertEquals(0f, lighting.intensity, DELTA)
    }

    @Test
    fun clampsUnexpectedPercentagesAtPresentationBoundary() {
        val lighting = resolveDeviceLightAquariumLighting(
            channels = listOf(
                channel("red", 140),
                channel("green", -10),
                channel("blue", 50),
                channel("white", 0)
            ),
            outputActive = true
        )

        assertEquals(1f, lighting.red, DELTA)
        assertEquals(0f, lighting.green, DELTA)
        assertEquals(0.5f, lighting.blue, DELTA)
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

    private companion object {
        const val DELTA = 0.0001f
    }
}
