package com.aqua.aqualight.data.devices

import com.aqua.aqualight.application.devices.DeviceRootMenuFeature
import com.aqua.aqualight.application.devices.DeviceRootRoute
import com.aqua.aqualight.data.devices.catalog.AqlCommercialDeviceCatalog
import com.aqua.aqualight.data.devices.contract.AqlCatalogKeySet
import com.aqua.aqualight.data.devices.contract.AqlDeviceFeatureKey
import com.aqua.aqualight.data.devices.contract.AqlDeviceScreenKey
import com.aqua.aqualight.data.devices.contract.parseAqlDeviceFeatureKeysExact
import com.aqua.aqualight.data.devices.model.DeviceFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AqlDeviceCatalogFoundationTest {

    @Test
    fun `wire keys are exact and permissive aliases are rejected`() {
        assertEquals(
            AqlDeviceFeatureKey.LIGHT_QUICK_SETUP,
            AqlDeviceFeatureKey.fromWireExact("LIGHT_QUICK_SETUP")
        )
        assertEquals(
            AqlDeviceScreenKey.DOSING_SCHEDULES,
            AqlDeviceScreenKey.fromWireExact("DOSING_SCHEDULES")
        )

        assertNull(AqlDeviceFeatureKey.fromWireExact("light_quick_setup"))
        assertNull(AqlDeviceFeatureKey.fromWireExact(" LIGHT_QUICK_SETUP"))
        assertNull(AqlDeviceFeatureKey.fromWireExact("LIGHT_QUICK_SETUP "))
        assertNull(AqlDeviceScreenKey.fromWireExact("channels"))
        assertNull(AqlDeviceScreenKey.fromWireExact("settings"))

        val parsed = listOf("LIGHT_CONTROL", "legacy.manual").parseAqlDeviceFeatureKeysExact()
        assertTrue(parsed is AqlCatalogKeySet.Invalid)
        assertEquals(
            setOf("legacy.manual"),
            (parsed as AqlCatalogKeySet.Invalid).unknownWireValues
        )
    }

    @Test
    fun `all generated products resolve the fixture declared family menus`() {
        assertEquals(9, AqlCommercialDeviceCatalog.products.size)
        AqlCommercialDeviceCatalog.products.forEach { product ->
            assertEquals(
                product.profile.expectedMenuFeatureNames,
                DeviceRootMenuFeatureResolver.resolve(product).mapTo(linkedSetOf()) { it.name }
            )
        }
    }

    @Test
    fun `light models share one family resolver but expose distinct profile menus`() {
        val elite = product("LIGHT_WRGB_PRO_ELITE")
        val slim = product("LIGHT_RGB_PRO_SLIM")

        val eliteMenus = DeviceRootMenuFeatureResolver.resolve(elite)
        val slimMenus = DeviceRootMenuFeatureResolver.resolve(slim)

        assertTrue(DeviceRootMenuFeature.COOLING_FANS in eliteMenus)
        assertTrue(DeviceRootMenuFeature.COOLING_TEMPERATURE in eliteMenus)
        assertFalse(DeviceRootMenuFeature.COOLING_FANS in slimMenus)
        assertFalse(DeviceRootMenuFeature.COOLING_TEMPERATURE in slimMenus)
        assertTrue(DeviceRootMenuFeature.LIGHT_MANUAL in eliteMenus)
        assertTrue(DeviceRootMenuFeature.LIGHT_MANUAL in slimMenus)
    }

    @Test
    fun `wrgb and cool pro retain distinct family destinations over shared cooling api`() {
        val eliteRoutes = DeviceRootRoutePolicy.allowedRoutes(product("LIGHT_WRGB_PRO_ELITE"))
        val coolRoutes = DeviceRootRoutePolicy.allowedRoutes(product("COOLING_COOL_PRO_2F"))

        assertTrue(DeviceRootRoute.LIGHT_FAN_CONTROL in eliteRoutes)
        assertTrue(DeviceRootRoute.LIGHT_TEMPERATURE_PROTECTION in eliteRoutes)
        assertFalse(DeviceRootRoute.COOLING_CONTROL in eliteRoutes)

        assertTrue(DeviceRootRoute.COOLING_CONTROL in coolRoutes)
        assertTrue(DeviceRootRoute.COOLING_TEMPERATURE in coolRoutes)
        assertFalse(DeviceRootRoute.LIGHT_FAN_CONTROL in coolRoutes)
    }

    @Test
    fun `dosing family never authorizes standalone timer routes`() {
        val dosing = product("DOSING_DOSE_PRO_4")
        val routes = DeviceRootRoutePolicy.allowedRoutes(dosing)

        assertEquals(DeviceFamily.DOSING, dosing.family)
        assertTrue(DeviceRootRoute.DOSING_CHANNELS in routes)
        assertFalse(DeviceRootRoute.TIMER_CHANNELS in routes)
        assertFalse(DeviceRootRoute.TIMER_SCHEDULES in routes)
        assertFalse(DeviceRootRoutePolicy.authorize(dosing, DeviceRootRoute.TIMER_CHANNELS))
    }

    @Test
    fun `settings route is independent from OTA capability`() {
        val timer = product("TIMER_RELAY_PRO_2")
        val withoutOta = timer.copy(
            profile = timer.profile.copy(
                capabilities = timer.profile.capabilities.copy(ota = false)
            )
        )

        assertTrue(
            DeviceRootMenuFeature.DEVICE_SETTINGS in
                DeviceRootMenuFeatureResolver.resolve(withoutOta)
        )
        assertTrue(DeviceRootRoutePolicy.authorize(withoutOta, DeviceRootRoute.DEVICE_SETTINGS))
    }

    private fun product(productKey: String) = AqlCommercialDeviceCatalog.products.single {
        product -> product.productKey.value == productKey
    }
}
