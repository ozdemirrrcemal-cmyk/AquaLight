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
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadataGeneration
import com.aqua.aqualight.data.devices.model.DeviceRuntimeModules
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceRuntimeDomainBootstrapCoordinatorTest {
    @Test
    fun `hydrates required domains sequentially before RuntimeReady`() = runTest {
        val order = mutableListOf<DeviceRuntimeDomain>()
        var currentGeneration: DeviceRuntimeConnectionGeneration? = G1
        val coordinator = DeviceRuntimeDomainBootstrapCoordinator(
            ports = listOf(
                RecordingPort(DeviceRuntimeDomain.LIGHT, order),
                RecordingPort(DeviceRuntimeDomain.TIMER, order)
            ),
            currentConnectionGeneration = { currentGeneration }
        )

        val result = coordinator.hydrate(
            context = context(G1),
            plan = DeviceRuntimeBootstrapPlan(
                listOf(DeviceRuntimeDomain.LIGHT, DeviceRuntimeDomain.TIMER)
            )
        )

        assertEquals(
            listOf(DeviceRuntimeDomain.LIGHT, DeviceRuntimeDomain.TIMER),
            order
        )
        assertEquals(
            DeviceRuntimeReadiness.Ready(
                G1,
                listOf(DeviceRuntimeDomain.LIGHT, DeviceRuntimeDomain.TIMER)
            ),
            result
        )
        assertEquals(result, coordinator.readiness.value[DEVICE_UID])
    }

    @Test
    fun `generation change during hydration prevents later domains and stale RuntimeReady`() = runTest {
        val order = mutableListOf<DeviceRuntimeDomain>()
        var currentGeneration: DeviceRuntimeConnectionGeneration? = G1
        val coordinator = DeviceRuntimeDomainBootstrapCoordinator(
            ports = listOf(
                RecordingPort(DeviceRuntimeDomain.LIGHT, order) {
                    currentGeneration = G2
                },
                RecordingPort(DeviceRuntimeDomain.TIMER, order)
            ),
            currentConnectionGeneration = { currentGeneration }
        )

        val result = coordinator.hydrate(
            context = context(G1),
            plan = DeviceRuntimeBootstrapPlan(
                listOf(DeviceRuntimeDomain.LIGHT, DeviceRuntimeDomain.TIMER)
            )
        )

        assertEquals(listOf(DeviceRuntimeDomain.LIGHT), order)
        assertTrue(result is DeviceRuntimeReadiness.Stale)
        assertTrue(coordinator.readiness.value[DEVICE_UID] is DeviceRuntimeReadiness.Stale)
    }

    private fun context(generation: DeviceRuntimeConnectionGeneration) =
        DeviceRuntimeBootstrapContext(
            deviceUid = DEVICE_UID,
            connectionGeneration = generation,
            metadataGeneration = DeviceRuntimeMetadataGeneration(1L),
            metadata = product("LIGHT_RGB_PRO_SLIM").toMetadata(DEVICE_UID)
        )

    private class RecordingPort(
        override val domain: DeviceRuntimeDomain,
        private val order: MutableList<DeviceRuntimeDomain>,
        private val afterHydrate: () -> Unit = {}
    ) : DeviceRuntimeDomainBootstrapPort {
        override suspend fun hydrate(
            context: DeviceRuntimeBootstrapContext
        ): DeviceRuntimeDomainHydrationResult {
            order += domain
            afterHydrate()
            return DeviceRuntimeDomainHydrationResult.Hydrated(
                domain = domain,
                generation = context.connectionGeneration
            )
        }
    }

    private fun product(productKey: String): AqlCommercialCatalogProduct =
        AqlCommercialDeviceCatalog.products.single { it.productKey.value == productKey }

    private fun AqlCommercialCatalogProduct.toMetadata(
        deviceUid: DeviceUid
    ): DeviceRuntimeMetadata = DeviceRuntimeMetadata(
        identity = DeviceRuntimeIdentity(
            deviceUid = deviceUid,
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

    private companion object {
        val DEVICE_UID = DeviceUid("AQL-BOOTSTRAP-COORDINATOR")
        val G1 = DeviceRuntimeConnectionGeneration(1L)
        val G2 = DeviceRuntimeConnectionGeneration(2L)
    }
}
