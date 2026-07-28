package com.aqua.aqualight.data.devices

import com.aqua.aqualight.application.devices.DeviceRootMenuFeature
import com.aqua.aqualight.data.devices.contract.AqlCatalogKeySet
import com.aqua.aqualight.data.devices.contract.AqlDeviceFeatureKey
import com.aqua.aqualight.data.devices.contract.AqlDeviceScreenKey
import com.aqua.aqualight.data.devices.contract.parseAqlDeviceFeatureKeysExact
import com.aqua.aqualight.data.devices.model.DeviceCapabilities
import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceIdentity
import com.aqua.aqualight.data.devices.model.DeviceProduct
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import org.junit.Assert.assertEquals
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
    fun `light menus resolve only from exact light contract`() {
        val snapshot = snapshot(
            family = DeviceFamily.LIGHT,
            capabilities = DeviceCapabilities(
                light = true,
                manualLight = true,
                lightProgram = true,
                lightPresets = true,
                fan = true,
                cooling = true,
                temperature = true,
                ota = true
            ),
            features = listOf(
                "LIGHT_CONTROL",
                "LIGHT_QUICK_SETUP",
                "LIGHT_PRESETS",
                "COOLING_CONTROL",
                "TEMPERATURE_READ",
                "OTA_UPDATE"
            ),
            screens = listOf(
                "LIGHT_CONTROL",
                "LIGHT_QUICK_SETUP",
                "LIGHT_SCHEDULE",
                "LIGHT_PRESETS",
                "COOLING_CONTROL",
                "COOLING_FANS",
                "COOLING_SENSOR_STATUS",
                "ADVANCED"
            )
        )

        assertEquals(
            setOf(
                DeviceRootMenuFeature.LIGHT_MANUAL,
                DeviceRootMenuFeature.LIGHT_QUICK_SETUP,
                DeviceRootMenuFeature.LIGHT_PROGRAMS,
                DeviceRootMenuFeature.LIGHT_PRESETS,
                DeviceRootMenuFeature.COOLING_FANS,
                DeviceRootMenuFeature.COOLING_TEMPERATURE,
                DeviceRootMenuFeature.DEVICE_SETTINGS
            ),
            DeviceRootMenuFeatureResolver.resolve(snapshot)
        )
    }

    @Test
    fun `dosing never unlocks timer menus even when timer keys are injected`() {
        val snapshot = snapshot(
            family = DeviceFamily.DOSING,
            capabilities = DeviceCapabilities(
                standaloneTimer = false,
                dosing = true,
                ota = true
            ),
            features = listOf(
                "DOSING_CONTROL",
                "DOSING_CALIBRATION",
                "TIMER_CONTROL",
                "OTA_UPDATE"
            ),
            screens = listOf(
                "DOSING_CHANNELS",
                "DOSING_CALIBRATION",
                "DOSING_SCHEDULES",
                "TIMER_CHANNELS",
                "TIMER_SCHEDULES",
                "ADVANCED"
            )
        )

        val resolved = DeviceRootMenuFeatureResolver.resolve(snapshot)
        assertEquals(
            setOf(
                DeviceRootMenuFeature.DOSING_CHANNELS,
                DeviceRootMenuFeature.DOSING_CALIBRATION,
                DeviceRootMenuFeature.DOSING_SCHEDULES,
                DeviceRootMenuFeature.DEVICE_SETTINGS
            ),
            resolved
        )
        assertTrue(DeviceRootMenuFeature.TIMER_CHANNELS !in resolved)
        assertTrue(DeviceRootMenuFeature.TIMER_SCHEDULES !in resolved)
    }

    @Test
    fun `unknown metadata and unknown family fail closed`() {
        val unknownKeySnapshot = snapshot(
            family = DeviceFamily.LIGHT,
            capabilities = DeviceCapabilities(manualLight = true),
            features = listOf("LIGHT_CONTROL", "LIGHT_CONTROL_V2"),
            screens = listOf("LIGHT_CONTROL", "ADVANCED")
        )
        assertTrue(DeviceRootMenuFeatureResolver.resolve(unknownKeySnapshot).isEmpty())

        val unknownFamilySnapshot = snapshot(
            family = DeviceFamily.UNKNOWN,
            capabilities = DeviceCapabilities(
                manualLight = true,
                standaloneTimer = true,
                dosing = true,
                cooling = true,
                fan = true,
                temperature = true,
                ota = true
            ),
            features = AqlDeviceFeatureKey.entries.map(AqlDeviceFeatureKey::wireValue),
            screens = AqlDeviceScreenKey.entries.map(AqlDeviceScreenKey::wireValue)
        )
        assertTrue(DeviceRootMenuFeatureResolver.resolve(unknownFamilySnapshot).isEmpty())
    }

    @Test
    fun `settings route and OTA capability remain separate`() {
        val otaWithoutSettings = snapshot(
            family = DeviceFamily.TIMER,
            capabilities = DeviceCapabilities(standaloneTimer = true, ota = true),
            features = listOf("TIMER_CONTROL", "OTA_UPDATE"),
            screens = listOf("TIMER_CHANNELS", "TIMER_SCHEDULES")
        )
        assertTrue(
            DeviceRootMenuFeature.DEVICE_SETTINGS !in
                DeviceRootMenuFeatureResolver.resolve(otaWithoutSettings)
        )

        val settingsWithoutOta = snapshot(
            family = DeviceFamily.TIMER,
            capabilities = DeviceCapabilities(standaloneTimer = true, ota = false),
            features = listOf("TIMER_CONTROL"),
            screens = listOf("TIMER_CHANNELS", "TIMER_SCHEDULES", "ADVANCED")
        )
        assertTrue(
            DeviceRootMenuFeature.DEVICE_SETTINGS in
                DeviceRootMenuFeatureResolver.resolve(settingsWithoutOta)
        )
    }

    private fun snapshot(
        family: DeviceFamily,
        capabilities: DeviceCapabilities,
        features: List<String>,
        screens: List<String>
    ): DeviceSnapshot = DeviceSnapshot(
        identity = DeviceIdentity(uid = DeviceUid("fixture-device")),
        product = DeviceProduct(family = family),
        capabilities = capabilities,
        supportedFeatures = features,
        supportedScreens = screens
    )
}
