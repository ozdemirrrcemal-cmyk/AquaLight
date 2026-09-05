package com.aqua.aqualight.data.devices.runtime.modules.cooling.v1

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCoolingV1FixtureParityTest {
    @Test
    fun `request serializers match every golden Cooling V1 command`() {
        val fixture = resourceJson(CONTRACT_FIXTURE)
        val commands = fixture.getJSONObject("commands")
        val slot = DeviceCoolingV1ProgramSlotPayload(
            startMinute = 480,
            endMinute = 600,
            fanOnTemperatureC = 25.0,
            fanPercent = 50.0
        )
        val actual = linkedMapOf(
            "cooling.status.get" to emptySet(),
            "cooling.config.apply" to DeviceCoolingV1ConfigApplyPayload(
                expectedConfigRevision = 1L,
                controlMode = DeviceCoolingV1ControlMode.AUTOMATIC,
                startTemperatureC = 25.5,
                fullSpeedTemperatureC = 30.0,
                silentModeEnabled = true
            ).toJson().keySetExact(),
            "cooling.manual.apply" to DeviceCoolingV1ManualApplyPayload(
                expectedConfigRevision = 1L,
                targetPercent = 50.0
            ).toJson().keySetExact(),
            "cooling.program.get" to emptySet(),
            "cooling.program.apply" to DeviceCoolingV1ProgramApplyPayload(
                expectedProgramRevision = 1L,
                slots = listOf(slot)
            ).toJson().keySetExact(),
            "cooling.history.get" to DeviceCoolingV1HistoryGetPayload(
                DeviceCoolingV1HistoryRange.HOURS_24
            ).toJson().keySetExact()
        )

        assertEquals(DeviceCoolingV1Contract.SCHEMA, fixture.getString("schema"))
        assertEquals(
            DeviceCoolingV1Contract.PRODUCT_KEY,
            fixture.getJSONObject("product").getString("productKey")
        )
        assertEquals(commands.keySetExact(), actual.keys)
        actual.forEach { (command, fields) ->
            assertEquals(command, commands.getJSONArray(command).asStringSet(), fields)
        }
        assertEquals(
            setOf("startMinute", "endMinute", "fanOnTemperatureC", "fanPercent"),
            slot.toJson().keySetExact()
        )
    }

    @Test
    fun `firmware fixture command event and error catalogs mirror Android contract`() {
        val fixture = resourceJson(CONTRACT_FIXTURE)
        val androidCommands = setOf(
            coolingCommand(DeviceCoolingV1Contract.Action.STATUS_GET),
            coolingCommand(DeviceCoolingV1Contract.Action.CONFIG_APPLY),
            coolingCommand(DeviceCoolingV1Contract.Action.MANUAL_APPLY),
            coolingCommand(DeviceCoolingV1Contract.Action.PROGRAM_GET),
            coolingCommand(DeviceCoolingV1Contract.Action.PROGRAM_APPLY),
            coolingCommand(DeviceCoolingV1Contract.Action.HISTORY_GET)
        )
        val androidEvents = setOf(
            DeviceCoolingV1Contract.Event.STATUS_CHANGED,
            DeviceCoolingV1Contract.Event.TELEMETRY_CHANGED
        )
        val androidErrors = setOf(
            DeviceCoolingV1Contract.Error.BAD_REQUEST,
            DeviceCoolingV1Contract.Error.MISSING_FIELD,
            DeviceCoolingV1Contract.Error.INVALID_VALUE,
            DeviceCoolingV1Contract.Error.CONFLICT,
            DeviceCoolingV1Contract.Error.HARDWARE_ERROR,
            DeviceCoolingV1Contract.Error.STORAGE_ERROR,
            DeviceCoolingV1Contract.Error.CLOCK_UNSYNCED
        )

        assertEquals(androidCommands, fixture.getJSONObject("commands").keySetExact())
        assertEquals(androidEvents, fixture.getJSONArray("events").asStringSet())
        assertEquals(androidErrors, fixture.getJSONArray("errors").asStringSet())
    }

    @Test
    fun `telemetry fixture freezes the direct event root`() {
        val fixture = resourceJson(TELEMETRY_FIXTURE)

        assertEquals(50, fixture.getInt("commandCount"))
        assertEquals(DeviceCoolingV1Contract.Event.TELEMETRY_CHANGED, fixture.getString("event"))
        assertEquals(
            setOf("water", "ambient"),
            fixture.getJSONArray("sensorKeys").asStringSet()
        )
        assertEquals(
            setOf(DeviceCoolingV1Contract.FAN_KEY),
            fixture.getJSONArray("fanKeys").asStringSet()
        )
        assertEquals(23, fixture.getJSONArray("requiredRootFields").length())
    }

    @Test
    fun `program and step rules fail closed before transport`() {
        assertTrue(
            runCatching {
                DeviceCoolingV1ProgramSlotPayload(1, 16, 25.0, 50.0)
            }.isFailure
        )
        assertTrue(
            runCatching {
                DeviceCoolingV1ProgramApplyPayload(
                    expectedProgramRevision = 1L,
                    slots = listOf(
                        DeviceCoolingV1ProgramSlotPayload(0, 60, 25.0, 50.0),
                        DeviceCoolingV1ProgramSlotPayload(30, 90, 25.0, 50.0)
                    )
                )
            }.isFailure
        )
        assertTrue(
            runCatching {
                DeviceCoolingV1ManualApplyPayload(
                    expectedConfigRevision = 1L,
                    targetPercent = 50.5
                )
            }.isFailure
        )
    }

    private fun resourceJson(name: String): JSONObject = JSONObject(
        requireNotNull(javaClass.classLoader?.getResourceAsStream(name)) {
            "Missing fixture resource: $name"
        }.use { stream -> stream.readBytes().toString(Charsets.UTF_8) }
    )

    private fun coolingCommand(action: String): String = "cooling.$action"

    private fun JSONObject.keySetExact(): Set<String> =
        keys().asSequence().toCollection(linkedSetOf())

    private fun JSONArray.asStringSet(): Set<String> = (0 until length())
        .mapTo(linkedSetOf()) { index -> getString(index) }

    private companion object {
        const val CONTRACT_FIXTURE = "aql_cooling_contract_v1.json"
        const val TELEMETRY_FIXTURE = "aql_cooling_telemetry_v1.json"
    }
}
