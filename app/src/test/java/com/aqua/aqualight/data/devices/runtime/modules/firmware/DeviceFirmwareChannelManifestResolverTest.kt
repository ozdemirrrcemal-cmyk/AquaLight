package com.aqua.aqualight.data.devices.runtime.modules.firmware

import com.aqua.aqualight.application.devices.DeviceFirmwareChannel
import com.aqua.aqualight.data.devices.model.DeviceCapabilities
import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceIdentity
import com.aqua.aqualight.data.devices.model.DeviceLimits
import com.aqua.aqualight.data.devices.model.DeviceProduct
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DeviceFirmwareChannelManifestResolverTest {

    private val resolver = DeviceFirmwareChannelManifestResolver()
    private val wrgbProduct = ProductFixture(
        productKey = "LIGHT_WRGB_PRO_ELITE",
        productId = "com.aqualight.light.wrgb_pro_elite",
        family = DeviceFamily.LIGHT,
        line = "wrgb_pro_elite",
        model = "wrgb_pro_elite_120"
    )
    private val dosePro4Product = ProductFixture(
        productKey = "DOSING_DOSE_PRO_4",
        productId = "com.aqualight.dosing.dose_pro_4",
        family = DeviceFamily.DOSING,
        line = "dose_pro",
        model = "dose_pro_4"
    )

    @Test
    fun `resolves isolated product channels from authenticated productKey`() {
        val wrgb = resolver.resolve(
            snapshot(product = wrgbProduct),
            DeviceFirmwareChannel.STABLE
        )
        val dosePro4 = resolver.resolve(
            snapshot(product = dosePro4Product),
            DeviceFirmwareChannel.STABLE
        )

        assertEquals(
            DeviceFirmwareRuntimeContract.OFFICIAL_CHANNEL_MANIFEST_URL_PREFIX +
                "stable/light_wrgb_pro_elite.json",
            wrgb
        )
        assertEquals(
            DeviceFirmwareRuntimeContract.OFFICIAL_CHANNEL_MANIFEST_URL_PREFIX +
                "stable/dosing_dose_pro_4.json",
            dosePro4
        )
        assertNotEquals(wrgb, dosePro4)
    }

    @Test
    fun `owner custom name cannot influence product channel resolution`() {
        val unnamed = snapshot(customName = "")
        val named = snapshot(customName = "Salon Aydınlatması")

        assertEquals(
            resolver.resolve(unnamed, DeviceFirmwareChannel.BETA),
            resolver.resolve(named, DeviceFirmwareChannel.BETA)
        )
    }

    @Test
    fun `rejects unauthenticated metadata and unsafe product keys`() {
        val unauthenticated = snapshot().copy(runtimeMetadataGeneration = 0L)
        val unsafe = snapshot(
            product = wrgbProduct.copy(productKey = "LIGHT/WRGB")
        )

        assertThrows(IllegalArgumentException::class.java) {
            resolver.resolve(unauthenticated, DeviceFirmwareChannel.STABLE)
        }
        assertThrows(IllegalArgumentException::class.java) {
            resolver.resolve(unsafe, DeviceFirmwareChannel.STABLE)
        }
    }

    private fun snapshot(
        customName: String = "",
        product: ProductFixture = wrgbProduct
    ): DeviceSnapshot = DeviceSnapshot(
        identity = DeviceIdentity(
            uid = DeviceUid("AQL-CHANNEL-TEST"),
            customName = customName
        ),
        product = DeviceProduct(
            brand = "AquaLight",
            productId = product.productId,
            productKey = product.productKey,
            family = product.family,
            familyRaw = product.family.wireValue,
            line = product.line,
            model = product.model,
            displayName = product.model,
            skuCode = "AQL-CHANNEL-TEST",
            hardwareRevision = "2.0"
        ),
        firmwareVersion = "1.0.0",
        apiVersion = "1",
        protocolVersion = "1",
        capabilities = DeviceCapabilities(ota = true),
        limits = DeviceLimits(),
        runtimeMetadataGeneration = 7L
    )

    private data class ProductFixture(
        val productKey: String,
        val productId: String,
        val family: DeviceFamily,
        val line: String,
        val model: String
    )
}
