package com.aqua.aqualight.data.devices.repository

import com.aqua.aqualight.data.devices.model.DeviceApiVersion
import com.aqua.aqualight.data.devices.model.DeviceCapabilitySet
import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceFirmwareVersion
import com.aqua.aqualight.data.devices.model.DeviceHardwareRevision
import com.aqua.aqualight.data.devices.model.DeviceIdentity
import com.aqua.aqualight.data.devices.model.DeviceLimitSet
import com.aqua.aqualight.data.devices.model.DeviceProduct
import com.aqua.aqualight.data.devices.model.DeviceProductId
import com.aqua.aqualight.data.devices.model.DeviceProductKey
import com.aqua.aqualight.data.devices.model.DeviceProductLine
import com.aqua.aqualight.data.devices.model.DeviceProductModel
import com.aqua.aqualight.data.devices.model.DeviceProtocolVersion
import com.aqua.aqualight.data.devices.model.DeviceRuntimeCapabilities
import com.aqua.aqualight.data.devices.model.DeviceRuntimeEndpoint
import com.aqua.aqualight.data.devices.model.DeviceRuntimeIdentity
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadataFailureCode
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadataFragment
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadataGenerationState
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadataReduction
import com.aqua.aqualight.data.devices.model.DeviceRuntimeModuleKey
import com.aqua.aqualight.data.devices.model.DeviceRuntimeModules
import com.aqua.aqualight.data.devices.model.DeviceSkuCode
import com.aqua.aqualight.data.devices.model.DeviceSkuId
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceRuntimeMetadataGenerationTest {

    private val reducer = DeviceRuntimeMetadataReducer()
    private val deviceUid = DeviceUid("typed-generation-device")

    @Test
    fun `metadata publishes only after all three fragments from one generation`() {
        val collecting = reducer.begin(deviceUid = deviceUid, previous = null)
        assertNull(collecting.publishedMetadata)

        val afterModules = reducer.accept(
            collecting,
            DeviceRuntimeMetadataFragment.Modules(collecting.generation, modules())
        )
        assertTrue(afterModules is DeviceRuntimeMetadataGenerationState.Collecting)
        assertNull(afterModules.publishedMetadata)

        val afterIdentity = reducer.accept(
            afterModules,
            DeviceRuntimeMetadataFragment.Identity(collecting.generation, identity())
        )
        assertTrue(afterIdentity is DeviceRuntimeMetadataGenerationState.Collecting)
        assertNull(afterIdentity.publishedMetadata)

        val ready = reducer.accept(
            afterIdentity,
            DeviceRuntimeMetadataFragment.Capabilities(collecting.generation, capabilities())
        )
        assertTrue(ready is DeviceRuntimeMetadataGenerationState.Ready)
        val metadata = checkNotNull(ready.publishedMetadata)
        assertEquals(identity(), metadata.identity)
        assertEquals(capabilities(), metadata.capabilities)
        assertEquals(modules(), metadata.modules)
    }

    @Test
    fun `new authentication invalidates ready metadata and ignores stale fragments`() {
        val first = reducer.begin(deviceUid = deviceUid, previous = null)
        val firstReady = readyState(first)
        assertTrue(firstReady is DeviceRuntimeMetadataGenerationState.Ready)

        val second = reducer.begin(deviceUid = deviceUid, previous = firstReady)
        assertEquals(first.generation.value + 1L, second.generation.value)
        assertNull(second.publishedMetadata)
        assertNull(second.identity)
        assertNull(second.capabilities)
        assertNull(second.modules)

        val stale = reducer.reduce(
            current = second,
            fragment = DeviceRuntimeMetadataFragment.Identity(first.generation, identity())
        )
        assertTrue(stale is DeviceRuntimeMetadataReduction.IgnoredStale)
        assertEquals(second, stale.state)
        assertNull(stale.state.publishedMetadata)
    }

    @Test
    fun `conflicting duplicate rejects generation and withdraws publication`() {
        val collecting = reducer.begin(deviceUid = deviceUid, previous = null)
        val ready = readyState(collecting)
        val conflicting = identity().copy(model = DeviceProductModel("relay_pro_4"))

        val reduction = reducer.reduce(
            current = ready,
            fragment = DeviceRuntimeMetadataFragment.Identity(collecting.generation, conflicting)
        )

        assertTrue(reduction is DeviceRuntimeMetadataReduction.Rejected)
        val rejected = reduction.state as DeviceRuntimeMetadataGenerationState.Rejected
        assertEquals(
            DeviceRuntimeMetadataFailureCode.CONFLICTING_IDENTITY,
            rejected.failure.code
        )
        assertNull(rejected.publishedMetadata)
    }

    @Test
    fun `identity for another device rejects current generation`() {
        val collecting = reducer.begin(deviceUid = deviceUid, previous = null)
        val wrongIdentity = identity().copy(deviceUid = DeviceUid("another-device"))

        val reduction = reducer.reduce(
            current = collecting,
            fragment = DeviceRuntimeMetadataFragment.Identity(
                collecting.generation,
                wrongIdentity
            )
        )

        val rejected = reduction.state as DeviceRuntimeMetadataGenerationState.Rejected
        assertEquals(DeviceRuntimeMetadataFailureCode.DEVICE_UID_MISMATCH, rejected.failure.code)
        assertNull(rejected.publishedMetadata)
    }

    @Test
    fun `exact parsers reject missing unknown normalized and coerced fields`() {
        val identityJson = identityJson()
        val parsedIdentity = DeviceRuntimeIdentityParser.parse(deviceUid, identityJson).getOrThrow()
        assertEquals(identity(), parsedIdentity.identity)

        val missingModel = JSONObject(identityJson.toString()).apply { remove("model") }
        assertTrue(DeviceRuntimeIdentityParser.parse(deviceUid, missingModel).isFailure)

        val unknownField = JSONObject(identityJson.toString()).put("legacyModel", "relay_pro_2")
        assertTrue(DeviceRuntimeIdentityParser.parse(deviceUid, unknownField).isFailure)

        val normalizedFamily = JSONObject(identityJson.toString()).put("family", "TIMER")
        assertTrue(DeviceRuntimeIdentityParser.parse(deviceUid, normalizedFamily).isFailure)

        val coercedVersion = JSONObject(identityJson.toString()).put("apiVersion", "1")
        assertTrue(DeviceRuntimeIdentityParser.parse(deviceUid, coercedVersion).isFailure)

        val capabilitiesJson = capabilitiesJson()
        assertEquals(
            capabilities(),
            DeviceRuntimeCapabilitiesParser.parse(capabilitiesJson).getOrThrow()
        )

        val missingBoolean = JSONObject(capabilitiesJson.toString()).apply {
            getJSONObject("capabilities").remove("dosing")
        }
        assertTrue(DeviceRuntimeCapabilitiesParser.parse(missingBoolean).isFailure)

        val coercedBoolean = JSONObject(capabilitiesJson.toString()).apply {
            getJSONObject("capabilities").put("dosing", "false")
        }
        assertTrue(DeviceRuntimeCapabilitiesParser.parse(coercedBoolean).isFailure)

        val unknownFeature = JSONObject(capabilitiesJson.toString()).apply {
            getJSONArray("supportedFeatures").put("TIMER_CONTROL_V2")
        }
        assertTrue(DeviceRuntimeCapabilitiesParser.parse(unknownFeature).isFailure)
    }

    @Test
    fun `provisioning projection is atomic and preserves owner fields`() {
        val provisional = DeviceSnapshot(
            identity = DeviceIdentity(
                uid = deviceUid,
                customName = "My timer"
            ),
            product = DeviceProduct(),
            endpoint = DeviceRuntimeEndpoint(ip = "192.168.1.20")
        )
        val parsedIdentity = DeviceRuntimeIdentityParser.parse(deviceUid, identityJson()).getOrThrow()
        val parsedCapabilities = DeviceRuntimeCapabilitiesParser.parse(capabilitiesJson()).getOrThrow()

        val projected = DeviceRuntimeMetadataProjector.applyProvisioningMetadata(
            snapshot = provisional,
            parsedIdentity = parsedIdentity,
            capabilities = parsedCapabilities
        )

        assertEquals("My timer", projected.identity.customName)
        assertEquals("192.168.1.20", projected.endpoint.ip)
        assertEquals("TIMER_RELAY_PRO_2", projected.product.productKey)
        assertEquals(2, projected.limits.timerChannelCount)
        assertTrue(projected.capabilities.standaloneTimer)
        assertFalse(projected.capabilities.dosing)
    }

    private fun readyState(
        collecting: DeviceRuntimeMetadataGenerationState.Collecting
    ): DeviceRuntimeMetadataGenerationState {
        var state: DeviceRuntimeMetadataGenerationState = collecting
        state = reducer.accept(
            state,
            DeviceRuntimeMetadataFragment.Identity(collecting.generation, identity())
        )
        state = reducer.accept(
            state,
            DeviceRuntimeMetadataFragment.Capabilities(collecting.generation, capabilities())
        )
        state = reducer.accept(
            state,
            DeviceRuntimeMetadataFragment.Modules(collecting.generation, modules())
        )
        return state
    }

    private fun DeviceRuntimeMetadataReducer.accept(
        current: DeviceRuntimeMetadataGenerationState,
        fragment: DeviceRuntimeMetadataFragment
    ): DeviceRuntimeMetadataGenerationState {
        val reduction = reduce(current, fragment)
        assertTrue(reduction is DeviceRuntimeMetadataReduction.Accepted)
        return reduction.state
    }

    private fun identity(): DeviceRuntimeIdentity = DeviceRuntimeIdentity(
        deviceUid = deviceUid,
        productKey = DeviceProductKey("TIMER_RELAY_PRO_2"),
        productId = DeviceProductId("com.aqualight.timer.relay_pro_2"),
        family = DeviceFamily.TIMER,
        line = DeviceProductLine("relay_pro"),
        model = DeviceProductModel("relay_pro_2"),
        brand = "AquaLight",
        displayName = "Relay Pro 2",
        skuId = DeviceSkuId("com.aqualight.timer.relay_pro_2.global.black"),
        skuCode = DeviceSkuCode("AQL-T-RP2-GLB-BLK"),
        hardwareRevision = DeviceHardwareRevision("2.0"),
        firmwareVersion = DeviceFirmwareVersion("6.0.0"),
        apiVersion = DeviceApiVersion(1),
        protocolVersion = DeviceProtocolVersion(1)
    )

    private fun capabilities(): DeviceRuntimeCapabilities = DeviceRuntimeCapabilities(
        capabilities = DeviceCapabilitySet(
            light = false,
            manualLight = false,
            lightProgram = false,
            lightPresets = false,
            lightSimulation = false,
            fan = false,
            cooling = false,
            temperature = false,
            standaloneTimer = true,
            dosing = false,
            timeSync = true,
            ota = true
        ),
        limits = DeviceLimitSet(
            lightChannelCount = 0,
            fanOutputCount = 0,
            temperatureSensorCount = 0,
            timerChannelCount = 2,
            dosingChannelCount = 0
        ),
        supportedFeatures = setOf(
            com.aqua.aqualight.data.devices.contract.AqlDeviceFeatureKey.WIFI_SETUP,
            com.aqua.aqualight.data.devices.contract.AqlDeviceFeatureKey.LAN_DISCOVERY,
            com.aqua.aqualight.data.devices.contract.AqlDeviceFeatureKey.TIMER_CONTROL,
            com.aqua.aqualight.data.devices.contract.AqlDeviceFeatureKey.TIMER_MANUAL_RUN,
            com.aqua.aqualight.data.devices.contract.AqlDeviceFeatureKey.OTA_UPDATE
        ),
        supportedScreens = setOf(
            com.aqua.aqualight.data.devices.contract.AqlDeviceScreenKey.OVERVIEW,
            com.aqua.aqualight.data.devices.contract.AqlDeviceScreenKey.TIMER_CONTROL,
            com.aqua.aqualight.data.devices.contract.AqlDeviceScreenKey.TIMER_CHANNELS,
            com.aqua.aqualight.data.devices.contract.AqlDeviceScreenKey.TIMER_SCHEDULES,
            com.aqua.aqualight.data.devices.contract.AqlDeviceScreenKey.TIMER_MANUAL_RUN,
            com.aqua.aqualight.data.devices.contract.AqlDeviceScreenKey.ADVANCED
        )
    )

    private fun modules(): DeviceRuntimeModules = DeviceRuntimeModules(
        light = false,
        cooling = false,
        temperature = false,
        timerApi = true,
        timerEngine = true,
        dosing = false,
        network = true,
        discovery = true,
        firmware = true,
        system = true
    )

    private fun identityJson(): JSONObject = JSONObject()
        .put("productKey", "TIMER_RELAY_PRO_2")
        .put("productId", "com.aqualight.timer.relay_pro_2")
        .put("setupCode", "AQL-T-RP2")
        .put("deviceUid", deviceUid.value)
        .put("shortId", "A1B2C3")
        .put("serialNumber", "AQL-T-RP2-000001")
        .put("firmwareSerial", "FW-000001")
        .put("macAddress", "AA:BB:CC:DD:EE:FF")
        .put("brand", "AquaLight")
        .put("family", "timer")
        .put("line", "relay_pro")
        .put("model", "relay_pro_2")
        .put("displayName", "Relay Pro 2")
        .put("skuId", "com.aqualight.timer.relay_pro_2.global.black")
        .put("skuCode", "AQL-T-RP2-GLB-BLK")
        .put("firmwareVersion", "6.0.0")
        .put("hardwareRevision", "2.0")
        .put("apiVersion", 1)
        .put("protocolVersion", 1)
        .put(
            "runtime",
            JSONObject()
                .put("transport", "websocket")
                .put("wsSchema", "aql.ws.v1")
                .put("wsPath", "/aql/v1/ws")
                .put("wsPort", 81)
                .put("wsProtocolVersion", 1)
        )

    private fun capabilitiesJson(): JSONObject = JSONObject()
        .put(
            "capabilities",
            JSONObject()
                .put("light", false)
                .put("manualLight", false)
                .put("lightProgram", false)
                .put("lightPresets", false)
                .put("lightSimulation", false)
                .put("fan", false)
                .put("cooling", false)
                .put("temperature", false)
                .put("standaloneTimer", true)
                .put("dosing", false)
                .put("timeSync", true)
                .put("ota", true)
        )
        .put(
            "limits",
            JSONObject()
                .put("lightChannelCount", 0)
                .put("fanOutputCount", 0)
                .put("temperatureSensorCount", 0)
                .put("timerChannelCount", 2)
                .put("dosingChannelCount", 0)
        )
        .put(
            "supportedFeatures",
            JSONArray(
                listOf(
                    "WIFI_SETUP",
                    "LAN_DISCOVERY",
                    "TIMER_CONTROL",
                    "TIMER_MANUAL_RUN",
                    "OTA_UPDATE"
                )
            )
        )
        .put(
            "supportedScreens",
            JSONArray(
                listOf(
                    "OVERVIEW",
                    "TIMER_CONTROL",
                    "TIMER_CHANNELS",
                    "TIMER_SCHEDULES",
                    "TIMER_MANUAL_RUN",
                    "ADVANCED"
                )
            )
        )
}
