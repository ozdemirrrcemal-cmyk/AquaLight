package com.aqua.aqualight.data.devices.runtime.modules.light

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceLightStatusParserContractTest {

    @Test
    fun parsesExactFirmwarePayloadWithoutInventedIndexes() {
        val parsed = DeviceLightStatusParser.parse(exactStatus())

        assertTrue(parsed.supported)
        assertEquals(1, parsed.channelCount)
        assertEquals("red", parsed.channels.single().key)
        assertEquals(DeviceLightRegime.AUTO, parsed.channels.single().regime)
        assertEquals(1, parsed.programCount)
        assertEquals(0, parsed.programs.single().index)
        assertEquals(2, parsed.programs.single().points.size)
        assertEquals(43_200_000L, parsed.programs.single().points.last().timeMs)
        assertEquals(75.0, parsed.programs.single().points.last().percent, 0.0)
        assertFalse(parsed.runtime.readOnly)
        assertEquals("light.status.changed", parsed.runtime.event)
    }

    @Test
    fun rejectsUnknownAndFormerlyInventedFields() {
        val unknownTopLevel = exactStatus().put("unexpected", true)
        assertThrows(RuntimeException::class.java) {
            DeviceLightStatusParser.parse(unknownTopLevel)
        }

        val inventedProgramIndex = exactStatus().also { status ->
            status.getJSONArray("programs").getJSONObject(0).put("listIndex", 0)
        }
        assertThrows(RuntimeException::class.java) {
            DeviceLightStatusParser.parse(inventedProgramIndex)
        }

        val inventedPointIndex = exactStatus().also { status ->
            status.getJSONArray("programs")
                .getJSONObject(0)
                .getJSONArray("points")
                .getJSONObject(0)
                .put("index", 0)
        }
        assertThrows(RuntimeException::class.java) {
            DeviceLightStatusParser.parse(inventedPointIndex)
        }
    }

    @Test
    fun rejectsCountAndBindingDrift() {
        val channelCountDrift = exactStatus().put("channelCount", 2)
        assertThrows(RuntimeException::class.java) {
            DeviceLightStatusParser.parse(channelCountDrift)
        }

        val pointCountDrift = exactStatus().also { status ->
            status.getJSONArray("programs").getJSONObject(0).put("pointCount", 1)
        }
        assertThrows(RuntimeException::class.java) {
            DeviceLightStatusParser.parse(pointCountDrift)
        }

        val unknownBoundChannel = exactStatus().also { status ->
            status.getJSONArray("programs").getJSONObject(0).put("channelKey", "blue")
        }
        assertThrows(RuntimeException::class.java) {
            DeviceLightStatusParser.parse(unknownBoundChannel)
        }
    }

    @Test
    fun rejectsTypeRuntimeAndUnitEchoDrift() {
        val percentDrift = exactStatus().also { status ->
            status.getJSONArray("channels").getJSONObject(0).put("percentNow", 49.0)
        }
        assertThrows(RuntimeException::class.java) {
            DeviceLightStatusParser.parse(percentDrift)
        }

        val runtimeDrift = exactStatus().also { status ->
            status.getJSONObject("runtime").put("supportsProgramApply", false)
        }
        assertThrows(RuntimeException::class.java) {
            DeviceLightStatusParser.parse(runtimeDrift)
        }

        val numericBoolean = exactStatus().put("supported", 1)
        assertThrows(RuntimeException::class.java) {
            DeviceLightStatusParser.parse(numericBoolean)
        }
    }

    private fun exactStatus(): JSONObject = JSONObject()
        .put("supported", true)
        .put("manualSupported", true)
        .put("programSupported", true)
        .put("presetsSupported", true)
        .put("simulationSupported", true)
        .put("channelCount", 1)
        .put("programCount", 1)
        .put("liveEditEnabled", false)
        .put("channelEdit", 0)
        .put("powerLimitW", 120.0)
        .put("lockLoop", false)
        .put("temperatureDownStepPercent", 10.0)
        .put("temperatureRecoveryMs", 5_000L)
        .put("lightCorrectionFactor", 1.0)
        .put("uptimeMs", 12_345L)
        .put("channels", JSONArray().put(channel()))
        .put("programs", JSONArray().put(program()))
        .put("runtime", runtime())

    private fun channel(): JSONObject = JSONObject()
        .put("index", 0)
        .put("key", "red")
        .put("name", "Red")
        .put("displayName", "Red")
        .put("profileManaged", true)
        .put("regime", "Auto")
        .put("channelKind", "gpio")
        .put("gpio", 16)
        .put("ledcChannel", 0)
        .put("group", 0)
        .put("valueNow", 0.5)
        .put("valueAuto", 0.5)
        .put("valueManual", -1.0)
        .put("manualTimeoutMs", 0L)
        .put("percentNow", 50.0)
        .put("percentAuto", 50.0)
        .put("percentManual", -100.0)
        .put("invert", false)
        .put("pwmResolutionBits", 12)
        .put("pwmFrequencyHz", 5_000)
        .put("color", 0x00FF0000)
        .put("lumen", 100.0)
        .put("lux", 20.0)
        .put("watt", 15.0)
        .put(
            "editable",
            JSONObject()
                .put("hardware", false)
                .put("displayName", false)
                .put("color", false)
                .put("hardwareCalibration", false)
        )

    private fun program(): JSONObject = JSONObject()
        .put("index", 0)
        .put("channelKey", "red")
        .put("bound", true)
        .put("pointCount", 2)
        .put(
            "points",
            JSONArray()
                .put(
                    JSONObject()
                        .put("timeMs", 0L)
                        .put("time", "00:00:00")
                        .put("value", 0.0)
                        .put("percent", 0.0)
                )
                .put(
                    JSONObject()
                        .put("timeMs", 43_200_000L)
                        .put("time", "12:00:00")
                        .put("value", 0.75)
                        .put("percent", 75.0)
                )
        )

    private fun runtime(): JSONObject = JSONObject()
        .put("module", "light")
        .put("readOnly", false)
        .put("supportsManualSet", true)
        .put("supportsChannelRegimeSet", true)
        .put("supportsProgramApply", true)
        .put("supportsProgramDelete", true)
        .put("supportsLiveEdit", false)
        .put("event", "light.status.changed")
}
