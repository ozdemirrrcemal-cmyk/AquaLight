package com.aqua.aqualight.data.aquarium.devices

import com.aqua.aqualight.data.devices.catalog.AqlCommercialCatalogProduct
import com.aqua.aqualight.data.devices.catalog.AqlCommercialDeviceCatalog
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceIdentity
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.model.DeviceProduct
import com.aqua.aqualight.data.devices.model.DeviceCapabilities
import com.aqua.aqualight.data.devices.model.DeviceLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TankControlGroupDeviceMappingTest {
    @Test fun `both 120 cm products get distinct validated keys despite identical custom names`() {
        val elite = product("LIGHT_WRGB_PRO_ELITE").toSnapshot()
        val slim = product("LIGHT_RGB_PRO_SLIM").toSnapshot()
        val first = elite.copy(identity = elite.identity.copy(customName = "Tank light")).toControlGroupDevice()!!
        val second = slim.copy(identity = slim.identity.copy(customName = "Tank light")).toControlGroupDevice()!!
        assertEquals("Tank light", first.device.displayName)
        assertEquals("wrgb_pro_elite_120", first.compatibility!!.model)
        assertEquals("rgb_pro_slim_120", second.compatibility!!.model)
        assertEquals(4, first.compatibility!!.channelCount)
        assertEquals(3, second.compatibility!!.channelCount)
        assertNotEquals(first.compatibility, second.compatibility)
    }

    @Test fun `persisted metadata and malformed catalog identities cannot be selected`() {
        val snapshot = product("LIGHT_WRGB_PRO_ELITE").toSnapshot()
        assertNotNull(snapshot.toControlGroupDevice()!!.compatibility)
        assertNull(snapshot.copy(runtimeMetadataGeneration = 0L).toControlGroupDevice()!!.compatibility)
        assertNull(snapshot.copy(product = snapshot.product.copy(skuCode = "unknown"))
            .toControlGroupDevice()!!.compatibility)
    }

    @Test fun `non light families never enter candidate list`() {
        AqlCommercialDeviceCatalog.products.filterNot { it.productKey.value.startsWith("LIGHT_") }.forEach {
            assertNull(it.toSnapshot().toControlGroupDevice())
        }
    }

    private fun product(key: String) = AqlCommercialDeviceCatalog.products.single { it.productKey.value == key }

    private fun AqlCommercialCatalogProduct.toSnapshot(): DeviceSnapshot = DeviceSnapshot(
        identity = DeviceIdentity(
            uid = DeviceUid("catalog-${model.value}"),
            customName = "Fixture $displayName"
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
        firmwareVersion = "6.0.0",
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
        supportedFeatures = profile.supportedFeatures.map { it.wireValue },
        supportedScreens = profile.supportedScreens.map { it.wireValue },
        runtimeMetadataGeneration = 1L
    )
}
