package com.aqua.aqualight.data.devices

import com.aqua.aqualight.application.devices.DeviceMetadataReadiness
import com.aqua.aqualight.application.devices.DeviceRootMenuFeature
import com.aqua.aqualight.data.devices.contract.AqlDeviceFeatureKey
import com.aqua.aqualight.data.devices.contract.AqlDeviceScreenKey
import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceConnectionState
import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceIdentity
import com.aqua.aqualight.data.devices.model.DeviceOnlineState
import com.aqua.aqualight.data.devices.model.DeviceProduct
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DeviceRuntimeBootstrapGate
import com.aqua.aqualight.data.devices.repository.DeviceRuntimeBootstrapPlan
import com.aqua.aqualight.data.devices.repository.DeviceRuntimeMetadataReducer
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AqlProductCatalogContractTest {

    private val fixture: JSONObject by lazy {
        val stream = checkNotNull(
            javaClass.getResourceAsStream("/aql_product_catalog_v1.json")
        ) { "Missing shared AquaLight product catalog fixture." }
        stream.bufferedReader(Charsets.UTF_8).use { reader ->
            JSONObject(reader.readText())
        }
    }

    private val reducer = DeviceRuntimeMetadataReducer()

    @Test
    fun `all nine firmware products map to exact menus limits and runtime modules`() {
        val profiles = fixture.getJSONObject("profiles")
        val products = fixture.getJSONArray("products")
        val observedModels = linkedSetOf<String>()
        val observedFamilies = linkedSetOf<DeviceFamily>()

        assertEquals(1, fixture.getInt("fixtureVersion"))
        assertEquals(9, products.length())

        repeat(products.length()) { index ->
            val product = products.getJSONObject(index)
            val profile = profiles.getJSONObject(product.getString("profile"))
            val model = product.getString("model")
            val deviceUid = "fixture-$model"
            val identityData = product.toIdentityData(deviceUid)
            val capabilitiesData = product.toCapabilitiesData(profile)

            val identified = reducer.applyDeviceIdentity(
                snapshot = emptyOnlineSnapshot(deviceUid),
                identityData = identityData
            )
            val snapshot = reducer.applyDeviceCapabilities(
                snapshot = identified,
                capabilitiesData = capabilitiesData
            )
            val root = snapshot.toDeviceRootSnapshot()

            observedModels += snapshot.product.model
            observedFamilies += snapshot.product.family

            assertEquals(product.getString("productKey"), snapshot.product.productKey)
            assertEquals(product.getString("productId"), snapshot.product.productId)
            assertEquals(DeviceFamily.fromWire(product.getString("family")), snapshot.product.family)
            assertEquals(product.getString("line"), snapshot.product.line)
            assertEquals(model, snapshot.product.model)
            assertEquals(product.getString("displayName"), snapshot.product.displayName)
            assertEquals(product.getString("hardwareRevision"), snapshot.product.hardwareRevision)
            assertEquals(DeviceMetadataReadiness.READY, root.metadataReadiness)

            val limits = product.getJSONObject("limits")
            assertEquals(limits.getInt("lightChannelCount"), root.lightChannelCount)
            assertEquals(limits.getInt("fanOutputCount"), root.fanOutputCount)
            assertEquals(limits.getInt("temperatureSensorCount"), root.temperatureSensorCount)
            assertEquals(limits.getInt("timerChannelCount"), root.timerChannelCount)
            assertEquals(limits.getInt("dosingChannelCount"), root.dosingChannelCount)

            assertEquals(
                profile.getJSONArray("expectedMenuFeatures").asStringSet(),
                root.menuFeatures.map(DeviceRootMenuFeature::name).toSet()
            )
            assertFalse(DeviceRootMenuFeature.LIGHT_SIMULATION in root.menuFeatures)

            val gate = DeviceRuntimeBootstrapGate()
            assertNull(gate.accept(identityResponse(identityData)))
            val plan = checkNotNull(gate.accept(capabilitiesResponse(capabilitiesData)))
            assertEquals(
                profile.getJSONArray("expectedStatusModules").asStringSet(),
                plan.requestedStatusModules()
            )
            assertEquals(snapshot.product.family, plan.family)
            assertNull(gate.accept(capabilitiesResponse(capabilitiesData)))
        }

        assertEquals(9, observedModels.size)
        assertEquals(
            setOf(
                DeviceFamily.LIGHT,
                DeviceFamily.TIMER,
                DeviceFamily.DOSING,
                DeviceFamily.COOLING
            ),
            observedFamilies
        )
    }

    @Test
    fun `wire keys are exact and legacy aliases cannot unlock menus`() {
        assertEquals(
            AqlDeviceFeatureKey.LIGHT_QUICK_SETUP,
            AqlDeviceFeatureKey.fromWire("LIGHT_QUICK_SETUP")
        )
        assertEquals(
            AqlDeviceScreenKey.DOSING_SCHEDULES,
            AqlDeviceScreenKey.fromWire("DOSING_SCHEDULES")
        )
        assertNull(AqlDeviceFeatureKey.fromWire("light.quickSetup"))
        assertNull(AqlDeviceFeatureKey.fromWire("quick_setup"))
        assertNull(AqlDeviceScreenKey.fromWire("channels"))
        assertNull(AqlDeviceScreenKey.fromWire("settings"))

        val snapshot = emptyOnlineSnapshot("legacy-aliases").copy(
            product = DeviceProduct(family = DeviceFamily.LIGHT),
            supportedFeatures = listOf("light.quickSetup", "quick_setup"),
            supportedScreens = listOf("channels", "settings")
        )

        assertTrue(snapshot.toDeviceRootSnapshot().menuFeatures.isEmpty())
    }

    @Test
    fun `bootstrap waits for both successful metadata responses in either order`() {
        val product = fixture.getJSONArray("products").getJSONObject(4)
        val profile = fixture.getJSONObject("profiles")
            .getJSONObject(product.getString("profile"))
        val identityData = product.toIdentityData("fixture-order")
        val capabilitiesData = product.toCapabilitiesData(profile)
        val gate = DeviceRuntimeBootstrapGate()

        assertNull(gate.accept(capabilitiesResponse(capabilitiesData)))
        val plan = checkNotNull(gate.accept(identityResponse(identityData)))

        assertTrue(plan.requestDosingStatus)
        assertFalse(plan.requestTimerStatus)

        gate.reset()
        assertNull(
            gate.accept(
                identityResponse(identityData).copy(
                    ok = false,
                    statusCode = 500
                )
            )
        )
        assertNull(gate.accept(capabilitiesResponse(capabilitiesData)))
    }

    private fun emptyOnlineSnapshot(deviceUid: String): DeviceSnapshot {
        return DeviceSnapshot(
            identity = DeviceIdentity(uid = DeviceUid(deviceUid)),
            product = DeviceProduct(),
            connectionState = DeviceConnectionState(
                onlineState = DeviceOnlineState.AUTHENTICATED
            )
        )
    }

    private fun JSONObject.toIdentityData(deviceUid: String): JSONObject {
        return JSONObject()
            .put("deviceUid", deviceUid)
            .put("brand", "AquaLight")
            .put("productKey", getString("productKey"))
            .put("productId", getString("productId"))
            .put("family", getString("family"))
            .put("line", getString("line"))
            .put("model", getString("model"))
            .put("displayName", getString("displayName"))
            .put("skuId", getString("skuId"))
            .put("skuCode", getString("skuCode"))
            .put("hardwareRevision", getString("hardwareRevision"))
    }

    private fun JSONObject.toCapabilitiesData(profile: JSONObject): JSONObject {
        return JSONObject()
            .put("capabilities", JSONObject(profile.getJSONObject("capabilities").toString()))
            .put("limits", JSONObject(getJSONObject("limits").toString()))
            .put(
                "supportedFeatures",
                JSONArray(profile.getJSONArray("supportedFeatures").toString())
            )
            .put(
                "supportedScreens",
                JSONArray(profile.getJSONArray("supportedScreens").toString())
            )
    }

    private fun identityResponse(data: JSONObject): AqlWsIncomingMessage.Response {
        return deviceResponse(
            action = AqlWsContract.ACTION_DEVICE_IDENTITY_GET,
            data = data
        )
    }

    private fun capabilitiesResponse(data: JSONObject): AqlWsIncomingMessage.Response {
        return deviceResponse(
            action = AqlWsContract.ACTION_DEVICE_CAPABILITIES_GET,
            data = data
        )
    }

    private fun deviceResponse(
        action: String,
        data: JSONObject
    ): AqlWsIncomingMessage.Response {
        return AqlWsIncomingMessage.Response(
            id = "fixture-response",
            type = AqlWsContract.TYPE_RESPONSE,
            module = AqlWsContract.MODULE_DEVICE,
            action = action,
            data = data,
            ok = true,
            statusCode = 200
        )
    }

    private fun DeviceRuntimeBootstrapPlan.requestedStatusModules(): Set<String> = buildSet {
        if (requestTimeStatus) add(AqlWsContract.MODULE_TIME)
        if (requestFirmwareStatus) add(AqlWsContract.MODULE_FIRMWARE)
        if (requestLightStatus) add(AqlWsContract.MODULE_LIGHT)
        if (requestCoolingStatus) add(AqlWsContract.MODULE_COOLING)
        if (requestTimerStatus) add(AqlWsContract.MODULE_TIMER)
        if (requestDosingStatus) add(AqlWsContract.MODULE_DOSING)
    }

    private fun JSONArray.asStringSet(): Set<String> = buildSet {
        repeat(length()) { index -> add(getString(index)) }
    }
}
