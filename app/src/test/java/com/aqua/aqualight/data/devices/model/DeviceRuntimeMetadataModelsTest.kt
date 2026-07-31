package com.aqua.aqualight.data.devices.model

import com.aqua.aqualight.data.devices.contract.AqlDeviceFeatureKey
import com.aqua.aqualight.data.devices.contract.AqlDeviceScreenKey
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceRuntimeMetadataModelsTest {

    private val fixture: JSONObject by lazy {
        val stream = checkNotNull(
            javaClass.getResourceAsStream("/aql_product_catalog_v1.json")
        ) { "Missing shared AquaLight product catalog fixture." }
        stream.bufferedReader(Charsets.UTF_8).use { reader ->
            JSONObject(reader.readText())
        }
    }

    @Test
    fun `all nine catalog products construct complete typed metadata`() {
        val profiles = fixture.getJSONObject("profiles")
        val products = fixture.getJSONArray("products")

        assertEquals(9, products.length())

        repeat(products.length()) { index ->
            val product = products.getJSONObject(index)
            val profile = profiles.getJSONObject(product.getString("profile"))
            val family = checkNotNull(DeviceFamily.fromWireExact(product.getString("family")))
            val capabilitiesJson = profile.getJSONObject("capabilities")
            val limitsJson = product.getJSONObject("limits")
            val displayName = product.getString("displayName")

            val identity = DeviceRuntimeIdentity(
                deviceUid = DeviceUid("typed-fixture-${product.getString("model")}"),
                productKey = DeviceProductKey(product.getString("productKey")),
                productId = DeviceProductId(product.getString("productId")),
                family = family,
                line = DeviceProductLine(product.getString("line")),
                model = DeviceProductModel(product.getString("model")),
                brand = "AquaLight",
                displayName = displayName,
                customName = "",
                effectiveDisplayName = displayName,
                nameEditable = true,
                customNameMaxBytes = 64,
                skuId = DeviceSkuId(product.getString("skuId")),
                skuCode = DeviceSkuCode(product.getString("skuCode")),
                hardwareRevision = DeviceHardwareRevision(product.getString("hardwareRevision")),
                firmwareVersion = DeviceFirmwareVersion("6.0.0"),
                apiVersion = DeviceApiVersion(1),
                protocolVersion = DeviceProtocolVersion(1)
            )
            val capabilities = DeviceRuntimeCapabilities(
                capabilities = capabilitiesJson.toCapabilitySet(),
                limits = limitsJson.toLimitSet(),
                supportedFeatures = profile.getJSONArray("supportedFeatures").mapExactFeatures(),
                supportedScreens = profile.getJSONArray("supportedScreens").mapExactScreens()
            )
            val modules = modulesFor(family, capabilities.capabilities)
            val metadata = DeviceRuntimeMetadata(identity, capabilities, modules)

            assertEquals(product.getString("productKey"), metadata.identity.productKey.value)
            assertEquals(product.getString("productId"), metadata.identity.productId.value)
            assertEquals(product.getString("model"), metadata.identity.model.value)
            assertEquals(
                product.getString("hardwareRevision"),
                metadata.identity.hardwareRevision.value
            )
            assertEquals(
                metadata.identity.compatibilityIdentity,
                DeviceCompatibilityIdentity(
                    productKey = metadata.identity.productKey,
                    productId = metadata.identity.productId,
                    model = metadata.identity.model,
                    hardwareRevision = metadata.identity.hardwareRevision
                )
            )
        }
    }

    @Test
    fun `identity wire values reject normalization and incomplete versions`() {
        assertEquals(DeviceFamily.LIGHT, DeviceFamily.fromWireExact("light"))
        assertNull(DeviceFamily.fromWireExact("LIGHT"))
        assertNull(DeviceFamily.fromWireExact(" light"))
        assertNull(DeviceFamily.fromWireExact("light "))

        assertThrows(IllegalArgumentException::class.java) {
            DeviceProductKey(" LIGHT_WRGB_PRO_ELITE")
        }
        assertThrows(IllegalArgumentException::class.java) {
            DeviceProductId("com.other.light.product")
        }
        assertThrows(IllegalArgumentException::class.java) {
            DeviceProductModel("WRGB_PRO_ELITE")
        }
        assertThrows(IllegalArgumentException::class.java) {
            DeviceHardwareRevision("2.x")
        }
        assertThrows(IllegalArgumentException::class.java) {
            DeviceApiVersion(0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DeviceProtocolVersion(0)
        }
    }

    @Test
    fun `module keys are exact and timer engine never implies timer API`() {
        assertEquals(
            DeviceRuntimeModuleKey.TIMER_API,
            DeviceRuntimeModuleKey.fromWireExact("timerApi")
        )
        assertNull(DeviceRuntimeModuleKey.fromWireExact("timer"))
        assertNull(DeviceRuntimeModuleKey.fromWireExact("timerapi"))
        assertNull(DeviceRuntimeModuleKey.fromWireExact(" timerApi"))

        val dosingModules = DeviceRuntimeModules(
            light = false,
            cooling = false,
            temperature = false,
            timerApi = false,
            timerEngine = true,
            dosing = true,
            network = true,
            discovery = true,
            firmware = true,
            system = true
        )

        assertFalse(dosingModules.exposesStandaloneTimerApi)
        assertTrue(dosingModules.usesInternalTimerEngine)
        assertTrue(DeviceRuntimeModuleKey.TIMER_ENGINE in dosingModules.enabled)
        assertFalse(DeviceRuntimeModuleKey.TIMER_API in dosingModules.enabled)
        assertTrue(DeviceRuntimeModuleKey.DOSING in dosingModules.enabled)
    }

    @Test
    fun `negative channel limits are impossible`() {
        assertThrows(IllegalArgumentException::class.java) {
            DeviceLimitSet(
                lightChannelCount = -1,
                fanOutputCount = 0,
                temperatureSensorCount = 0,
                timerChannelCount = 0,
                dosingChannelCount = 0
            )
        }
    }

    private fun JSONObject.toCapabilitySet(): DeviceCapabilitySet = DeviceCapabilitySet(
        light = getBoolean("light"),
        manualLight = getBoolean("manualLight"),
        lightProgram = getBoolean("lightProgram"),
        lightPresets = getBoolean("lightPresets"),
        lightSimulation = getBoolean("lightSimulation"),
        fan = getBoolean("fan"),
        cooling = getBoolean("cooling"),
        temperature = getBoolean("temperature"),
        standaloneTimer = getBoolean("standaloneTimer"),
        dosing = getBoolean("dosing"),
        timeSync = getBoolean("timeSync"),
        ota = getBoolean("ota")
    )

    private fun JSONObject.toLimitSet(): DeviceLimitSet = DeviceLimitSet(
        lightChannelCount = getInt("lightChannelCount"),
        fanOutputCount = getInt("fanOutputCount"),
        temperatureSensorCount = getInt("temperatureSensorCount"),
        timerChannelCount = getInt("timerChannelCount"),
        dosingChannelCount = getInt("dosingChannelCount")
    )

    private fun JSONArray.mapExactFeatures(): Set<AqlDeviceFeatureKey> = buildSet {
        repeat(length()) { index ->
            add(checkNotNull(AqlDeviceFeatureKey.fromWireExact(getString(index))))
        }
    }

    private fun JSONArray.mapExactScreens(): Set<AqlDeviceScreenKey> = buildSet {
        repeat(length()) { index ->
            add(checkNotNull(AqlDeviceScreenKey.fromWireExact(getString(index))))
        }
    }

    private fun modulesFor(
        family: DeviceFamily,
        capabilities: DeviceCapabilitySet
    ): DeviceRuntimeModules = DeviceRuntimeModules(
        light = family == DeviceFamily.LIGHT,
        cooling = capabilities.cooling,
        temperature = capabilities.temperature,
        timerApi = family == DeviceFamily.TIMER,
        timerEngine = family == DeviceFamily.TIMER || family == DeviceFamily.DOSING,
        dosing = family == DeviceFamily.DOSING,
        network = true,
        discovery = true,
        firmware = true,
        system = true
    )
}
