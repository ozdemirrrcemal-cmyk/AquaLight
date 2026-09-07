package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommand
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeEventPayload
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeTypedEvent
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceLightRuntimeContractTest {

    @Test
    fun `status parser accepts exact firmware shape and rejects drift`() {
        val parsed = DeviceLightStatusParser.parse(DeviceLightRuntimeFixtures.status())

        assertEquals(1, parsed.channelCount)
        assertEquals("white", parsed.channels.single().key)
        assertEquals(DeviceLightRegime.AUTO, parsed.channels.single().regime)
        assertEquals(1, parsed.programCount)
        assertTrue(parsed.runtime.supportsManualSet)

        val extraField = DeviceLightRuntimeFixtures.status().put("extra", 1)
        assertTrue(runCatching { DeviceLightStatusParser.parse(extraField) }.isFailure)
        val unknownRegime = DeviceLightRuntimeFixtures.status().also { status ->
            status.getJSONArray("channels").getJSONObject(0).put("regime", "automatic")
        }
        assertTrue(runCatching { DeviceLightStatusParser.parse(unknownRegime) }.isFailure)
    }

    @Test
    fun `repository sends exact actions and parses correlated mutation results`() = runBlocking {
        val gateway = QueueGateway(exactResponses())
        val repository = DeviceLightRuntimeRepository(gateway)

        repository.requestStatus(DEVICE_UID).requireSuccess()
        repository.setManual(
            DEVICE_UID,
            DeviceLightManualSetPayload(
                channels = listOf(DeviceLightManualChannelPayload("white", percent = 25.0)),
                durationMs = 60_000L
            )
        ).requireSuccess()
        repository.setChannelRegime(DEVICE_UID, "white", DeviceLightRegime.ON)
            .requireSuccess()
        repository.applyProgram(
            DEVICE_UID,
            DeviceLightProgramApplyPayload(
                channelKey = "white",
                points = listOf(DeviceLightProgramPointPayload(timeMs = 0L, percent = 30.0))
            )
        ).requireSuccess()
        repository.deleteProgram(DEVICE_UID, programIndex = 1).requireSuccess()

        assertEquals(EXPECTED_ACTIONS, gateway.actions)
        assertEquals(setOf("clear", "durationMs", "channels"), gateway.payloads[1].keySet())
        assertEquals("On", gateway.payloads[2].getString("regime"))
        assertEquals(setOf("channelKey", "points", "save"), gateway.payloads[3].keySet())
        assertEquals(1, repository.states.value[DEVICE_UID]?.programCount)
    }

    @Test
    fun `manual clear uses channel key only and omits duration`() = runBlocking {
        val gateway = QueueGateway(
            mutableMapOf(
                DeviceLightRuntimeContract.Action.STATUS_GET to
                    DeviceLightRuntimeFixtures.status(),
                DeviceLightRuntimeContract.Action.MANUAL_SET to
                    DeviceLightRuntimeFixtures.manualClear()
            )
        )
        val repository = DeviceLightRuntimeRepository(gateway)
        repository.beginGeneration(DEVICE_UID, RUNTIME_GENERATION)
        repository.requestStatus(DEVICE_UID).requireSuccess()

        repository.clearManual(DEVICE_UID, channelKeys = listOf("white")).requireSuccess()

        assertEquals(
            listOf(
                DeviceLightRuntimeContract.Action.STATUS_GET,
                DeviceLightRuntimeContract.Action.MANUAL_SET
            ),
            gateway.actions
        )
        val payload = gateway.payloads[1]
        assertEquals(setOf("clear", "channels"), payload.keySet())
        assertTrue(payload.getBoolean("clear"))
        val channel = payload.getJSONArray("channels").getJSONObject(0)
        assertEquals(setOf("channelKey"), channel.keySet())
        assertEquals("white", channel.getString("channelKey"))
    }

    @Test
    fun `typed command event reduction is idempotent and device isolated`() = runBlocking {
        val gateway = QueueGateway(
            mutableMapOf(
                DeviceLightRuntimeContract.Action.STATUS_GET to DeviceLightRuntimeFixtures.status()
            )
        )
        val stateStore = DeviceLightRuntimeStateStore()
        val repository = DeviceLightRuntimeRepository(gateway, stateStore)
        val reducer = DeviceLightTypedEventReducer(stateStore)
        repository.requestStatus(DEVICE_UID).requireSuccess()
        val event = manualChangedEvent()

        assertEquals(DeviceLightEventApplyResult.Applied, reducer.apply(event))
        assertEquals(DeviceLightEventApplyResult.Applied, reducer.apply(event))
        assertEquals(25.0, repository.states.value[DEVICE_UID]?.channels?.single()?.percentManual)
        assertEquals(null, repository.states.value[DeviceUid("another-device")])
    }

    @Test
    fun `mutation parser rejects partial persistence and count echoes`() {
        val invalidPersistence = DeviceLightRuntimeFixtures.regime().put("saved", false)
        assertTrue(
            runCatching { DeviceLightMutationParser.parseChannelRegime(invalidPersistence) }
                .isFailure
        )
        val invalidCount = DeviceLightRuntimeFixtures.manual().put("affectedChannelCount", 2)
        assertTrue(runCatching { DeviceLightMutationParser.parseManual(invalidCount) }.isFailure)
    }

    private class QueueGateway(
        private val responses: MutableMap<String, JSONObject>
    ) : DeviceRuntimeCommandGateway {
        val actions = mutableListOf<String>()
        val payloads = mutableListOf<JSONObject>()

        override suspend fun <T> execute(
            deviceUid: DeviceUid,
            command: DeviceRuntimeCommand<T>,
            timeoutMillis: Long
        ): DeviceRuntimeCommandOutcome<T> {
            actions += command.action
            payloads += command.encodeData()
            val response = AqlWsIncomingMessage.Response(
                id = "response-${actions.size}",
                type = "res",
                module = command.module,
                action = command.action,
                data = requireNotNull(responses[command.action]),
                ok = true,
                statusCode = if (
                    command.action == DeviceLightRuntimeContract.Action.PROGRAM_APPLY
                ) 201 else 200
            )
            return DeviceRuntimeCommandOutcome.Success(
                deviceUid = deviceUid,
                module = command.module,
                action = command.action,
                messageId = response.id,
                generation = RUNTIME_GENERATION,
                statusCode = response.statusCode,
                value = command.parseSuccess(response)
            )
        }
    }

    private companion object {
        val DEVICE_UID = DeviceUid("AQL-LIGHT-DEVICE")
        val RUNTIME_GENERATION = DeviceRuntimeConnectionGeneration(1L)
        val EXPECTED_ACTIONS = listOf(
            DeviceLightRuntimeContract.Action.STATUS_GET,
            DeviceLightRuntimeContract.Action.MANUAL_SET,
            DeviceLightRuntimeContract.Action.CHANNEL_REGIME_SET,
            DeviceLightRuntimeContract.Action.PROGRAM_APPLY,
            DeviceLightRuntimeContract.Action.PROGRAM_DELETE
        )

        fun exactResponses(): MutableMap<String, JSONObject> = mutableMapOf(
            DeviceLightRuntimeContract.Action.STATUS_GET to DeviceLightRuntimeFixtures.status(),
            DeviceLightRuntimeContract.Action.MANUAL_SET to DeviceLightRuntimeFixtures.manual(),
            DeviceLightRuntimeContract.Action.CHANNEL_REGIME_SET to
                DeviceLightRuntimeFixtures.regime(),
            DeviceLightRuntimeContract.Action.PROGRAM_APPLY to
                DeviceLightRuntimeFixtures.programApply(),
            DeviceLightRuntimeContract.Action.PROGRAM_DELETE to
                DeviceLightRuntimeFixtures.programDelete()
        )

        fun manualChangedEvent(): DeviceRuntimeTypedEvent = DeviceRuntimeTypedEvent(
            deviceUid = DEVICE_UID,
            generation = RUNTIME_GENERATION,
            messageId = "event-1",
            type = DeviceRuntimeTypedEvent.Type.LIGHT_STATUS_CHANGED,
            payload = DeviceRuntimeEventPayload.CommandResult(
                commandId = "command-1",
                commandModule = DeviceLightRuntimeContract.MODULE,
                commandAction = DeviceLightRuntimeContract.Action.MANUAL_SET,
                sessionId = "session-1",
                publishedAtMillis = 1L,
                result = DeviceLightRuntimeFixtures.manual()
            )
        )

        fun <T> DeviceRuntimeCommandOutcome<T>.requireSuccess(): T =
            (this as DeviceRuntimeCommandOutcome.Success).value
    }
}
