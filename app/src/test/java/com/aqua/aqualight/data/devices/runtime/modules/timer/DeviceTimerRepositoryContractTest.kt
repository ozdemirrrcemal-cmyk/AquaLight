@file:Suppress("MagicNumber")

package com.aqua.aqualight.data.devices.runtime.modules.timer

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommand
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceTimerRepositoryContractTest {
    @Test
    fun `repository uses scoped readback revision CAS and exact Timer serializers`() = runBlocking {
        val gateway = FixtureGateway()
        val repository = DeviceTimerRuntimeRepository(
            gateway = gateway,
            stateStore = DeviceTimerRuntimeStateStore(),
            accessProvider = { SUPPORTED_ACCESS }
        )

        val status = repository.requestStatus(DEVICE_UID)
        val replace = repository.replaceSchedules(
            deviceUid = DEVICE_UID,
            channelKey = "channel1",
            schedules = listOf(DeviceTimerRuntimeFixtures.schedulePayload())
        )
        val temporary = repository.setTemporaryOverride(
            deviceUid = DEVICE_UID,
            channelKey = "channel1",
            regime = DeviceTimerRegime.OFF,
            durationMs = 300_000L
        )

        assertSuccessful(status, replace, temporary)
        assertEquals(
            listOf("status.get", "config.apply", "status.get", "channel.set", "status.get"),
            gateway.actions
        )
        assertEquals(emptySet<String>(), gateway.encoded[0].keySetExact())
        assertEquals(
            setOf("channelKey", "expectedRevision", "schedules", "save"),
            gateway.encoded[1].keySetExact()
        )
        assertEquals(7L, gateway.encoded[1].getLong("expectedRevision"))
        assertFalse(gateway.encoded[1].getJSONArray("schedules").getJSONObject(0)
            .has("channelKey"))
        assertEquals(setOf("channelKey"), gateway.encoded[2].keySetExact())
        assertEquals(
            setOf("channelKey", "expectedRevision", "regime", "durationMs", "save"),
            gateway.encoded[3].keySetExact()
        )
        assertEquals(8L, gateway.encoded[3].getLong("expectedRevision"))
        assertFalse(gateway.encoded[3].getBoolean("save"))

        val state = repository.states.value.getValue(DEVICE_UID)
        assertEquals(8L, state.status?.revision)
        assertFalse(requireNotNull(state.status).channelScoped)
        assertEquals("Day Filter", state.channelDetails.getValue("channel1").schedules.single().name)
        assertFalse(state.requiresStatusRefresh)
    }

    private fun assertSuccessful(vararg outcomes: DeviceRuntimeCommandOutcome<*>) {
        assertTrue(outcomes.all { outcome -> outcome is DeviceRuntimeCommandOutcome.Success })
    }

    private class FixtureGateway : DeviceRuntimeCommandGateway {
        val actions = mutableListOf<String>()
        val encoded = mutableListOf<JSONObject>()
        private var revision = 7L

        override suspend fun <T> execute(
            deviceUid: DeviceUid,
            command: DeviceRuntimeCommand<T>,
            timeoutMillis: Long
        ): DeviceRuntimeCommandOutcome<T> {
            actions += command.action
            val request = command.encodeData()
            encoded += JSONObject(request.toString())
            val response = when (command.action) {
                DeviceTimerRuntimeContract.Action.STATUS_GET -> status(request)
                DeviceTimerRuntimeContract.Action.CONFIG_APPLY -> configApply(request)
                DeviceTimerRuntimeContract.Action.CHANNEL_SET -> channelSet(request)
                else -> error("Unexpected Timer action: " + command.action)
            }
            val value = command.parseSuccess(
                AqlWsIncomingMessage.Response(
                    id = "res-" + command.action,
                    type = "res",
                    module = command.module,
                    action = command.action,
                    data = response,
                    ok = true,
                    statusCode = 200
                )
            )
            return DeviceRuntimeCommandOutcome.Success(
                deviceUid = deviceUid,
                module = command.module,
                action = command.action,
                messageId = "res-" + command.action,
                generation = GENERATION,
                statusCode = 200,
                value = value
            )
        }

        private fun status(request: JSONObject): JSONObject =
            if (request.has("channelKey")) {
                DeviceTimerRuntimeFixtures.channelStatus(revision = revision)
            } else {
                DeviceTimerRuntimeFixtures.globalStatus(revision = revision)
            }

        private fun configApply(request: JSONObject): JSONObject {
            revision += 1L
            return DeviceTimerRuntimeFixtures.configApply(revision = revision).also { result ->
                result.put("appliedDisplayName", request.has("displayName"))
                result.put("replacedSchedules", request.has("schedules"))
                result.getJSONObject("channel").put(
                    "scheduleCount",
                    request.optJSONArray("schedules")?.length() ?: 1
                )
            }
        }

        private fun channelSet(request: JSONObject): JSONObject {
            assertEquals(revision, request.getLong("expectedRevision"))
            return DeviceTimerRuntimeFixtures.channelSet(revision).also { result ->
                result.getJSONObject("channel").put("scheduleCount", 1)
            }
        }
    }

    private fun JSONObject.keySetExact(): Set<String> =
        keys().asSequence().toCollection(linkedSetOf())

    private companion object {
        val DEVICE_UID = DeviceUid("AQL-TIMER-REPOSITORY")
        val GENERATION = DeviceRuntimeConnectionGeneration(1L)
        val SUPPORTED_ACCESS = DeviceTimerRuntimeAccess(
            supportsApi = true,
            channelCount = 2,
            supportsSchedules = true,
            supportsChannelState = true,
            supportsChannelDisplayName = true
        )
    }
}
