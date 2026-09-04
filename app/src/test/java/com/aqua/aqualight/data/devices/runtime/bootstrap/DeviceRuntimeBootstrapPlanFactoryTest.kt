package com.aqua.aqualight.data.devices.runtime.bootstrap

import com.aqua.aqualight.data.devices.catalog.AqlCommercialCatalogProduct
import com.aqua.aqualight.data.devices.catalog.AqlCommercialDeviceCatalog
import com.aqua.aqualight.data.devices.model.DeviceApiVersion
import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceFirmwareVersion
import com.aqua.aqualight.data.devices.model.DeviceProtocolVersion
import com.aqua.aqualight.data.devices.model.DeviceRuntimeCapabilities
import com.aqua.aqualight.data.devices.model.DeviceRuntimeIdentity
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadata
import com.aqua.aqualight.data.devices.model.DeviceRuntimeModules
import com.aqua.aqualight.data.devices.model.DeviceUid
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceRuntimeBootstrapPlanFactoryTest {
    @Test
    fun `WRGB elite hydrates Light protection and thermal owners in deterministic order`() {
        assertEquals(
            listOf(
                DeviceRuntimeDomain.LIGHT,
                DeviceRuntimeDomain.LIGHT_PROTECTION,
                DeviceRuntimeDomain.LIGHT_THERMAL
            ),
            plan("LIGHT_WRGB_PRO_ELITE")
        )
    }

    @Test
    fun `commercial families hydrate only their owning runtime domains`() {
        assertEquals(
            listOf(DeviceRuntimeDomain.LIGHT),
            plan("LIGHT_RGB_PRO_SLIM")
        )
        assertEquals(
            listOf(DeviceRuntimeDomain.TIMER),
            plan("TIMER_RELAY_PRO_4")
        )
        assertEquals(
            listOf(DeviceRuntimeDomain.COOLING),
            plan("COOLING_COOL_PRO_1F")
        )

        // Dosing deliberately remains in its existing owner-scoped production runtime. This plan
        // must not create a second Dosing state owner or duplicate its authenticated refresh.
        assertEquals(emptyList<DeviceRuntimeDomain>(), plan("DOSING_DOSE_PRO_4"))
    }

    private fun plan(productKey: String): List<DeviceRuntimeDomain> =
        DeviceRuntimeBootstrapPlanFactory.create(product(productKey).toMetadata()).domains

    private fun product(productKey: String): AqlCommercialCatalogProduct =
        AqlCommercialDeviceCatalog.products.single { it.productKey.value == productKey }

    private fun AqlCommercialCatalogProduct.toMetadata(): DeviceRuntimeMetadata =
        DeviceRuntimeMetadata(
            identity = DeviceRuntimeIdentity(
                deviceUid = DeviceUid("bootstrap-${model.value}"),
                productKey = productKey,
                productId = productId,
                family = family,
                line = line,
                model = model,
                brand = "AquaLight",
                displayName = displayName,
                skuId = skuId,
                skuCode = skuCode,
                hardwareRevision = hardwareRevision,
                firmwareVersion = DeviceFirmwareVersion("6.0.0"),
                apiVersion = DeviceApiVersion(1),
                protocolVersion = DeviceProtocolVersion(1)
            ),
            capabilities = DeviceRuntimeCapabilities(
                capabilities = profile.capabilities,
                limits = limits,
                supportedFeatures = profile.supportedFeatures,
                supportedScreens = profile.supportedScreens
            ),
            modules = DeviceRuntimeModules(
                light = family == DeviceFamily.LIGHT,
                cooling = profile.capabilities.cooling,
                temperature = profile.capabilities.temperature,
                timerApi = family == DeviceFamily.TIMER,
                timerEngine = family == DeviceFamily.TIMER,
                dosing = family == DeviceFamily.DOSING,
                network = true,
                discovery = true,
                firmware = true,
                system = true
            )
        )
}
