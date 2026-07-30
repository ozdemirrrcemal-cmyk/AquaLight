package com.aqua.aqualight.ui.tabs.devices.detail.common

import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootMenuFeature
import com.aqua.aqualight.application.devices.DeviceRootRoute
import com.aqua.aqualight.application.devices.DeviceRootRouteResolver
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceRootMenuMapperTest {

    @Test
    fun `unsupported light features are absent instead of disabled placeholders`() {
        val sections = DeviceRootMenuMapper.light(
            snapshot(
                family = OwnerDeviceFamily.LIGHT,
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
    fun `elite light uses exact light family fan and temperature routes`() {
        val sections = DeviceRootMenuMapper.light(
            snapshot(
                family = OwnerDeviceFamily.LIGHT,
                DeviceRootMenuFeature.LIGHT_MANUAL,
                DeviceRootMenuFeature.LIGHT_QUICK_SETUP,
                DeviceRootMenuFeature.LIGHT_PROGRAMS,
                DeviceRootMenuFeature.LIGHT_PRESETS,
                DeviceRootMenuFeature.COOLING_FANS,
                DeviceRootMenuFeature.COOLING_TEMPERATURE,
                DeviceRootMenuFeature.DEVICE_SETTINGS
            )
        )

        assertTrue(sections.secondary.any { it.route == DeviceRootRoute.LIGHT_FAN_CONTROL })
        assertTrue(
            sections.secondary.any {
                it.route == DeviceRootRoute.LIGHT_TEMPERATURE_PROTECTION
            }
        )
        assertFalse(sections.secondary.any { it.route == DeviceRootRoute.COOLING_CONTROL })
    }

    @Test
    fun `cooling family uses cooling destinations for the same menu concepts`() {
        val sections = DeviceRootMenuMapper.overview(
            DeviceRootKind.COOLING,
            snapshot(
                family = OwnerDeviceFamily.COOLING,
                DeviceRootMenuFeature.COOLING_FANS,
                DeviceRootMenuFeature.COOLING_TEMPERATURE,
                DeviceRootMenuFeature.DEVICE_SETTINGS
            )
        )

        assertEquals(DeviceRootRoute.COOLING_CONTROL, sections.primary.single().route)
        assertTrue(sections.secondary.any { it.route == DeviceRootRoute.COOLING_TEMPERATURE })
        assertFalse(sections.primary.any { it.route == DeviceRootRoute.LIGHT_FAN_CONTROL })
    }

    private fun snapshot(
        family: OwnerDeviceFamily,
        vararg menuFeatures: DeviceRootMenuFeature
    ): DeviceRootSnapshot {
        val features = menuFeatures.toSet()
        val routes = features.mapNotNullTo(linkedSetOf()) { feature ->
            DeviceRootRouteResolver.resolve(family, feature)
        }
        return DeviceRootSnapshot(
            deviceUid = "fixture-${family.name.lowercase()}",
            title = "Fixture ${family.name}",
            availability = OwnerDeviceAvailability.REACHABLE,
            family = family,
            catalogState = DeviceRootCatalogState.VALID,
            menuFeatures = features,
            allowedRoutes = routes
        )
    }
}
