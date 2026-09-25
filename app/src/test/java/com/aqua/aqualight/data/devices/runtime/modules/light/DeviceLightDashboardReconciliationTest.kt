package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommand
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceLightDashboardReconciliationTest {

    @Test
    fun `control set returns firmware commit without synchronous dashboard readback`() = runTest {
        val gateway = FixtureGateway()
        val owner = DeviceLightRuntimeStateOwner()
        owner.beginGeneration(DEVICE_UID, GENERATION)
        owner.recordStatus(
            DEVICE_UID,
            GENERATION,
            DeviceLightStatusParser.parse(DeviceLightRuntimeFixtures.status())
        )
        val runtime = DeviceLightRuntimeRepository(gateway, owner)

        val outcome = runtime.setControl(
            DEVICE_UID,
            DeviceLightControlSetPayload(DeviceLightMode.AUTO)
        )

        assertTrue(outcome is DeviceRuntimeCommandOutcome.Success)
        assertEquals(listOf(DeviceLightRuntimeContract.Action.CONTROL_SET), gateway.actions)
    }

    @Test
    fun `dashboard helper reads one coherent status graph pair`() = runTest {
        val gateway = FixtureGateway()
        val owner = DeviceLightRuntimeStateOwner()
        owner.beginGeneration(DEVICE_UID, GENERATION)
        val runtime = DeviceLightRuntimeRepository(gateway, owner)
        val coordinator = DeviceLightDashboardRefreshCoordinator(runtime)

        val result = coordinator.refresh(DEVICE_UID)

        assertTrue(result is DeviceLightDashboardRefreshResult.Success)
        assertEquals(
            listOf(
                DeviceLightRuntimeContract.Action.STATUS_GET,
                DeviceLightRuntimeContract.Action.GRAPH_GET
            ),
            gateway.actions
        )
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
                DeviceLightRuntimeContract.Action.CONTROL_SET -> JSONObject()
                    .put(DeviceLightRuntimeContract.Field.MODE, DeviceLightMode.AUTO.wireValue)
                    .put("event", DeviceLightRuntimeContract.Event.STATUS_CHANGED)
                DeviceLightRuntimeContract.Action.STATUS_GET -> autoStatus()
                DeviceLightRuntimeContract.Action.GRAPH_GET ->
                    DeviceLightRuntimeFixtures.graph(mode = DeviceLightMode.AUTO)
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

        private fun autoStatus(): JSONObject = DeviceLightRuntimeFixtures.status()
            .put(DeviceLightRuntimeContract.Field.MODE, DeviceLightMode.AUTO.wireValue)
            .also { status ->
                status.getJSONObject("auto").put("runtimeState", "DARK")
            }
    }

    private companion object {
        val DEVICE_UID = DeviceUid("light-dashboard-reconciliation")
        val GENERATION = DeviceRuntimeConnectionGeneration(1L)
    }
}
