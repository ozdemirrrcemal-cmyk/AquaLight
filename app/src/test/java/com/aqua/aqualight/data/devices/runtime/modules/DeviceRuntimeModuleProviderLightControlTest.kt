package com.aqua.aqualight.data.devices.runtime.modules

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommand
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeEventPayload
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeTypedEvent
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightMode
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightProduct
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRuntimeAccess
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRuntimeContract
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRuntimeFixtures
import com.aqua.aqualight.data.devices.runtime.modules.timer.DeviceTimerRuntimeAccess
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceRuntimeModuleProviderLightControlTest {

    @Test
    fun `committed control event reuses the already reconciled authoritative dashboard`() =
        kotlinx.coroutines.runBlocking {
            val gateway = FixtureGateway()
            val provider = DeviceRuntimeModuleProvider(
                commandGateway = gateway,
                revokeLocalCredential = { Result.success(Unit) },
                timerAccessProvider = { DeviceTimerRuntimeAccess.UNAVAILABLE },
                lightAccessProvider = {
                    DeviceLightRuntimeAccess(
                        supportsApi = true,
                        supportsManagedAutoPlan = true
                    )
                }
            )
            provider.beginRuntimeGeneration(DEVICE_UID, GENERATION)

            provider.refreshLightRuntime(DEVICE_UID)
            gateway.actions.clear()

            provider.acceptTypedRuntimeEvent(
                DeviceRuntimeTypedEvent(
                    deviceUid = DEVICE_UID,
                    generation = GENERATION,
                    messageId = "evt-control-set",
                    type = DeviceRuntimeTypedEvent.Type.LIGHT_STATUS_CHANGED,
                    payload = DeviceRuntimeEventPayload.CommandResult(
                        commandId = "cmd-control-set",
                        commandModule = DeviceLightRuntimeContract.MODULE,
                        commandAction = DeviceLightRuntimeContract.Action.CONTROL_SET,
                        sessionId = "session-1",
                        publishedAtMillis = 1L,
                        result = JSONObject()
                            .put(DeviceLightRuntimeContract.Field.MODE, DeviceLightMode.AUTO.wireValue)
                    )
                )
            )

            assertEquals(emptyList<String>(), gateway.actions)
        }

    private class FixtureGateway : DeviceRuntimeCommandGateway {
        val actions = mutableListOf<String>()

        override suspend fun <T> execute(
            deviceUid: DeviceUid,
            command: DeviceRuntimeCommand<T>,
            timeoutMillis: Long
        ): DeviceRuntimeCommandOutcome<T> {
            actions += command.action
            val data = when (command.action) {
                DeviceLightRuntimeContract.Action.STATUS_GET -> autoStatus()
                DeviceLightRuntimeContract.Action.GRAPH_GET ->
                    DeviceLightRuntimeFixtures.graph(
                        product = DeviceLightProduct.RGB_PRO_SLIM,
                        mode = DeviceLightMode.AUTO
                    )
                DeviceLightRuntimeContract.Action.CUSTOM_GET -> JSONObject()
                    .put("revision", 1)
                    .put("installed", false)
                    .put("weekdaysMask", 0)
                    .put("pointCount", 0)
                    .put("points", JSONArray())
                DeviceLightRuntimeContract.Action.AUTO_PROGRAMS_GET -> JSONObject()
                    .put("revision", 1)
                    .put("capacity", DeviceLightRuntimeContract.Limit.AUTO_PROGRAM_CAPACITY)
                    .put("programCount", 0)
                    .put("enabledCount", 0)
                    .put("programs", JSONArray())
                else -> error("Unexpected Light action: ${command.action}")
            }
            val response = AqlWsIncomingMessage.Response(
                id = "response-${actions.size}",
                type = "res",
                module = DeviceLightRuntimeContract.MODULE,
                action = command.action,
                data = data,
                ok = true,
                statusCode = 200
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

        private fun autoStatus(): JSONObject = DeviceLightRuntimeFixtures.status(
            DeviceLightProduct.RGB_PRO_SLIM
        )
            .put(DeviceLightRuntimeContract.Field.MODE, DeviceLightMode.AUTO.wireValue)
            .also { status ->
                status.getJSONObject("auto").put("runtimeState", "DARK")
            }
    }

    private companion object {
        val DEVICE_UID = DeviceUid("light-control-test")
        val GENERATION = DeviceRuntimeConnectionGeneration(1L)
    }
}
