package com.aqua.aqualight.data.devices.runtime.modules.light

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
