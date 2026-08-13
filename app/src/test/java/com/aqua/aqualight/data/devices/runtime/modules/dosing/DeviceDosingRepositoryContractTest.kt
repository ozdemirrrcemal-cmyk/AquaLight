package com.aqua.aqualight.data.devices.runtime.modules.dosing

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommand
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import com.aqua.aqualight.data.devices.runtime.modules.dosing.contract.DeviceDosingRuntimeAccess
import com.aqua.aqualight.data.devices.runtime.modules.dosing.contract.DeviceDosingRuntimeContract
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingDistributedProgramConfig
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingProgram
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingProgramMode
import com.aqua.aqualight.data.devices.runtime.modules.dosing.repository.DeviceDosingRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.dosing.state.DeviceDosingRuntimeStateStore
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDosingRepositoryContractTest {
    @Test
    fun `program save loads channel baseline and sends current expected revision`() = runBlocking {
        val gateway = FixtureGateway()
        val repository = repository(gateway)

        val outcome = repository.saveProgram(DEVICE_UID, "channel1", program())

        assertTrue(outcome is DeviceRuntimeCommandOutcome.Success)
        assertEquals(listOf("status.get", "program.apply"), gateway.actions)
        assertEquals(setOf("channelKey"), gateway.encoded[0].keySet())
        assertEquals(7L, gateway.encoded[1].getLong("expectedRevision"))
        assertEquals("single", gateway.encoded[1].getJSONObject("program").getString("mode"))
        val state = repository.states.value.getValue(DEVICE_UID)
        assertEquals(8L, state.channel("channel1")?.channel?.revision)
        assertTrue(state.requiresStatusRefresh)
    }

    @Test
    fun `rename and reset reuse authoritative revision instead of schedule list replacement`() = runBlocking {
        val gateway = FixtureGateway()
        val repository = repository(gateway)

        assertTrue(repository.requestChannelStatus(DEVICE_UID, "channel1") is DeviceRuntimeCommandOutcome.Success)
        assertTrue(repository.setChannelDisplayName(DEVICE_UID, "channel1", "Macro Pump") is DeviceRuntimeCommandOutcome.Success)
        assertTrue(repository.resetChannel(DEVICE_UID, "channel1") is DeviceRuntimeCommandOutcome.Success)

        assertEquals(listOf("status.get", "config.apply", "channel.reset"), gateway.actions)
        assertEquals(7L, gateway.encoded[1].getLong("expectedRevision"))
        assertEquals(8L, gateway.encoded[2].getLong("expectedRevision"))
        assertFalse(gateway.encoded.any { it.has("schedules") })
        val channel = repository.states.value.getValue(DEVICE_UID).channel("channel1")?.channel
        assertEquals(9L, channel?.revision)
        assertNull(channel?.program)
    }

    @Test
    fun `global status and channel status remain one device isolated state`() = runBlocking {
        val gateway = FixtureGateway()
        val repository = repository(gateway)

        repository.requestStatus(DEVICE_UID)
        repository.requestChannelStatus(DEVICE_UID, "channel1")
        repository.requestStatus(OTHER_UID)

        assertEquals(2, repository.states.value.size)
        assertEquals(2, repository.states.value.getValue(DEVICE_UID).globalStatus?.channels?.size)
        assertEquals("Nutrients", repository.states.value.getValue(DEVICE_UID).channel("channel1")?.channel?.effectiveName)
        assertTrue(repository.states.value.getValue(OTHER_UID).channels.isEmpty())
    }

    private fun program() = DeviceDosingProgram(
        enabled = true,
        weekdays = List(7) { true },
        mode = DeviceDosingProgramMode.SINGLE,
        missedDoseRecoveryEnabled = true,
        config = DeviceDosingDistributedProgramConfig(10.0, 28_800_000L)
    )

    private fun repository(gateway: DeviceRuntimeCommandGateway) = DeviceDosingRuntimeRepository(
        gateway,
        DeviceDosingRuntimeStateStore()
    ) { SUPPORTED_ACCESS }

    private class FixtureGateway : DeviceRuntimeCommandGateway {
        val actions = mutableListOf<String>()
        val encoded = mutableListOf<JSONObject>()
        private var revision = 7L
        private var displayName = "Nutrients"
        private var currentProgram: JSONObject? = DeviceDosingRuntimeFixtures.singleProgram()
        private var uptimeMs = 12_000L

        override suspend fun <T> execute(
            deviceUid: DeviceUid,
            command: DeviceRuntimeCommand<T>,
            timeoutMillis: Long
        ): DeviceRuntimeCommandOutcome<T> {
            actions += command.action
            val data = command.encodeData()
            encoded += JSONObject(data.toString())
            val responseData = when (command.action) {
                DeviceDosingRuntimeContract.Action.STATUS_GET -> {
                    uptimeMs += 1L
                    if (data.has("channelKey")) {
                        DeviceDosingRuntimeFixtures.channelStatus(
                            uptimeMs = uptimeMs,
                            revision = revision,
                            displayName = displayName,
                            program = currentProgram
                        )
                    } else {
                        DeviceDosingRuntimeFixtures.globalStatus(
                            uptimeMs = uptimeMs,
                            channelOneRevision = revision,
                            channelOneName = displayName
                        )
                    }
                }
                DeviceDosingRuntimeContract.Action.CONFIG_APPLY -> {
                    assertEquals(revision, data.getLong("expectedRevision"))
                    displayName = data.optString("displayName", displayName)
                    revision += 1L
                    DeviceDosingRuntimeFixtures.channelConfigApply(revision, displayName)
                }
                DeviceDosingRuntimeContract.Action.PROGRAM_APPLY -> {
                    assertEquals(revision, data.getLong("expectedRevision"))
                    currentProgram = JSONObject(data.getJSONObject("program").toString())
                    revision += 1L
                    DeviceDosingRuntimeFixtures.programApply(revision, requireNotNull(currentProgram))
                }
                DeviceDosingRuntimeContract.Action.CHANNEL_RESET -> {
                    assertEquals(revision, data.getLong("expectedRevision"))
                    currentProgram = null
                    revision += 1L
                    DeviceDosingRuntimeFixtures.channelReset(revision)
                }
                else -> error("Unexpected Dosing repository action: ${command.action}")
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
        val DEVICE_UID = DeviceUid("AQL-DOSING-REPOSITORY")
        val OTHER_UID = DeviceUid("AQL-DOSING-OTHER")
        val SUPPORTED_ACCESS = DeviceDosingRuntimeAccess(
            supportsApi = true,
            channelCount = 2,
            supportsProgramEditing = true,
            supportsChannelReset = true,
            supportsPrime = true,
            supportsManualDose = true,
            supportsCalibrationWorkflow = true,
            supportsReservoirRefill = true,
            supportsChannelDisplayName = true
        )
    }

    private fun JSONObject.keySet(): Set<String> = keys().asSequence().toSet()
}
