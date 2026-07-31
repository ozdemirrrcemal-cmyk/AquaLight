package com.aqua.aqualight.data.devices.runtime.modules.cooling

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCoolingStatusParserContractTest {

    @Test
    fun `firmware cooling status parses exact fan display-name capability`() {
        val parsed = DeviceCoolingStatusParser.parseExact(validStatus()).getOrThrow()

        assertTrue(parsed.runtime.supportsFanDisplayName)
        assertTrue(parsed.fans.single().editable.displayName)
        assertEquals("fan1", parsed.fans.single().key)
        assertEquals(DeviceCoolingMode.AUTO, parsed.mode)
    }

    @Test
    fun `missing runtime capability fails closed`() {
        val invalid = validStatus().apply {
            getJSONObject("runtime").remove("supportsFanDisplayName")
        }

        assertTrue(DeviceCoolingStatusParser.parseExact(invalid).isFailure)
    }

    @Test
    fun `runtime and fan editability disagreement fails closed`() {
        val invalid = validStatus().apply {
            getJSONObject("runtime").put("supportsFanDisplayName", false)
        }

        assertTrue(DeviceCoolingStatusParser.parseExact(invalid).isFailure)
    }

    @Test
    fun `unknown firmware mode is not coerced to off`() {
        val invalid = validStatus().put("mode", "automatic")

        assertTrue(DeviceCoolingStatusParser.parseExact(invalid).isFailure)
    }

    @Test
    fun `declared fan count must match complete fan array`() {
        val invalid = validStatus().put("fanOutputCount", 2)

        assertTrue(DeviceCoolingStatusParser.parseExact(invalid).isFailure)
    }

    private fun validStatus(): JSONObject = JSONObject()
        .put("supported", true)
        .put("fanSupported", true)
        .put("temperatureSupported", true)
        .put("fanOutputCount", 1)
        .put("ruleCount", 1)
        .put("mode", "Auto")
        .put("minTemperatureC", 28.0)
        .put("maxTemperatureC", 35.0)
        .put("fixedSensorIndex", 0)
        .put("uptimeMs", 12_345L)
        .put("fans", JSONArray().put(validFan()))
        .put("rules", JSONArray().put(validRule()))
        .put("runtime", validRuntime())

    private fun validRuntime(): JSONObject = JSONObject()
        .put("module", "cooling")
        .put("readOnly", false)
        .put("supportsConfigApply", true)
        .put("supportsModeSet", true)
        .put("supportsTemperatureRange", true)
        .put("supportsFanDisplayName", true)
        .put("hardwareEditable", false)
        .put("fanMappingEditable", false)
        .put("sensorMappingEditable", false)
        .put("event", "cooling.status.changed")

    private fun validFan(): JSONObject = JSONObject()
        .put("index", 0)
        .put("key", "fan1")
        .put("name", "Fan 1")
        .put("displayName", "Sol Fan")
        .put("profileManaged", true)
        .put("regime", "Auto")
        .put("channelKind", "gpio")
        .put("gpio", 5)
        .put("ledcChannel", 1)
        .put("group", 0)
        .put("valueNow", 0.4)
        .put("valueAuto", 0.4)
        .put("valueManual", -1.0)
        .put("valueMin", 0.0)
        .put("valueMax", 1.0)
        .put("manualTimeoutMs", 0L)
        .put("percentNow", 40.0)
        .put("percentAuto", 40.0)
        .put("percentManual", -100.0)
        .put("percentMin", 0.0)
        .put("percentMax", 100.0)
        .put("invert", false)
        .put("pwmResolutionBits", 8)
        .put("pwmFrequencyHz", 25_000)
        .put(
            "editable",
            JSONObject()
                .put("hardware", false)
                .put("displayName", true)
                .put("hardwareCalibration", false)
        )

    private fun validRule(): JSONObject = JSONObject()
        .put("index", 0)
        .put("name", "Cooling")
        .put("enabled", true)
        .put("fanIndex", 0)
        .put("channelKey", "fan1")
        .put("bound", true)
        .put("minTemperatureC", 28.0)
        .put("maxTemperatureC", 35.0)
        .put("group", 0)
        .put("sensorBindings", JSONArray().put(0))
}
