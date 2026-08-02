package com.aqua.aqualight.data.devices.runtime.modules.timer

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommand
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceTimerRepositoryContractTest {
    @Test
    fun `all Timer operations use correlated results and one device state`() = runBlocking {
        val gateway = FixtureGateway()
        val repository = DeviceTimerRuntimeRepository(
            gateway = gateway,
            stateStore = DeviceTimerRuntimeStateStore(),
            accessProvider = { SUPPORTED_ACCESS }
        )

        val status = repository.requestStatus(DEVICE_UID)
        val rename = repository.setChannelDisplayName(
            DEVICE_UID,
            "channel1",
            "Return Pump"
        )
        val channel = repository.setChannelRegime(
            DEVICE_UID,
            "channel1",
            DeviceTimerRegime.ON
        )
        val created = repository.createSchedule(
            DEVICE_UID,
            DeviceTimerRuntimeFixtures.schedulePayload(
                name = "Night Filter",
                startTimeMs = 72_000_000L
            )
        )
        val updated = repository.updateSchedule(
            DEVICE_UID,
            scheduleIndex = 1,
            schedule = DeviceTimerRuntimeFixtures.schedulePayload(
                name = "Late Filter",
                startTimeMs = 75_600_000L
            )
        )
        val deleted = repository.deleteSchedule(DEVICE_UID, scheduleIndex = 0)

        assertSuccessful(status, rename, channel, created, updated, deleted)
        assertEquals(
            listOf(
                "status.get",
                "config.apply",
                "channel.set",
                "config.apply",
                "config.apply",
                "config.apply"
            ),
            gateway.actions
        )

        val state = repository.states.value.getValue(DEVICE_UID)
        assertEquals(DeviceTimerRegime.ON, state.status?.channels?.first()?.regime)
        assertEquals("Return Pump", state.status?.channels?.first()?.displayName)
        assertEquals("Return Pump", state.config?.channels?.first()?.displayNameOverride)
        assertEquals(listOf("Late Filter"), state.config?.schedules?.map { it.name })
        assertEquals(listOf("Late Filter"), state.status?.schedules?.map { it.name })
        assertTrue(state.requiresStatusRefresh)

        val deletePayload = gateway.encoded.last()
        assertEquals(setOf("schedules", "save"), deletePayload.keys().asSequence().toSet())
        assertEquals(1, deletePayload.getJSONArray("schedules").length())
    }

    private fun assertSuccessful(vararg outcomes: DeviceRuntimeCommandOutcome<*>) {
        assertTrue(outcomes.all { outcome -> outcome is DeviceRuntimeCommandOutcome.Success })
    }

    private class FixtureGateway : DeviceRuntimeCommandGateway {
        val actions = mutableListOf<String>()
        val encoded = mutableListOf<JSONObject>()
        private var displayNameOverride: String? = "Filter"
        private var channelRegime = "Auto"
        private var schedules = JSONArray().put(DeviceTimerRuntimeFixtures.configSchedule())

        override suspend fun <T> execute(
            deviceUid: DeviceUid,
            command: DeviceRuntimeCommand<T>,
            timeoutMillis: Long
        ): DeviceRuntimeCommandOutcome<T> {
            actions += command.action
            val data = command.encodeData()
            encoded += JSONObject(data.toString())
            val responseData = when (command.action) {
                DeviceTimerRuntimeContract.Action.STATUS_GET -> DeviceTimerRuntimeFixtures.status()
                DeviceTimerRuntimeContract.Action.CONFIG_APPLY -> applyConfig(data)
                DeviceTimerRuntimeContract.Action.CHANNEL_SET -> applyChannel(data)
                else -> error("Unexpected Timer action ${command.action}")
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

        private fun applyConfig(data: JSONObject): JSONObject {
            data.optJSONArray("channels")?.let { channels ->
                val channel = channels.getJSONObject(0)
                if (channel.has("displayName")) {
                    displayNameOverride = channel.getString("displayName").ifBlank { null }
                }
                if (channel.has("regime")) channelRegime = channel.getString("regime")
            }
            data.optJSONArray("schedules")?.let { requested ->
                schedules = JSONArray().also { returned ->
                    repeat(requested.length()) { index ->
                        returned.put(
                            JSONObject(requested.getJSONObject(index).toString())
                                .put("amountMl", TIMER_STANDALONE_AMOUNT_ML)
                        )
                    }
                }
            }
            return DeviceTimerRuntimeFixtures.configApply(
                save = data.getBoolean("save"),
                appliedChannels = data.has("channels"),
                appliedSchedules = data.has("schedules"),
                channelOneDisplayNameOverride = displayNameOverride,
                channelOneRegime = channelRegime,
                schedules = JSONArray(schedules.toString())
            )
        }

        private fun applyChannel(data: JSONObject): JSONObject {
            channelRegime = data.getString("regime")
            return DeviceTimerRuntimeFixtures.channelSet(
                regime = channelRegime,
                save = data.getBoolean("save"),
                displayName = displayNameOverride ?: "Channel 1"
            )
        }
    }

    private companion object {
        val DEVICE_UID = DeviceUid("AQL-TIMER-REPOSITORY")
        val SUPPORTED_ACCESS = DeviceTimerRuntimeAccess(
            supportsApi = true,
            channelCount = 2,
            supportsSchedules = true,
            supportsChannelState = true,
            supportsChannelDisplayName = true
        )
    }
}
