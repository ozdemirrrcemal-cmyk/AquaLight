package com.aqua.aqualight.data.devices.runtime.modules.firmware

import com.aqua.aqualight.data.devices.catalog.AqlCommercialCatalogProduct
import com.aqua.aqualight.data.devices.catalog.AqlCommercialDeviceCatalog
import com.aqua.aqualight.data.devices.model.DeviceCapabilities
import com.aqua.aqualight.data.devices.model.DeviceIdentity
import com.aqua.aqualight.data.devices.model.DeviceLimits
import com.aqua.aqualight.data.devices.model.DeviceProduct
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PublishedDeviceFirmwareManifestIntegrationTest {

    @Test
    fun `published v1 manifest parses and display name does not participate in OTA identity`() {
        val resource = checkNotNull(
            javaClass.classLoader?.getResource("firmware/manifest-stable-v1.0.2.json")
        )
        val manifest = DeviceFirmwareManifestParser.parse(resource.readText()).getOrThrow()
        val artifact = manifest.artifacts.single()

        assertEquals("1.0.2", manifest.version)
        assertEquals("AquaLight WRGB Pro Elite 120", artifact.product.displayName)
        assertEquals("WRGB Pro Elite 120", product().displayName)
        assertEquals("esp32-app-bin", artifact.firmware.format)
        assertEquals("tr", manifest.releaseNotes.defaultLocale)

        val availability = DeviceFirmwareUpdatePlanner { listOf("tr-TR", "tr", "en") }
            .evaluateUpdate(product().toSnapshot(), manifest)
            .getOrThrow()

        assertTrue(availability is DeviceFirmwareAvailability.UpdateAvailable)
        val plan = (availability as DeviceFirmwareAvailability.UpdateAvailable).plan
        assertEquals("1.0.2", plan.targetVersion)
        assertEquals("light_wrgb_pro_elite", plan.env)
        assertEquals(
            listOf("OTA manifest sözleşmesi üretici çıktısıyla uyumlu hale getirildi."),
            plan.releaseContent.changes
        )
    }

    private fun product(): AqlCommercialCatalogProduct =
        AqlCommercialDeviceCatalog.products.single { product ->
            product.productKey.value == PRODUCT_KEY
        }

    private fun AqlCommercialCatalogProduct.toSnapshot(): DeviceSnapshot = DeviceSnapshot(
        identity = DeviceIdentity(
            uid = DEVICE_UID,
            customName = "Salon Aydınlatma"
        ),
        product = DeviceProduct(
            brand = "AquaLight",
            productId = productId.value,
            productKey = productKey.value,
            family = family,
            familyRaw = family.wireValue,
            line = line.value,
            model = model.value,
            displayName = displayName,
            skuId = skuId.value,
            skuCode = skuCode.value,
            hardwareRevision = hardwareRevision.value
        ),
        firmwareVersion = "1.0.1",
        apiVersion = "1",
        protocolVersion = "1",
        capabilities = DeviceCapabilities(
            light = profile.capabilities.light,
            manualLight = profile.capabilities.manualLight,
            lightProgram = profile.capabilities.lightProgram,
            lightPresets = profile.capabilities.lightPresets,
            lightSimulation = profile.capabilities.lightSimulation,
            fan = profile.capabilities.fan,
            cooling = profile.capabilities.cooling,
            temperature = profile.capabilities.temperature,
            standaloneTimer = profile.capabilities.standaloneTimer,
            dosing = profile.capabilities.dosing,
            timeSync = profile.capabilities.timeSync,
            ota = profile.capabilities.ota
        ),
        limits = DeviceLimits(
            lightChannelCount = limits.lightChannelCount,
            fanOutputCount = limits.fanOutputCount,
            temperatureSensorCount = limits.temperatureSensorCount,
            timerChannelCount = limits.timerChannelCount,
            dosingChannelCount = limits.dosingChannelCount
        ),
        supportedFeatures = profile.supportedFeatures.map { feature -> feature.wireValue },
        supportedScreens = profile.supportedScreens.map { screen -> screen.wireValue },
        runtimeMetadataGeneration = 7L
    )

    private companion object {
        const val PRODUCT_KEY = "LIGHT_WRGB_PRO_ELITE"
        val DEVICE_UID = DeviceUid("AQL-WPE120-MANIFEST-TEST")
    }
}
