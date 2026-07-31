package com.aqua.aqualight.data.devices.runtime.modules.light

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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceLightRuntimeContractTest {

    @Test
    fun `manual and program requests emit canonical v1 fields only`() {
        val manualChannel = DeviceLightManualChannelPayload(" White ", 42.5)
        assertEquals("white", manualChannel.canonicalChannelKey)
        assertEquals(
            setOf("channelKey", "percent"),
            manualChannel.toJson(clear = false).keys().asSequence().toSet()
        )

        val clear = DeviceLightManualSetPayload(
            channels = listOf(manualChannel),
            durationMs = null,
            clear = true
        ).toJson()
        assertEquals(setOf("clear", "channels"), clear.keys().asSequence().toSet())
        assertEquals(
            setOf("channelKey"),
            clear.getJSONArray("channels").getJSONObject(0).keys().asSequence().toSet()
        )

        val program = DeviceLightProgramApplyPayload(
            channelKey = "WHITE",
            points = listOf(DeviceLightProgramPointPayload(timeMs = 3_661_007L, percent = 33.0)),
            save = true
        ).toJson()
        val point = program.getJSONArray("points").getJSONObject(0)
        assertEquals(setOf("timeMs", "percent"), point.keys().asSequence().toSet())
        assertFalse(point.has("time"))
        assertFalse(point.has("value"))
        assertFalse(program.has("index"))
    }

    @Test
    fun `request models reject duplicates invalid channel keys non finite values and day overflow`() {
        assertFails {
            DeviceLightManualSetPayload(
                channels = listOf(
                    DeviceLightManualChannelPayload("white", 10.0),
                    DeviceLightManualChannelPayload(" WHITE ", 20.0)
                ),
                clear = false
            )
        }
        assertFails { DeviceLightManualChannelPayload("none", 10.0) }
        assertFails { DeviceLightManualChannelPayload("white", Double.NaN) }
        assertFails {
            DeviceLightManualSetPayload(
                channels = listOf(DeviceLightManualChannelPayload("white", 10.0)),
                durationMs = 1_000L,
                clear = true
            )
        }
        assertFails {
            DeviceLightProgramPointPayload(
                timeMs = DeviceLightRuntimeContract.Limit.MILLIS_IN_DAY,
                percent = 10.0
            )
        }
    }

    @Test
    fun `status parser validates full channel program and runtime graph`() {
        val status = DeviceLightStatusParser.parse(statusJson())

        assertEquals("AllCh", status.channelEdit)
        assertEquals(1, status.channelCount)
        assertEquals(1, status.programCount)
        assertEquals(DeviceLightRegime.AUTO, status.channels.single().regime)
        assertEquals("12:00", status.programs.single().points[1].time)
    }

    @Test
    fun `status parser rejects wrapper unknown type count binding percent and time mismatches`() {
        val invalid = listOf(
            JSONObject().put("status", statusJson()),
            statusJson().put("legacy", true),
            statusJson().put("channelCount", "1"),
            statusJson().put("programCount", 2),
            statusJson().apply {
                getJSONArray("programs").getJSONObject(0).put("channelKey", "blue")
            },
            statusJson().apply {
                getJSONArray("channels").getJSONObject(0).put("percentNow", 49.0)
            },
            statusJson().apply {
                getJSONArray("programs").getJSONObject(0)
                    .getJSONArray("points").getJSONObject(1).put("time", "12:00:00")
            }
        )
        invalid.forEach { value -> assertFails { DeviceLightStatusParser.parse(value) } }
    }

    @Test
    fun `mutation parsers enforce persistence route and snapshot-specific key sets`() {
        val manual = DeviceLightStatusParser.parseManualSetResult(manualResultJson())
        assertTrue(manual.manualActive)
        assertFalse(manual.saved)
        assertEquals(1, manual.affectedChannelCount)

        val regime = DeviceLightStatusParser.parseChannelRegimeSetResult(regimeResultJson())
        assertEquals(DeviceLightRegime.ON, regime.regime)
        assertTrue(regime.saved)

        val created = DeviceLightStatusParser.parseProgramApplyResult(
            programApplyResultJson(created = true),
            statusCode = 201
        )
        assertTrue(created.created)
        assertEquals(2, created.program.points.size)

        val deleted = DeviceLightStatusParser.parseProgramDeleteResult(programDeleteResultJson())
        assertTrue(deleted.deleted)
        assertEquals(0, deleted.programCount)

        assertFails {
            DeviceLightStatusParser.parseProgramApplyResult(
                programApplyResultJson(created = true),
                statusCode = 200
            )
        }
        assertFails {
            DeviceLightStatusParser.parseChannelRegimeSetResult(
                regimeResultJson().put("saved", false)
            )
        }
    }

    @Test
    fun `temperature protection supports exact nullable unsupported status and saved set result`() {
        val unsupported = DeviceLightTemperatureProtectionParser
            .parseStatus(temperatureStatusJson(supported = false))
            .getOrThrow()
        assertFalse(unsupported.supported)
        assertNull(unsupported.temperatureProtection.thresholdC)

        val supported = DeviceLightTemperatureProtectionParser
            .parseStatus(temperatureStatusJson(supported = true))
            .getOrThrow()
        assertEquals(60.0, supported.temperatureProtection.thresholdC!!, 0.0)

        val result = DeviceLightTemperatureProtectionParser
            .parseSetResult(temperatureSetResultJson())
            .getOrThrow()
        assertTrue(result.saved)
        assertTrue(result.status.supported)
    }

    @Test
    fun `typed repositories execute all seven firmware light actions`() = runBlocking {
        val gateway = RespondingGateway()
        val light = DeviceLightRuntimeRepository(gateway)
        val protection = DeviceLightTemperatureProtectionRuntimeRepository(gateway)

        assertSuccess(light.requestStatus(DEVICE_UID))
        assertSuccess(
            light.setManual(
                DEVICE_UID,
                DeviceLightManualSetPayload(
                    channels = listOf(DeviceLightManualChannelPayload("white", 50.0)),
                    durationMs = 900_000L
                )
            )
        )
        assertSuccess(
            light.setChannelRegime(
                DEVICE_UID,
                DeviceLightChannelRegimeSetPayload("white", DeviceLightRegime.ON)
            )
        )
        assertSuccess(
            light.applyProgram(
                DEVICE_UID,
                DeviceLightProgramApplyPayload(
                    channelKey = "white",
                    points = listOf(
                        DeviceLightProgramPointPayload(0L, 0.0),
                        DeviceLightProgramPointPayload(43_200_000L, 100.0)
                    )
                )
            )
        )
        assertSuccess(light.deleteProgram(DEVICE_UID, programIndex = 0))
        assertSuccess(protection.requestStatus(DEVICE_UID))
        assertSuccess(
            protection.setThreshold(
                DEVICE_UID,
                DeviceLightTemperatureProtectionSetPayload(62.0, save = true)
            )
        )

        assertEquals(
            listOf(
                "status.get",
                "manual.set",
                "channel.regime.set",
                "program.apply",
                "program.delete",
                "temperature-protection.status.get",
                "temperature-protection.set"
            ),
            gateway.actions
        )
        assertEquals(0, gateway.requests.first().length())
        assertEquals(
            setOf("thresholdC", "save"),
            gateway.requests.last().keys().asSequence().toSet()
        )
    }

    private fun assertSuccess(outcome: DeviceRuntimeCommandOutcome<*>) {
        assertTrue(outcome is DeviceRuntimeCommandOutcome.Success)
    }

    private class RespondingGateway : DeviceRuntimeCommandGateway {
        val actions = mutableListOf<String>()
        val requests = mutableListOf<JSONObject>()

        override suspend fun <T> execute(
            deviceUid: DeviceUid,
            command: DeviceRuntimeCommand<T>,
            timeoutMillis: Long
        ): DeviceRuntimeCommandOutcome<T> {
            val request = command.encodeData()
            actions += command.action
            requests += request
            val responseData = when (command.action) {
                "status.get" -> statusJson()
                "manual.set" -> manualResultJson()
                "channel.regime.set" -> regimeResultJson()
                "program.apply" -> programApplyResultJson(created = !request.has("programIndex"))
                "program.delete" -> programDeleteResultJson()
                "temperature-protection.status.get" -> temperatureStatusJson(supported = true)
                "temperature-protection.set" -> temperatureSetResultJson()
                else -> error("Unexpected Light action ${command.action}")
            }
            val statusCode = if (command.action == "program.apply" && !request.has("programIndex")) {
                201
            } else {
                200
            }
            val response = AqlWsIncomingMessage.Response(
                id = "light-${command.action}",
                type = "res",
                module = command.module,
                action = command.action,
                data = responseData,
                ok = true,
                statusCode = statusCode
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
        .put("channelEdit", "AllCh")
        .put("powerLimitW", 100.0)
        .put("lockLoop", false)
        .put("temperatureDownStepPercent", 70.0)
        .put("temperatureRecoveryMs", 60_000)
        .put("lightCorrectionFactor", 1.0)
        .put("uptimeMs", 120_000)
        .put("channels", JSONArray().put(channelJson(manualPercent = null)))
        .put(
            "programs",
            JSONArray().put(
                JSONObject()
                    .put("index", 0)
                    .put("channelKey", "white")
                    .put("bound", true)
                    .put("pointCount", 2)
                    .put(
                        "points",
                        JSONArray()
                            .put(pointJson(0L, 0.0, includeIndex = false, index = 0))
                            .put(pointJson(43_200_000L, 100.0, includeIndex = false, index = 1))
                    )
            )
        )
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

    private fun channelJson(
        manualPercent: Double?,
        regime: String = "Auto",
        includeListIndex: Boolean = false
    ): JSONObject {
        val manualValue = manualPercent?.div(100.0) ?: -1.0
        return JSONObject()
            .put("index", 0)
            .put("key", "white")
            .put("name", "White")
            .put("displayName", "White")
            .put("profileManaged", true)
            .put("regime", regime)
            .put("channelKind", "gpio")
            .put("gpio", 5)
            .put("ledcChannel", 0)
            .put("group", -1)
            .put("valueNow", manualValue.takeIf { it >= 0.0 } ?: 0.5)
            .put("valueAuto", 0.5)
            .put("valueManual", manualValue)
            .put("manualTimeoutMs", if (manualPercent == null) 0 else 900_000)
            .put("percentNow", manualPercent ?: 50.0)
            .put("percentAuto", 50.0)
            .put("percentManual", manualPercent ?: -100.0)
            .put("invert", false)
            .put("pwmResolutionBits", 12)
            .put("pwmFrequencyHz", 1_000)
            .put("color", 16_777_215)
            .put("lumen", 0.0)
            .put("lux", 0.0)
            .put("watt", 0.0)
            .put(
                "editable",
                JSONObject()
                    .put("hardware", false)
                    .put("displayName", false)
                    .put("color", false)
                    .put("hardwareCalibration", false)
            )
            .apply { if (includeListIndex) put("listIndex", 0) }
    }

    private fun pointJson(
        timeMs: Long,
        percent: Double,
        includeIndex: Boolean,
        index: Int
    ): JSONObject = JSONObject()
        .put("timeMs", timeMs)
        .put("time", if (timeMs == 0L) "00:00" else "12:00")
        .put("value", percent / 100.0)
        .put("percent", percent)
        .apply { if (includeIndex) put("index", index) }

    private fun manualResultJson(): JSONObject = JSONObject()
        .put("operation", "manualState")
        .put("manualActive", true)
        .put("durationMs", 900_000)
        .put("runtimeTransport", "websocket")
        .put("command", "light.manual.set")
        .put("event", "light.status.changed")
        .put("channels", JSONArray().put(channelJson(50.0, includeListIndex = true)))
        .put("affectedChannelCount", 1)
        .put("saved", false)

    private fun regimeResultJson(): JSONObject = JSONObject()
        .put("operation", "channelRegimeSet")
        .put("changed", true)
        .put("saved", true)
        .put("saveRequested", true)
        .put("channelKey", "white")
        .put("regime", "On")
        .put("runtimeTransport", "websocket")
        .put("command", "light.channel.regime.set")
        .put("event", "light.status.changed")
        .put("channel", channelJson(null, regime = "On", includeListIndex = true))

    private fun programApplyResultJson(created: Boolean): JSONObject = JSONObject()
        .put("operation", "programApply")
        .put("created", created)
        .put("changed", true)
        .put("saved", true)
        .put("saveRequested", true)
        .put("programIndex", 0)
        .put("channelKey", "white")
        .put("channelListIndex", 0)
        .put("runtimeTransport", "websocket")
        .put("command", "light.program.apply")
        .put("event", "light.status.changed")
        .put(
            "program",
            JSONObject()
                .put("listIndex", 0)
                .put("index", 0)
                .put("channelKey", "white")
                .put("bound", true)
                .put("pointCount", 2)
                .put(
                    "points",
                    JSONArray()
                        .put(pointJson(0L, 0.0, includeIndex = true, index = 0))
                        .put(pointJson(43_200_000L, 100.0, includeIndex = true, index = 1))
                )
        )

    private fun programDeleteResultJson(): JSONObject = JSONObject()
        .put("operation", "programDelete")
        .put("deleted", true)
        .put("changed", true)
        .put("saved", true)
        .put("saveRequested", true)
        .put("programIndex", 0)
        .put("deletedListIndex", 0)
        .put("channelKey", "white")
        .put("deletedPointCount", 2)
        .put("programCount", 0)
        .put("runtimeTransport", "websocket")
        .put("command", "light.program.delete")
        .put("event", "light.status.changed")

    private fun temperatureStatusJson(supported: Boolean): JSONObject = JSONObject()
        .put("supported", supported)
        .put(
            "temperatureProtection",
            JSONObject()
                .put("supported", supported)
                .put("active", false)
                .put("thresholdEditable", supported)
                .put("thresholdC", if (supported) 60.0 else JSONObject.NULL)
                .put("minimumC", if (supported) 50.0 else JSONObject.NULL)
                .put("maximumC", if (supported) 70.0 else JSONObject.NULL)
        )
        .put(
            "runtime",
            JSONObject()
                .put("module", "light")
                .put("readOnly", false)
                .put("supportsStatusGet", true)
                .put("supportsSet", supported)
                .put("event", "light.status.changed")
        )

    private fun temperatureSetResultJson(): JSONObject = JSONObject()
        .put("operation", "temperatureProtectionSet")
        .put("changed", true)
        .put("saved", true)
        .put("saveRequested", true)
        .put("runtimeTransport", "websocket")
        .put("command", "light.temperature-protection.set")
        .put("event", "light.status.changed")
        .put("status", temperatureStatusJson(supported = true))

    private fun assertFails(block: () -> Unit) {
        assertTrue(runCatching(block).isFailure)
    }

    private companion object {
        val DEVICE_UID = DeviceUid("AQL-LIGHT-000001")
        val GENERATION = DeviceRuntimeConnectionGeneration(13L)
    }
}
