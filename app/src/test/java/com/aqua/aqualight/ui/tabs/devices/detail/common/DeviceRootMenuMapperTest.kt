package com.aqua.aqualight.ui.tabs.devices.detail.common

import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.DeviceRootMenuFeature
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceRootMenuMapperTest {

    @Test
    fun `unsupported light features are absent instead of disabled placeholders`() {
        val sections = DeviceRootMenuMapper.light(
            snapshot(
                DeviceRootMenuFeature.LIGHT_MANUAL,
                DeviceRootMenuFeature.LIGHT_QUICK_SETUP,
                DeviceRootMenuFeature.LIGHT_PROGRAMS,
                DeviceRootMenuFeature.LIGHT_PRESETS,
                DeviceRootMenuFeature.DEVICE_SETTINGS
            )
        )

        assertEquals(2, sections.primary.size)
        assertEquals(3, sections.secondary.size)
        assertFalse(
            sections.secondary.any { item ->
                item.titleRes == R.string.device_menu_fan_control_title ||
                    item.titleRes == R.string.device_menu_temperature_automation_title
            }
        )
    }

    @Test
    fun `elite light profile adds fan and temperature entries to one family menu`() {
        val sections = DeviceRootMenuMapper.light(
            snapshot(
                DeviceRootMenuFeature.LIGHT_MANUAL,
                DeviceRootMenuFeature.LIGHT_QUICK_SETUP,
                DeviceRootMenuFeature.LIGHT_PROGRAMS,
                DeviceRootMenuFeature.LIGHT_PRESETS,
                DeviceRootMenuFeature.COOLING_FANS,
                DeviceRootMenuFeature.COOLING_TEMPERATURE,
                DeviceRootMenuFeature.DEVICE_SETTINGS
            )
        )

        assertTrue(
            sections.secondary.any { item -> item.titleRes == R.string.device_menu_fan_control_title }
        )
        assertTrue(
            sections.secondary.any { item ->
                item.titleRes == R.string.device_menu_temperature_automation_title
            }
        )
    }

    private fun snapshot(
        vararg menuFeatures: DeviceRootMenuFeature
    ): DeviceRootSnapshot {
        return DeviceRootSnapshot(
            deviceUid = "fixture-light",
            title = "Fixture Light",
            availability = OwnerDeviceAvailability.REACHABLE,
            menuFeatures = menuFeatures.toSet()
        )
    }
}
