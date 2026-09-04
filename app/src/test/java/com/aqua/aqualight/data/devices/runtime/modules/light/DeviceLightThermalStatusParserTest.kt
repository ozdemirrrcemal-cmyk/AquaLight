package com.aqua.aqualight.data.devices.runtime.modules.light

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceLightThermalStatusParserTest {
    @Test
    fun `parses firmware authoritative thermal status exactly`() {
        val status = DeviceLightThermalStatusParser.parse(validStatus())

        assertEquals("aql.light-thermal.v1", status.schema)
        assertEquals(1, status.schemaVersion)
        assertEquals("LIGHT_WRGB_PRO_ELITE", status.productKey)
        assertEquals(DeviceLightThermalMode.AUTO, status.config.mode)
        assertEquals(2, status.topology.fanOutputCount)
        assertEquals(1, status.topology.temperatureSensorCount)
        assertEquals(2, status.fans.size)
        assertEquals("fan1", status.fans.first().fanKey)
        assertEquals(50.0, status.fans.first().percentNow, 0.0)
        assertTrue(status.temperature.readingValid)
        assertEquals(40.0, status.temperature.temperatureC!!, 0.0)
        assertFalse(status.runtime.sensorFailSafeActive)
        assertTrue(status.runtime.automaticOutputCycleHealthy)
    }

    @Test
    fun `accepts explicit null temperature only when reading is invalid`() {
        val data = validStatus()
        data.getJSONObject("temperature")
            .put("readingValid", false)
            .put("temperatureC", JSONObject.NULL)

        val status = DeviceLightThermalStatusParser.parse(data)

        assertFalse(status.temperature.readingValid)
        assertNull(status.temperature.temperatureC)
    }

    @Test
    fun `rejects unknown top level fields`() {
        val data = validStatus().put("futureField", true)

        assertThrows(IllegalArgumentException::class.java) {
            DeviceLightThermalStatusParser.parse(data)
        }
    }

    @Test
    fun `rejects fan topology mismatch`() {
        val data = validStatus()
        data.getJSONObject("topology").put("fanOutputCount", 1)

        assertThrows(IllegalArgumentException::class.java) {
            DeviceLightThermalStatusParser.parse(data)
        }
    }

    @Test
    fun `rejects invalid temperature validity pair`() {
        val data = validStatus()
        data.getJSONObject("temperature").put("readingValid", false)

        assertThrows(IllegalArgumentException::class.java) {
            DeviceLightThermalStatusParser.parse(data)
        }
    }

    private fun validStatus(): JSONObject = JSONObject()
        .put("schema", "aql.light-thermal.v1")
        .put("schemaVersion", 1)
        .put("productKey", "LIGHT_WRGB_PRO_ELITE")
        .put("uptimeMs", 123_456L)
        .put(
            "topology",
            JSONObject()
                .put("fanOutputCount", 2)
                .put("temperatureSensorCount", 1)
        )
        .put(
            "config",
            JSONObject()
                .put("mode", "Auto")
                .put("minTemperatureC", 30.0)
                .put("maxTemperatureC", 50.0)
        )
        .put(
            "temperature",
            JSONObject()
                .put("sensorKey", "fixture")
                .put("sensorIndex", 0)
                .put("readingValid", true)
                .put("temperatureC", 40.0)
                .put("sampledAtMs", 123_000L)
        )
        .put(
            "lightProtection",
            JSONObject()
                .put("enabled", true)
                .put("active", false)
                .put("thresholdC", 60.0)
        )
        .put(
            "fans",
            JSONArray()
                .put(fan("fan1", 0, 15, 4))
                .put(fan("fan2", 1, 16, 5))
        )
        .put(
            "runtime",
            JSONObject()
                .put("event", "light.thermal.telemetry.changed")
                .put("statusEvent", "light.thermal.status.changed")
                .put("sensorFailSafeActive", false)
                .put("automaticOutputCycleHealthy", true)
                .put("hardwareEditable", false)
                .put("fanMappingEditable", false)
                .put("sensorMappingEditable", false)
        )

    private fun fan(
        key: String,
        index: Int,
        gpio: Int,
        ledcChannel: Int
    ): JSONObject = JSONObject()
        .put("fanKey", key)
        .put("index", index)
        .put("name", "Fan ${index + 1}")
        .put("regime", "Auto")
        .put("valueNow", 0.5)
        .put("valueAuto", 0.5)
        .put("percentNow", 50.0)
        .put("percentAuto", 50.0)
        .put(
            "hardware",
            JSONObject()
                .put("editable", false)
                .put("gpio", gpio)
                .put("ledcChannel", ledcChannel)
                .put("pwmFrequencyHz", 25_000)
                .put("pwmResolutionBits", 10)
                .put("invert", false)
                .put("pwmOutputHealth", "OK")
                .put("health", "UNVERIFIED")
                .put("physicalFeedbackAvailable", false)
        )
}
