package com.aqua.aqualight.data.devices

import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.data.devices.catalog.AqlCommercialCatalogFailureCode
import com.aqua.aqualight.data.devices.catalog.AqlCommercialCatalogProduct
import com.aqua.aqualight.data.devices.catalog.AqlCommercialCatalogValidation
import com.aqua.aqualight.data.devices.catalog.AqlCommercialDeviceCatalog
import com.aqua.aqualight.data.devices.catalog.expectedRuntimeModules
import com.aqua.aqualight.data.devices.model.DeviceApiVersion
import com.aqua.aqualight.data.devices.model.DeviceCapabilities
import com.aqua.aqualight.data.devices.model.DeviceFirmwareVersion
import com.aqua.aqualight.data.devices.model.DeviceIdentity
import com.aqua.aqualight.data.devices.model.DeviceLimits
import com.aqua.aqualight.data.devices.model.DeviceProduct
import com.aqua.aqualight.data.devices.model.DeviceProductKey
import com.aqua.aqualight.data.devices.model.DeviceRuntimeCapabilities
import com.aqua.aqualight.data.devices.model.DeviceRuntimeIdentity
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadata
import com.aqua.aqualight.data.devices.model.DeviceProtocolVersion
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AqlCommercialDeviceCatalogTest {

    @Test
    fun `all nine generated products validate as complete typed runtime metadata`() {
        AqlCommercialDeviceCatalog.products.forEach { product ->
            val validation = AqlCommercialDeviceCatalog.validate(product.toMetadata())
            assertTrue(validation is AqlCommercialCatalogValidation.Valid)
            assertEquals(
                product,
                (validation as AqlCommercialCatalogValidation.Valid).product
            )
        }
    }

    @Test
    fun `exact snapshot identity and profile validate against one catalog row`() {
        val product = product("LIGHT_WRGB_PRO_ELITE")
        val validation = AqlCommercialDeviceCatalog.validateSnapshot(product.toSnapshot())

        assertTrue(validation is AqlCommercialCatalogValidation.Valid)
        assertEquals(product, (validation as AqlCommercialCatalogValidation.Valid).product)
    }

    @Test
    fun `capability drift rejects an otherwise exact product identity`() {
        val product = product("LIGHT_RGB_PRO_SLIM")
        val metadata = product.toMetadata().let { current ->
            current.copy(
                capabilities = current.capabilities.copy(
                    capabilities = current.capabilities.capabilities.copy(fan = true)
                )
            )
        }

        val invalid = AqlCommercialDeviceCatalog.validate(metadata)
            as AqlCommercialCatalogValidation.Invalid
        assertEquals(AqlCommercialCatalogFailureCode.CAPABILITIES_MISMATCH, invalid.failure.code)
        assertEquals("capabilities", invalid.failure.field)
    }

    @Test
    fun `runtime module drift rejects an otherwise exact dosing product`() {
        val product = product("DOSING_DOSE_PRO_2")
        val metadata = product.toMetadata().let { current ->
            current.copy(
                modules = current.modules.copy(
                    timerApi = true,
                    dosing = false
                )
            )
        }

        val invalid = AqlCommercialDeviceCatalog.validate(metadata)
            as AqlCommercialCatalogValidation.Invalid
        assertEquals(AqlCommercialCatalogFailureCode.MODULES_MISMATCH, invalid.failure.code)
        assertEquals("modules", invalid.failure.field)
    }

    @Test
    fun `unknown compatibility identity fails closed without model fallback`() {
        val product = product("TIMER_RELAY_PRO_2")
        val metadata = product.toMetadata().let { current ->
            current.copy(
                identity = current.identity.copy(
                    productKey = DeviceProductKey("TIMER_RELAY_PRO_UNKNOWN")
                )
            )
        }

        val invalid = AqlCommercialDeviceCatalog.validate(metadata)
            as AqlCommercialCatalogValidation.Invalid
        assertEquals(
            AqlCommercialCatalogFailureCode.UNKNOWN_COMPATIBILITY_IDENTITY,
            invalid.failure.code
        )
    }

    @Test
    fun `unknown snapshot feature withdraws root family menus and routes`() {
        val snapshot = product("DOSING_DOSE_PRO_2").toSnapshot().copy(
            supportedFeatures = listOf("DOSING_CONTROL", "LEGACY_DOSING_ALIAS")
        )

        val root = snapshot.toDeviceRootSnapshot()

        assertEquals(DeviceRootCatalogState.INVALID, root.catalogState)
        assertEquals(OwnerDeviceFamily.UNKNOWN, root.family)
        assertTrue(root.menuFeatures.isEmpty())
        assertTrue(root.allowedRoutes.isEmpty())
        assertTrue(root.capabilities.isEmpty())
        assertEquals("", root.productKey)
    }

    private fun product(productKey: String): AqlCommercialCatalogProduct =
        AqlCommercialDeviceCatalog.products.single { it.productKey.value == productKey }

    private fun AqlCommercialCatalogProduct.toMetadata(): DeviceRuntimeMetadata =
        DeviceRuntimeMetadata(
            identity = DeviceRuntimeIdentity(
                deviceUid = DeviceUid("catalog-${model.value}"),
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
            modules = expectedRuntimeModules()
        )

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
