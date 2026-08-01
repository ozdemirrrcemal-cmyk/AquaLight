package com.aqua.aqualight.data.devices.runtime.modules.cooling

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommand
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCoolingRepositoryContractTest {
    @Test
    fun `status and config use correlated response values and shared state`() = runBlocking {
        val gateway = FixtureGateway()
        val repository = DeviceCoolingRuntimeRepository(gateway)

        val statusOutcome = repository.requestStatus(DEVICE_UID)
        val applyOutcome = repository.applyConfig(
            DEVICE_UID,
            DeviceCoolingConfigApplyPayload(
                mode = DeviceCoolingMode.ON,
                minTemperatureC = 29.0,
                maxTemperatureC = 36.0,
                fans = listOf(DeviceCoolingFanDisplayNamePayload("fan1", "Sol Fan")),
                save = true
            )
        )
        val state = repository.states.value.getValue(DEVICE_UID)

        assertTrue(statusOutcome is DeviceRuntimeCommandOutcome.Success)
        assertTrue(applyOutcome is DeviceRuntimeCommandOutcome.Success)
        assertEquals(listOf("status.get", "config.apply"), gateway.actions)
        assertEquals(
            setOf("mode", "minTemperatureC", "maxTemperatureC", "fans", "save"),
            gateway.encoded.getValue("config.apply").keys().asSequence().toSet()
        )
        assertEquals(DeviceCoolingMode.ON, state.status?.mode)
        assertEquals(27.4, state.temperature?.temperatureC!!, 0.0001)
        assertEquals("Sol Fan", state.config?.fans?.single()?.fan?.displayName)
    }

    private class FixtureGateway : DeviceRuntimeCommandGateway {
        val actions = mutableListOf<String>()
        val encoded = mutableMapOf<String, JSONObject>()

        override suspend fun <T> execute(
            deviceUid: DeviceUid,
            command: DeviceRuntimeCommand<T>,
            timeoutMillis: Long
        ): DeviceRuntimeCommandOutcome<T> {
            actions += command.action
            encoded[command.action] = command.encodeData()
            val responseData = when (command.action) {
                DeviceCoolingRuntimeContract.Action.STATUS_GET ->
                    DeviceCoolingRuntimeFixtures.status()
                DeviceCoolingRuntimeContract.Action.CONFIG_APPLY ->
                    DeviceCoolingRuntimeFixtures.configApply()
                else -> error("Unexpected action ${command.action}")
            }
            val value = command.parseSuccess(
                AqlWsIncomingMessage.Response(
                    id = "res-${command.action}",
                    type = "res",
                    module = command.module,
                    action = command.action,
                    data = responseData,
                    ok = true,
                    statusCode = 200
                )
            )
            return DeviceRuntimeCommandOutcome.Success(
                deviceUid = deviceUid,
                module = command.module,
                action = command.action,
                messageId = "res-${command.action}",
                generation = DeviceRuntimeConnectionGeneration(1L),
                statusCode = 200,
                value = value
            )
        }
    }

    private companion object {
        val DEVICE_UID = DeviceUid("AQL-COOLING-REPOSITORY")
    }
}
