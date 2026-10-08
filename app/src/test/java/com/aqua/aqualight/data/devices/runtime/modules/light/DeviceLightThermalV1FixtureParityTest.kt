package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.application.devices.light.system.DeviceLightTemperatureSensorState
import com.aqua.aqualight.data.devices.light.system.classifyLightSystemSensorState
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceLightThermalV1FixtureParityTest {
    @Test
    fun `WRGB thermal serializer matches the golden command contract`() {
        val fixture = resourceJson(FIXTURE)
        val commands = fixture.getJSONObject("commands")
        val payload = DeviceLightThermalConfigApplyPayload(
            mode = DeviceLightThermalMode.AUTO,
            minTemperatureC = 35.0,
            maxTemperatureC = 45.0,
            save = true
        ).toJson()

        assertEquals(DeviceLightThermalV1Contract.SCHEMA, fixture.getString("schema"))
        assertEquals(
            DeviceLightThermalV1Contract.PRODUCT_KEY,
            fixture.getJSONObject("product").getString("productKey")
        )
        assertEquals(
            setOf("mode", "minTemperatureC", "maxTemperatureC", "save"),
            payload.keySetExact()
        )
        assertEquals(
            commands.getJSONArray("light.thermal.config.apply").asStringSet(),
            payload.keySetExact()
        )
        assertEquals(
            setOf(
                DeviceLightThermalV1Contract.Event.STATUS_CHANGED,
                DeviceLightThermalV1Contract.Event.TELEMETRY_CHANGED
            ),
            fixture.getJSONArray("events").asStringSet()
        )
    }

    @Test
    fun `WRGB system sensor state uses only evidence available in v1`() {
        assertSensorState(
            expected = DeviceLightTemperatureSensorState.NOT_DETECTED,
            firmwareUptimeMs = 5_000L,
            temperature = temperature(
                sensorIndex = -1,
                readingValid = false,
                temperatureC = null,
                sampledAtMs = 0L
            ),
            failSafeActive = true
        )
        assertSensorState(
            expected = DeviceLightTemperatureSensorState.INVALID_READING,
            firmwareUptimeMs = 5_000L,
            temperature = temperature(
                sensorIndex = 0,
                readingValid = false,
                temperatureC = null,
                sampledAtMs = 5_000L
            ),
            failSafeActive = true
        )
        assertSensorState(
            expected = DeviceLightTemperatureSensorState.STALE_READING,
            firmwareUptimeMs = 20_001L,
            temperature = temperature(
                sensorIndex = 0,
                readingValid = true,
                temperatureC = 30.0,
                sampledAtMs = 10_000L
            ),
            failSafeActive = true
        )
        assertSensorState(
            expected = DeviceLightTemperatureSensorState.HEALTHY,
            firmwareUptimeMs = 15_000L,
            temperature = temperature(
                sensorIndex = 0,
                readingValid = true,
                temperatureC = 30.0,
                sampledAtMs = 10_000L
            ),
            failSafeActive = false
        )
    }

    @Test
    fun `WRGB thermal config rejects aliases and unsafe limits`() {
        assertTrue(
            runCatching {
                DeviceLightThermalConfigApplyPayload(
                    minTemperatureC = 45.0,
                    maxTemperatureC = 45.0
                )
            }.isFailure
        )
        assertTrue(runCatching { DeviceLightThermalConfigApplyPayload() }.isFailure)
    }

    private fun assertSensorState(
        expected: DeviceLightTemperatureSensorState,
        firmwareUptimeMs: Long,
        temperature: DeviceLightThermalTemperature,
        failSafeActive: Boolean
    ) {
        assertEquals(
            expected,
            classifyLightSystemSensorState(
                firmwareUptimeMs = firmwareUptimeMs,
                temperature = temperature,
                failSafeActive = failSafeActive
            )
        )
    }

    private fun temperature(
        sensorIndex: Int,
        readingValid: Boolean,
        temperatureC: Double?,
        sampledAtMs: Long
    ) = DeviceLightThermalTemperature(
        sensorKey = DeviceLightThermalV1Contract.FIXTURE_SENSOR_KEY,
        sensorIndex = sensorIndex,
        readingValid = readingValid,
        temperatureC = temperatureC,
        sampledAtMs = sampledAtMs
    )

    private fun resourceJson(name: String): JSONObject = JSONObject(
        requireNotNull(javaClass.classLoader?.getResourceAsStream(name)) {
            "Missing fixture resource: $name"
        }.use { stream -> stream.readBytes().toString(Charsets.UTF_8) }
    )

    private fun JSONObject.keySetExact(): Set<String> =
        keys().asSequence().toCollection(linkedSetOf())

    private fun JSONArray.asStringSet(): Set<String> = (0 until length())
        .mapTo(linkedSetOf()) { index -> getString(index) }

    private companion object {
        const val FIXTURE = "aql_light_thermal_contract_v1.json"
    }
}
