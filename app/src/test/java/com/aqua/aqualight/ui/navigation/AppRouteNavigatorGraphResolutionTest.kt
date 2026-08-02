package com.aqua.aqualight.ui.navigation

import com.aqua.aqualight.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppRouteNavigatorGraphResolutionTest {

    @Test
    fun `resolves devices Safe Args graph from nested app hierarchy`() {
        assertEquals(
            DeviceFirmwareUpdateGraph.DEVICES,
            resolveDeviceFirmwareUpdateGraph(
                setOf(
                    R.id.deviceLightSettingsFragment,
                    R.id.nav_devices,
                    R.id.nav_app
                )
            )
        )
    }

    @Test
    fun `resolves aquarium Safe Args graph from nested app hierarchy`() {
        assertEquals(
            DeviceFirmwareUpdateGraph.AQUARIUM,
            resolveDeviceFirmwareUpdateGraph(
                setOf(
                    R.id.deviceLightSettingsFragment,
                    R.id.nav_aquarium,
                    R.id.nav_app
                )
            )
        )
    }

    @Test
    fun `rejects hierarchy outside supported OTA graphs`() {
        assertNull(
            resolveDeviceFirmwareUpdateGraph(
                setOf(R.id.settingsFragment, R.id.nav_settings, R.id.nav_app)
            )
        )
    }
}
