package com.aqua.aqualight.data.devices.repository

import com.aqua.aqualight.data.devices.contract.AqlDeviceFeatureKey
import com.aqua.aqualight.data.devices.contract.AqlDeviceScreenKey
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
import com.aqua.aqualight.data.devices.model.DeviceRuntimeDeviceNameStatus
import com.aqua.aqualight.data.devices.model.DeviceRuntimeEndpoint
import com.aqua.aqualight.data.devices.model.DeviceRuntimeIdentity
import com.aqua.aqualight.data.devices.model.DeviceRuntimeIdentityEnvelope
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadataFailureCode
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadataFragment
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadataGenerationState
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadataReduction
import com.aqua.aqualight.data.devices.model.DeviceRuntimeModuleStatus
import com.aqua.aqualight.data.devices.model.DeviceRuntimeModules
import com.aqua.aqualight.data.devices.model.DeviceRuntimeTransportMetadata
import com.aqua.aqualight.data.devices.model.DeviceSkuCode
import com.aqua.aqualight.data.devices.model.DeviceSkuId
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceRuntimeMetadataGenerationTest {

    private val reducer = DeviceRuntimeMetadataReducer()
    private val deviceUid = DeviceUid("typed-generation-device")

    @Test
    fun `metadata publishes only after exact three fragments from one generation`() {
        val collecting = reducer.begin(deviceUid, null)
        val afterModules = reducer.accept(
            collecting,
            DeviceRuntimeMetadataFragment.Modules(collecting.generation, moduleStatus())
        )
        assertTrue(afterModules is DeviceRuntimeMetadataGenerationState.Collecting)
        assertNull(afterModules.publishedMetadata)

        val afterIdentity = reducer.accept(
            afterModules,
            DeviceRuntimeMetadataFragment.Identity(collecting.generation, identityEnvelope())
        )
        assertTrue(afterIdentity is DeviceRuntimeMetadataGenerationState.Collecting)

        val ready = reducer.accept(
            afterIdentity,
            DeviceRuntimeMetadataFragment.Capabilities(collecting.generation, capabilities())
        ) as DeviceRuntimeMetadataGenerationState.Ready

        assertEquals(identity(), ready.metadata.identity)
        assertEquals(capabilities(), ready.metadata.capabilities)
        assertEquals(modules(), ready.metadata.modules)
        assertEquals(identityEnvelope(), ready.identityEnvelope)
        assertEquals(moduleStatus(), ready.moduleStatus)
    }

    @Test
    fun `new authentication withdraws publication and ignores stale fragments`() {
        val first = reducer.begin(deviceUid, null)
        val firstReady = readyState(first)
        val second = reducer.begin(deviceUid, firstReady)

        assertEquals(first.generation.value + 1L, second.generation.value)
        assertNull(second.publishedMetadata)
        assertNull(second.identity)
        assertNull(second.capabilities)
        assertNull(second.moduleStatus)

        val stale = reducer.reduce(
            second,
            DeviceRuntimeMetadataFragment.Identity(first.generation, identityEnvelope())
        )
        assertTrue(stale is DeviceRuntimeMetadataReduction.IgnoredStale)
        assertNull(stale.state.publishedMetadata)
    }

    @Test
    fun `status product or name envelope mismatch rejects generation in either arrival order`() {
        val collecting = reducer.begin(deviceUid, null)
        val wrongStatus = moduleStatus().copy(
            deviceName = moduleStatus().deviceName.copy(
                customName = "Different timer",
                effectiveDisplayName = "Different timer"
            )
        )
        val afterIdentity = reducer.accept(
            collecting,
            DeviceRuntimeMetadataFragment.Identity(collecting.generation, identityEnvelope())
        )
        val rejected = reducer.reduce(
            afterIdentity,
            DeviceRuntimeMetadataFragment.Modules(collecting.generation, wrongStatus)
        ) as DeviceRuntimeMetadataReduction.Rejected

        assertEquals(
            DeviceRuntimeMetadataFailureCode.STATUS_IDENTITY_MISMATCH,
            rejected.state.failure.code
        )
        assertEquals("device.customName", rejected.state.failure.field)
        assertNull(rejected.state.publishedMetadata)
    }

    @Test
    fun `exact parsers reject runtime mismatch unknown fields and type coercion`() {
        assertEquals(
            identityEnvelope(),
            DeviceRuntimeIdentityParser.parse(deviceUid, identityJson()).getOrThrow()
        )
        assertEquals(
            capabilities(),
            DeviceRuntimeCapabilitiesParser.parse(capabilitiesJson()).getOrThrow()
        )

        val wrongPort = JSONObject(identityJson().toString()).apply {
            getJSONObject("runtime").put("wsPort", 81)
        }
        val wrongApi = JSONObject(identityJson().toString()).put("apiVersion", 2)
        val unknownIdentity = JSONObject(identityJson().toString())
            .put("legacyModel", "relay_pro_2")
        val missingNamePolicy = JSONObject(identityJson().toString()).apply {
            remove("customNameMaxBytes")
        }
        val wrongEffectiveName = JSONObject(identityJson().toString())
            .put("effectiveDisplayName", "Relay Pro 2")
        val coercedCapability = JSONObject(capabilitiesJson().toString()).apply {
            getJSONObject("capabilities").put("dosing", "false")
        }
        val unknownFeature = JSONObject(capabilitiesJson().toString()).apply {
            getJSONArray("supportedFeatures").put("TIMER_CONTROL_V2")
        }

        assertTrue(DeviceRuntimeIdentityParser.parse(deviceUid, wrongPort).isFailure)
        assertTrue(DeviceRuntimeIdentityParser.parse(deviceUid, wrongApi).isFailure)
        assertTrue(DeviceRuntimeIdentityParser.parse(deviceUid, unknownIdentity).isFailure)
        assertTrue(DeviceRuntimeIdentityParser.parse(deviceUid, missingNamePolicy).isFailure)
        assertTrue(DeviceRuntimeIdentityParser.parse(deviceUid, wrongEffectiveName).isFailure)
        assertTrue(DeviceRuntimeCapabilitiesParser.parse(coercedCapability).isFailure)
        assertTrue(DeviceRuntimeCapabilitiesParser.parse(unknownFeature).isFailure)
    }

    @Test
    fun `ready projection is atomic preserves endpoint and publishes authenticated firmware name`() {
        val provisional = DeviceSnapshot(
            identity = DeviceIdentity(uid = deviceUid, customName = "Local stale name"),
            product = DeviceProduct(),
            endpoint = DeviceRuntimeEndpoint(ip = "192.168.1.20")
        )
        val ready = readyState(reducer.begin(deviceUid, null))
        val projected = DeviceRuntimeMetadataProjector.applyReady(provisional, ready)

        assertEquals(CUSTOM_NAME, projected.identity.customName)
        assertEquals(CUSTOM_NAME, projected.identity.effectiveDisplayName)
        assertEquals("Relay Pro 2", projected.identity.displayName)
        assertEquals("192.168.1.20", projected.endpoint.ip)
        assertEquals("TIMER_RELAY_PRO_2", projected.product.productKey)
        assertEquals(2, projected.limits.timerChannelCount)
        assertTrue(projected.capabilities.standaloneTimer)
        assertFalse(projected.capabilities.dosing)
        assertEquals(ready.generation.value, projected.runtimeMetadataGeneration)
        assertTrue(projected.hasValidatedRuntimeMetadata)

        val invalidated = DeviceRuntimeMetadataProjector.invalidate(projected)
        assertFalse(invalidated.hasValidatedRuntimeMetadata)
        assertTrue(invalidated.supportedFeatures.isEmpty())
        assertTrue(invalidated.modules.isEmpty())
        assertEquals(CUSTOM_NAME, invalidated.identity.customName)
    }

    private fun readyState(
        collecting: DeviceRuntimeMetadataGenerationState.Collecting
    ): DeviceRuntimeMetadataGenerationState.Ready {
        var state: DeviceRuntimeMetadataGenerationState = collecting
        state = reducer.accept(
            state,
            DeviceRuntimeMetadataFragment.Identity(collecting.generation, identityEnvelope())
        )
        state = reducer.accept(
            state,
            DeviceRuntimeMetadataFragment.Capabilities(collecting.generation, capabilities())
        )
        state = reducer.accept(
            state,
            DeviceRuntimeMetadataFragment.Modules(collecting.generation, moduleStatus())
        )
        return state as DeviceRuntimeMetadataGenerationState.Ready
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
        customName = CUSTOM_NAME,
        effectiveDisplayName = CUSTOM_NAME,
        nameEditable = true,
        customNameMaxBytes = 64,
        skuId = DeviceSkuId("com.aqualight.timer.relay_pro_2.global.black"),
        skuCode = DeviceSkuCode("AQL-T-RP2-GLB-BLK"),
        hardwareRevision = DeviceHardwareRevision("2.0"),
        firmwareVersion = DeviceFirmwareVersion("6.0.0"),
        apiVersion = DeviceApiVersion(1),
        protocolVersion = DeviceProtocolVersion(1)
    )

    private fun identityEnvelope(): DeviceRuntimeIdentityEnvelope = DeviceRuntimeIdentityEnvelope(
        identity = identity(),
        shortId = "A1B2C3",
        serialNumber = "AQL-T-RP2-000001",
        firmwareSerial = "FW-000001",
        macAddress = "AA:BB:CC:DD:EE:FF",
        setupCode = "AQL-T-RP2",
        runtime = DeviceRuntimeTransportMetadata(
            transport = "websocket",
            wsSchema = "aql.ws.v1",
            wsPath = "/aql/v1/ws",
            wsPort = 80,
            wsProtocolVersion = 1
        )
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
        limits = DeviceLimitSet(0, 0, 0, 2, 0),
        supportedFeatures = setOf(
            AqlDeviceFeatureKey.WIFI_SETUP,
            AqlDeviceFeatureKey.LAN_DISCOVERY,
            AqlDeviceFeatureKey.TIMER_CONTROL,
            AqlDeviceFeatureKey.TIMER_MANUAL_RUN,
            AqlDeviceFeatureKey.TIMER_CHANNEL_DISPLAY_NAME,
            AqlDeviceFeatureKey.OTA_UPDATE
        ),
        supportedScreens = setOf(
            AqlDeviceScreenKey.OVERVIEW,
            AqlDeviceScreenKey.TIMER_CONTROL,
            AqlDeviceScreenKey.TIMER_CHANNELS,
            AqlDeviceScreenKey.TIMER_SCHEDULES,
            AqlDeviceScreenKey.TIMER_MANUAL_RUN,
            AqlDeviceScreenKey.ADVANCED
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

    private fun moduleStatus(): DeviceRuntimeModuleStatus = DeviceRuntimeModuleStatus(
        productKey = identity().productKey,
        family = identity().family,
        model = identity().model,
        displayName = identity().displayName,
        uptimeMs = 123_456L,
        modules = modules(),
        deviceName = DeviceRuntimeDeviceNameStatus(
            productDisplayName = identity().displayName,
            customName = identity().customName,
            effectiveDisplayName = identity().effectiveDisplayName,
            editable = identity().nameEditable,
            maxBytes = identity().customNameMaxBytes
        )
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
        .put("customName", CUSTOM_NAME)
        .put("effectiveDisplayName", CUSTOM_NAME)
        .put("nameEditable", true)
        .put("customNameMaxBytes", 64)
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
                .put("wsPort", 80)
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
            JSONArray(capabilities().supportedFeatures.map { it.wireValue })
        )
        .put(
            "supportedScreens",
            JSONArray(capabilities().supportedScreens.map { it.wireValue })
        )

    private companion object {
        const val CUSTOM_NAME = "My timer"
    }
}
