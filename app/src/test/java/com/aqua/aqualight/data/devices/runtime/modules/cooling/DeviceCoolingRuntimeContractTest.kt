package com.aqua.aqualight.data.devices.runtime.modules.cooling

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCoolingRuntimeContractTest {
    @Test
    fun `status parser accepts exact firmware status and temperature`() {
        val status = DeviceCoolingStatusParser.parse(DeviceCoolingRuntimeFixtures.status())

        assertEquals(DeviceCoolingMode.AUTO, status.mode)
        assertEquals(1, status.fanOutputCount)
        assertEquals(27.4, status.temperature.temperatureC!!, 0.0001)
        assertTrue(status.temperature.readingValid)
        assertEquals(listOf(0), status.rules.single().sensorBindings)
        assertTrue(status.runtime.supportsFanDisplayName)
    }

    @Test
    fun `temperature parser accepts completed invalid physical sample`() {
        val parsed = DeviceCoolingTemperatureParser.parse(
            DeviceCoolingRuntimeFixtures.temperature(
                temperatureC = null,
                sensorIndex = 0,
                sampledAtMs = 12_000L
            )
        )

        assertFalse(parsed.readingValid)
        assertNull(parsed.temperatureC)
        assertEquals(0, parsed.sensorIndex)
        assertEquals(12_000L, parsed.sampledAtMs)
    }

    @Test
    fun `status parser rejects extra fields and mode aliases`() {
        val extra = DeviceCoolingRuntimeFixtures.status().put("unexpected", true)
        val alias = DeviceCoolingRuntimeFixtures.status().put("mode", "auto")

        assertTrue(runCatching { DeviceCoolingStatusParser.parse(extra) }.isFailure)
        assertTrue(runCatching { DeviceCoolingStatusParser.parse(alias) }.isFailure)
    }

    @Test
    fun `temperature parser rejects validity and nullability mismatch`() {
        val invalid = DeviceCoolingRuntimeFixtures.temperature(null)
            .put("readingValid", true)

        assertTrue(runCatching { DeviceCoolingTemperatureParser.parse(invalid) }.isFailure)
    }

    @Test
    fun `temperature parser rejects firmware sentinel boundaries`() {
        listOf(
            COOLING_MIN_VALID_TEMPERATURE_C,
            COOLING_MAX_VALID_TEMPERATURE_C
        ).forEach { sentinel ->
            val snapshot = DeviceCoolingRuntimeFixtures.temperature(temperatureC = sentinel)
            assertTrue(runCatching { DeviceCoolingTemperatureParser.parse(snapshot) }.isFailure)
        }
    }

    @Test
    fun `temperature parser rejects uptime wider than firmware unsigned long`() {
        val snapshot = DeviceCoolingRuntimeFixtures.temperature(
            sampledAtMs = COOLING_DEVICE_UPTIME_MAX_MS + 1L
        )

        assertTrue(runCatching { DeviceCoolingTemperatureParser.parse(snapshot) }.isFailure)
    }

    @Test
    fun `config payload emits canonical exact fields`() {
        val payload = DeviceCoolingConfigApplyPayload(
            mode = DeviceCoolingMode.ON,
            minTemperatureC = 29.0,
            maxTemperatureC = 36.0,
            fans = listOf(DeviceCoolingFanDisplayNamePayload(" FAN1 ", " Sol Fan ")),
            save = true
        ).toJson()

        assertEquals(
            setOf("mode", "minTemperatureC", "maxTemperatureC", "fans", "save"),
            payload.keys().asSequence().toSet()
        )
        assertEquals("On", payload.getString("mode"))
        val fan = payload.getJSONArray("fans").getJSONObject(0)
        assertEquals(setOf("fanKey", "displayName"), fan.keys().asSequence().toSet())
        assertEquals("fan1", fan.getString("fanKey"))
        assertEquals("Sol Fan", fan.getString("displayName"))
    }

    @Test
    fun `blank display name is encoded as JSON null`() {
        val payload = DeviceCoolingConfigApplyPayload(
            fans = listOf(DeviceCoolingFanDisplayNamePayload("fan1", "   "))
        ).toJson()

        assertTrue(payload.getJSONArray("fans").getJSONObject(0).isNull("displayName"))
    }

    @Test
    fun `firmware telemetry fixture keeps exact field order and command count`() {
        val fixture = JSONObject(readFirmwareTelemetryFixture())

        assertEquals(44, fixture.getInt("commandCount"))
        assertEquals("temperature.changed", fixture.getString("event"))
        assertEquals(
            listOf("sensorIndex", "readingValid", "temperatureC", "sampledAtMs"),
            List(fixture.getJSONArray("exactFields").length()) { index ->
                fixture.getJSONArray("exactFields").getString(index)
            }
        )
    }
}

private const val FIRMWARE_TELEMETRY_FIXTURE =
    "protocol/fixtures/aql_cooling_temperature_telemetry_v1.json"

private fun readFirmwareTelemetryFixture(): String {
    val workingDirectory = File(System.getProperty("user.dir")).canonicalFile
    val fixture = generateSequence(workingDirectory) { directory -> directory.parentFile }
        .map { directory -> directory.resolve(FIRMWARE_TELEMETRY_FIXTURE) }
        .firstOrNull(File::isFile)
    return requireNotNull(fixture) {
        "Missing firmware telemetry fixture: $FIRMWARE_TELEMETRY_FIXTURE"
    }.readText(Charsets.UTF_8)
}
