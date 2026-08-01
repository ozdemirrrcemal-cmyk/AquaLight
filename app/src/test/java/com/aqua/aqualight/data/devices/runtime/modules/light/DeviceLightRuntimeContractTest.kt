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
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceLightRuntimeContractTest {

    @Test
    fun `status parser accepts exact firmware shape and rejects drift`() {
        val parsed = DeviceLightStatusParser.parse(statusJson())

        assertEquals(1, parsed.channelCount)
        assertEquals("white", parsed.channels.single().key)
        assertEquals(DeviceLightRegime.AUTO, parsed.channels.single().regime)
        assertEquals(1, parsed.programCount)
        assertTrue(parsed.runtime.supportsManualSet)

        assertTrue(runCatching { DeviceLightStatusParser.parse(statusJson().put("extra", 1)) }.isFailure)
        val unknownRegime = statusJson().also { status ->
            status.getJSONArray("channels").getJSONObject(0).put("regime", "automatic")
        }
        assertTrue(runCatching { DeviceLightStatusParser.parse(unknownRegime) }.isFailure)
    }

    @Test
    fun `repository sends exact actions and parses correlated mutation results`() = runBlocking {
        val gateway = QueueGateway(
            mutableMapOf(
                DeviceLightRuntimeContract.Action.STATUS_GET to statusJson(),
                DeviceLightRuntimeContract.Action.MANUAL_SET to manualResult(),
                DeviceLightRuntimeContract.Action.CHANNEL_REGIME_SET to regimeResult(),
                DeviceLightRuntimeContract.Action.PROGRAM_APPLY to programApplyResult(),
                DeviceLightRuntimeContract.Action.PROGRAM_DELETE to programDeleteResult()
            )
        )
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

        assertEquals(
            listOf(
                DeviceLightRuntimeContract.Action.STATUS_GET,
                DeviceLightRuntimeContract.Action.MANUAL_SET,
                DeviceLightRuntimeContract.Action.CHANNEL_REGIME_SET,
                DeviceLightRuntimeContract.Action.PROGRAM_APPLY,
                DeviceLightRuntimeContract.Action.PROGRAM_DELETE
            ),
            gateway.actions
        )
        assertEquals(setOf("clear", "durationMs", "channels"), gateway.payloads[1].keySet())
        assertEquals("On", gateway.payloads[2].getString("regime"))
        assertEquals(setOf("channelKey", "points", "save"), gateway.payloads[3].keySet())
        assertEquals(1, repository.states.value[DEVICE_UID]?.programCount)
    }

    @Test
    fun `typed command event reduction is idempotent and device isolated`() = runBlocking {
        val gateway = QueueGateway(
            mutableMapOf(DeviceLightRuntimeContract.Action.STATUS_GET to statusJson())
        )
        val stateStore = DeviceLightRuntimeStateStore()
        val repository = DeviceLightRuntimeRepository(gateway, stateStore)
        val reducer = DeviceLightTypedEventReducer(stateStore)
        repository.requestStatus(DEVICE_UID).requireSuccess()

        val event = DeviceRuntimeTypedEvent(
            deviceUid = DEVICE_UID,
            generation = DeviceRuntimeConnectionGeneration(1L),
            messageId = "event-1",
            type = DeviceRuntimeTypedEvent.Type.LIGHT_STATUS_CHANGED,
            payload = DeviceRuntimeEventPayload.CommandResult(
                commandId = "command-1",
                commandModule = DeviceLightRuntimeContract.MODULE,
                commandAction = DeviceLightRuntimeContract.Action.MANUAL_SET,
                sessionId = "session-1",
                publishedAtMillis = 1L,
                result = manualResult()
            )
        )

        assertEquals(DeviceLightEventApplyResult.Applied, reducer.apply(event))
        assertEquals(DeviceLightEventApplyResult.Applied, reducer.apply(event))
        assertEquals(25.0, repository.states.value[DEVICE_UID]?.channels?.single()?.percentManual)
        assertEquals(null, repository.states.value[DeviceUid("another-device")])
    }

    @Test
    fun `mutation parser rejects partial persistence and count echoes`() {
        val invalidPersistence = regimeResult().put("saved", false)
        assertTrue(
            runCatching { DeviceLightMutationParser.parseChannelRegime(invalidPersistence) }
                .isFailure
        )

        val invalidCount = manualResult().put("affectedChannelCount", 2)
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
            val data = requireNotNull(responses[command.action])
            val response = AqlWsIncomingMessage.Response(
                id = "response-${actions.size}",
                type = "res",
                module = command.module,
                action = command.action,
                data = data,
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
                generation = DeviceRuntimeConnectionGeneration(1L),
                statusCode = response.statusCode,
                value = command.parseSuccess(response)
            )
        }
    }

    private fun statusJson(): JSONObject = JSONObject()
        .put("supported", true)
        .put("manualSupported", true)
        .put("programSupported", true)
        .put("presetsSupported", true)
        .put("simulationSupported", true)
        .put("channelCount", 1)
        .put("programCount", 1)
        .put("liveEditEnabled", true)
        .put("channelEdit", 0)
        .put("powerLimitW", 120.0)
        .put("lockLoop", false)
        .put("temperatureDownStepPercent", 10.0)
        .put("temperatureRecoveryMs", 30_000L)
        .put("lightCorrectionFactor", 1.0)
        .put("uptimeMs", 12_000L)
        .put("channels", JSONArray().put(channelJson()))
        .put("programs", JSONArray().put(programJson(mutation = false, index = 0)))
        .put(
            "runtime",
            JSONObject()
                .put("module", "light")
                .put("readOnly", false)
                .put("supportsManualSet", true)
                .put("supportsChannelRegimeSet", true)
                .put("supportsProgramApply", true)
                .put("supportsProgramDelete", true)
                .put("supportsLiveEdit", true)
                .put("event", "light.status.changed")
        )

    private fun manualResult(): JSONObject = JSONObject()
        .put("operation", "manualState")
        .put("manualActive", true)
        .put("durationMs", 60_000L)
        .put("runtimeTransport", "websocket")
        .put("command", "light.manual.set")
        .put("event", "light.status.changed")
        .put(
            "channels",
            JSONArray().put(
                channelJson(regime = "Auto", manualValue = 0.25, mutation = true)
            )
        )
        .put("affectedChannelCount", 1)
        .put("saved", false)

    private fun regimeResult(): JSONObject = JSONObject()
        .put("operation", "channelRegimeSet")n        .put("changed", true)
        .put("saved", true)
        .put("saveRequested", true)
        .put("channelKey", "white")
        .put("regime", "On")
        .put("runtimeTransport", "websocket")
        .put("command", "light.channel.regime.set")
        .put("event", "light.status.changed")
        .put("channel", channelJson(regime = "On", mutation = true))

    private fun programApplyResult(): JSONObject = JSONObject()
        .put("operation", "programApply")
        .put("created", true)
        .put("changed", true)
        .put("saved", true)
        .put("saveRequested", true)
        .put("programIndex", 1)
        .put("channelKey", "white")
        .put("channelListIndex", 0)
        .put("runtimeTransport", "websocket")
        .put("command", "light.program.apply")
        .put("event", "light.status.changed")
        .put("program", programJson(mutation = true, index = 1))

    private fun programDeleteResult(): JSONObject = JSONObject()
        .put("operation", "programDelete")
        .put("deleted", true)
        .put("changed", true)
        .put("saved", true)
        .put("saveRequested", true)
        .put("programIndex", 1)
        .put("deletedListIndex", 1)
        .put("channelKey", "white")
        .put("deletedPointCount", 1)
        .put("programCount", 1)
        .put("runtimeTransport", "websocket")
        .put("command", "light.program.delete")
        .put("event", "light.status.changed")

    private fun channelJson(
        regime: String = "Auto",
        manualValue: Double = -1.0,
        mutation: Boolean = false
    ): JSONObject = JSONObject()
        .put("index", 0)
        .put("key", "white")
        .put("name", "White")
        .put("displayName", "White")
        .put("profileManaged", true)
        .put("regime", regime)
        .put("channelKind", "gpio")
        .put("gpio", 4)
        .put("ledcChannel", 0)
        .put("group", 0)
        .put("valueNow", if (manualValue >= 0.0) manualValue else 0.5)
        .put("valueAuto", 0.5)
        .put("valueManual", manualValue)
        .put("manualTimeoutMs", if (manualValue >= 0.0) 60_000L else 0L)
        .put("percentNow", if (manualValue >= 0.0) manualValue * 100.0 else 50.0)
        .put("percentAuto", 50.0)
        .put("percentManual", manualValue * 100.0)
        .put("invert", false)
        .put("pwmResolutionBits", 12)
        .put("pwmFrequencyHz", 5_000)
        .put("color", 16_777_215)
        .put("lumen", 0.0)
        .put("lux", 0.0)
        .put("watt", 24.0)
        .put(
            "editable",
            JSONObject()
                .put("hardware", false)
                .put("displayName", false)
                .put("color", false)
                .put("hardwareCalibration", false)
        ).also { channel ->
            if (mutation) channel.put("listIndex", 0)
        }

    private fun programJson(mutation: Boolean, index: Int): JSONObject = JSONObject()
        .put("index", index)
        .put("channelKey", "white")
        .put("bound", true)
        .put("pointCount", 1)
        .put(
            "points",
            JSONArray().put(
                JSONObject()
                    .put("timeMs", 0L)
                    .put("time", "00:00:00")
                    .put("value", 0.3)
                    .put("percent", 30.0)
                    .also { point -> if (mutation) point.put("index", 0) }
            )
        ).also { program ->
            if (mutation) program.put("listIndex", index)
        }

    private fun JSONObject.keySet(): Set<String> = keys().asSequence().toSet()

    private fun <T> DeviceRuntimeCommandOutcome<T>.requireSuccess(): T =
        (this as DeviceRuntimeCommandOutcome.Success).value

    private companion object {
        val DEVICE_UID = DeviceUid("AQL-LIGHT-DEVICE")
    }
}
