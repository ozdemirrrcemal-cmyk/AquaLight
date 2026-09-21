package com.aqua.aqualight.data.devices.light.manual

import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualChannel
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualMode
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualMutationResult
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualScene
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommand
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightMode
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightProduct
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRuntimeContract
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRuntimeFixtures
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRuntimeStateOwner
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightStatusParser
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultDeviceLightManualOperationsTest {

    @Test
    fun `slider or preset scene stores first then activates manual mode`() = runBlocking {
        val fixture = RuntimeFixture(DeviceLightMode.AUTO)
        val operations = DefaultDeviceLightManualOperations { fixture.repository }
        val requested = DeviceLightManualScene(
            mapOf(
                DeviceLightManualChannel.RED to REQUESTED_RED_PERCENT,
                DeviceLightManualChannel.GREEN to REQUESTED_GREEN_PERCENT,
                DeviceLightManualChannel.BLUE to REQUESTED_BLUE_PERCENT,
                DeviceLightManualChannel.WHITE to REQUESTED_WHITE_PERCENT
            )
        )

        val result = operations.setScene(DEVICE_UID.value, requested)

        assertTrue(result is DeviceLightManualMutationResult.Success)
        val snapshot = (result as DeviceLightManualMutationResult.Success).snapshot
        assertEquals(DeviceLightManualMode.MANUAL, snapshot.activeMode)
        assertEquals(requested, snapshot.scene)
        assertEquals(EXPECTED_MANUAL_MUTATION_ACTIONS, fixture.gateway.actions)
    }

    @Test
    fun `turn off from custom mode confirms zero LED power and manual mode`() = runBlocking {
        val fixture = RuntimeFixture(DeviceLightMode.CUSTOM)
        val operations = DefaultDeviceLightManualOperations { fixture.repository }

        val result = operations.turnOff(DEVICE_UID.value)

        assertTrue(result is DeviceLightManualMutationResult.Success)
        val snapshot = (result as DeviceLightManualMutationResult.Success).snapshot
        assertEquals(DeviceLightManualMode.MANUAL, snapshot.activeMode)
        assertTrue(snapshot.scene.channels.values.all { percent -> percent == 0 })
        assertEquals(0, snapshot.estimatedLedPowerWatts)
        assertEquals(0f, snapshot.estimatedLedPowerRatio ?: Float.NaN, FLOAT_TOLERANCE)
        assertEquals(EXPECTED_MANUAL_OFF_ACTIONS, fixture.gateway.actions)
    }

    private class RuntimeFixture(initialMode: DeviceLightMode) {
        private val stateOwner = DeviceLightRuntimeStateOwner()
        val gateway = StatefulLightGateway(initialMode)
        val repository = DeviceLightRuntimeRepository(
            gateway = gateway,
            stateOwner = stateOwner,
            accessProvider = { MANAGED_LIGHT_ACCESS }
        )

        init {
            stateOwner.beginGeneration(DEVICE_UID, GENERATION)
            stateOwner.recordStatus(
                DEVICE_UID,
                GENERATION,
                DeviceLightStatusParser.parse(gateway.statusData())
            )
        }
    }

    private class StatefulLightGateway(initialMode: DeviceLightMode) :
        DeviceRuntimeCommandGateway {
        private var mode = initialMode
        private var manualScene = defaultScene()
        val actions = mutableListOf<String>()

        override suspend fun <T> execute(
            deviceUid: DeviceUid,
            command: DeviceRuntimeCommand<T>,
            timeoutMillis: Long
        ): DeviceRuntimeCommandOutcome<T> {
            actions += command.action
            val request = command.encodeData()
            val responseData = when (command.action) {
                DeviceLightRuntimeContract.Action.MANUAL_SET -> {
                    manualScene = JSONObject(request.getJSONObject("scene").toString())
                    JSONObject().put("scene", JSONObject(manualScene.toString()))
                }
                DeviceLightRuntimeContract.Action.MANUAL_OFF -> {
                    manualScene = zeroScene()
                    JSONObject().put("scene", JSONObject(manualScene.toString()))
                }
                DeviceLightRuntimeContract.Action.CONTROL_SET -> {
                    mode = DeviceLightMode.fromWireExact(request.getString("mode"))
                    JSONObject().put("mode", mode.wireValue)
                }
                DeviceLightRuntimeContract.Action.STATUS_GET -> statusData()
                DeviceLightRuntimeContract.Action.GRAPH_GET ->
                    DeviceLightRuntimeFixtures.graph(DeviceLightProduct.WRGB_PRO_ELITE, mode)
                else -> error("Unexpected Light action ${command.action}")
            }
            val response = AqlWsIncomingMessage.Response(
                id = "manual-response-${actions.size}",
                type = "res",
                module = command.module,
                action = command.action,
                data = responseData,
                ok = true,
                statusCode = SUCCESS_STATUS_CODE
            )
            return DeviceRuntimeCommandOutcome.Success(
                deviceUid = deviceUid,
                module = command.module,
                action = command.action,
                messageId = response.id,
                generation = GENERATION,
                statusCode = response.statusCode,
                value = command.parseSuccess(response)
            )
        }

        fun statusData(): JSONObject = DeviceLightRuntimeFixtures.status().also { data ->
            data.put("mode", mode.wireValue)
            data.getJSONObject("manual")
                .put("scene", JSONObject(manualScene.toString()))
            if (mode == DeviceLightMode.MANUAL) {
                data.put("requested", JSONObject(manualScene.toString()))
                data.put("effective", JSONObject(manualScene.toString()))
                if (manualScene.isZeroScene()) {
                    data.put("outputActive", false)
                    data.put("outputReason", "ALL_CHANNELS_ZERO")
                    data.getJSONObject("power")
                        .put("estimatedLedPowerW", 0.0)
                        .put("estimatedFixturePowerW", FIXTURE_BASE_POWER_WATTS)
                        .put("ratio", FIXTURE_BASE_POWER_WATTS / FIXTURE_POWER_LIMIT_WATTS)
                    data.getJSONObject("color")
                        .put("cieX", 0.0)
                        .put("cieY", 0.0)
                        .put("estimatedCctK", 0)
                        .put("duv", 0.0)
                        .put(
                            "displayRgb",
                            JSONObject().put("red", 0).put("green", 0).put("blue", 0)
                        )
                }
            }
        }

        private fun JSONObject.isZeroScene(): Boolean = keys().asSequence().all { key ->
            getInt(key) == 0
        }
    }

    private companion object {
        val MANAGED_LIGHT_ACCESS = DeviceLightRuntimeAccess(
            supportsApi = true,
            supportsManagedAutoPlan = true
        )
        val DEVICE_UID = DeviceUid("manual-operations-light")
        val GENERATION = DeviceRuntimeConnectionGeneration(1L)
        val EXPECTED_MANUAL_MUTATION_ACTIONS = listOf(
            DeviceLightRuntimeContract.Action.MANUAL_SET,
            DeviceLightRuntimeContract.Action.STATUS_GET,
            DeviceLightRuntimeContract.Action.GRAPH_GET,
            DeviceLightRuntimeContract.Action.CONTROL_SET,
            DeviceLightRuntimeContract.Action.STATUS_GET,
            DeviceLightRuntimeContract.Action.GRAPH_GET
        )
        val EXPECTED_MANUAL_OFF_ACTIONS =
            listOf(DeviceLightRuntimeContract.Action.MANUAL_OFF) +
                EXPECTED_MANUAL_MUTATION_ACTIONS.drop(1)
        const val FIXTURE_BASE_POWER_WATTS = 9.39
        const val FIXTURE_POWER_LIMIT_WATTS = 150.0
        const val FLOAT_TOLERANCE = 0.0001f
        const val SUCCESS_STATUS_CODE = 200
        const val REQUESTED_RED_PERCENT = 61
        const val REQUESTED_GREEN_PERCENT = 47
        const val REQUESTED_BLUE_PERCENT = 73
        const val REQUESTED_WHITE_PERCENT = 39
        const val DEFAULT_RED_PERCENT = 20
        const val DEFAULT_GREEN_PERCENT = 30
        const val DEFAULT_BLUE_PERCENT = 40
        const val DEFAULT_WHITE_PERCENT = 50

        fun defaultScene() = JSONObject()
            .put("redPercent", DEFAULT_RED_PERCENT)
            .put("greenPercent", DEFAULT_GREEN_PERCENT)
            .put("bluePercent", DEFAULT_BLUE_PERCENT)
            .put("whitePercent", DEFAULT_WHITE_PERCENT)

        fun zeroScene() = JSONObject()
            .put("redPercent", 0)
            .put("greenPercent", 0)
            .put("bluePercent", 0)
            .put("whitePercent", 0)
    }
}
