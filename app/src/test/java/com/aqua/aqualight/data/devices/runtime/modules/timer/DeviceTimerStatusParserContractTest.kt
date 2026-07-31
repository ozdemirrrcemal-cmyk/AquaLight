package com.aqua.aqualight.data.devices.runtime.modules.timer

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceTimerStatusParserContractTest {

    @Test
    fun parsesExactFirmwareStatus() {
        val parsed = DeviceTimerStatusParser.parse(exactStatus())

        assertTrue(parsed.supported)
        assertEquals(1, parsed.channelCount)
        assertEquals("timer_main", parsed.channels.single().key)
        assertEquals(DeviceTimerRegime.AUTO, parsed.channels.single().regime)
        assertEquals(1, parsed.scheduleCount)
        assertTrue(parsed.schedules.single().bound)
        assertEquals(7, parsed.schedules.single().weekdays.size)
        assertFalse(parsed.runtime.readOnly)
        assertTrue(parsed.runtime.supportsConfigApply)
        assertEquals("timer.status.changed", parsed.runtime.event)
    }

    @Test
    fun rejectsUnknownFieldsAndCountDrift() {
        assertThrows(RuntimeException::class.java) {
            DeviceTimerStatusParser.parse(exactStatus().put("unexpected", true))
        }

        val inventedListIndex = exactStatus().also { status ->
            status.getJSONArray("channels").getJSONObject(0).put("listIndex", 0)
        }
        assertThrows(RuntimeException::class.java) {
            DeviceTimerStatusParser.parse(inventedListIndex)
        }

        assertThrows(RuntimeException::class.java) {
            DeviceTimerStatusParser.parse(exactStatus().put("channelCount", 2))
        }
        assertThrows(RuntimeException::class.java) {
            DeviceTimerStatusParser.parse(exactStatus().put("scheduleCount", 2))
        }
    }

    @Test
    fun rejectsTypeRuntimeAndRegimeDrift() {
        assertThrows(RuntimeException::class.java) {
            DeviceTimerStatusParser.parse(exactStatus().put("supported", 1))
        }

        val runtimeDrift = exactStatus().also { status ->
            status.getJSONObject("runtime").put("supportsChannelSet", false)
        }
        assertThrows(RuntimeException::class.java) {
            DeviceTimerStatusParser.parse(runtimeDrift)
        }

        val unknownRegime = exactStatus().also { status ->
            status.getJSONArray("channels").getJSONObject(0).put("regime", "Scheduled")
        }
        assertThrows(RuntimeException::class.java) {
            DeviceTimerStatusParser.parse(unknownRegime)
        }
    }

    @Test
    fun rejectsScheduleShapeAndBindingDrift() {
        val shortWeek = exactStatus().also { status ->
            status.getJSONArray("schedules")
                .getJSONObject(0)
                .put("weekdays", JSONArray().put(true))
        }
        assertThrows(RuntimeException::class.java) {
            DeviceTimerStatusParser.parse(shortWeek)
        }

        val outsideDay = exactStatus().also { status ->
            status.getJSONArray("schedules")
                .getJSONObject(0)
                .put("startTimeMs", 86_400_000L)
        }
        assertThrows(RuntimeException::class.java) {
            DeviceTimerStatusParser.parse(outsideDay)
        }

        val unknownBoundChannel = exactStatus().also { status ->
            status.getJSONArray("schedules")
                .getJSONObject(0)
                .put("channelKey", "missing")
        }
        assertThrows(RuntimeException::class.java) {
            DeviceTimerStatusParser.parse(unknownBoundChannel)
        }
    }

    private fun exactStatus(): JSONObject = JSONObject()
        .put("supported", true)
        .put("channelCount", 1)
        .put("scheduleCount", 1)
        .put("lockLoop", false)
        .put("schema", "commercial-v1")
        .put("rootName", "timer")
        .put("uptimeMs", 12_345L)
        .put("channels", JSONArray().put(channel()))
        .put("schedules", JSONArray().put(schedule()))
        .put("runtime", runtime())

    private fun channel(): JSONObject = JSONObject()
        .put("index", 0)
        .put("key", "timer_main")
        .put("name", "Main Timer")
        .put("displayName", "Main Timer")
        .put("profileManaged", true)
        .put("regime", "Auto")
        .put("channelKind", "gpio")
        .put("gpio", 16)
        .put("ledcChannel", 0)
        .put("group", 0)
        .put("valueNow", 1.0)
        .put("valueAuto", 1.0)
        .put("valueManual", -1.0)
        .put("manualTimeoutMs", 0L)
        .put("invert", false)
        .put("pwmResolutionBits", 12)
        .put("pwmFrequencyHz", 5_000)
        .put(
            "editable",
            JSONObject()
                .put("hardware", false)
                .put("displayName", false)
                .put("hardwareCalibration", false)
        )

    private fun schedule(): JSONObject = JSONObject()
        .put("index", 0)
        .put("enabled", true)
        .put("runtimeEnabled", true)
        .put("name", "Day Cycle")
        .put("channelKey", "timer_main")
        .put("bound", true)
        .put("group", 0)
        .put(
            "weekdays",
            JSONArray()
                .put(true)
                .put(true)
                .put(true)
                .put(true)
                .put(true)
                .put(false)
                .put(false)
        )
        .put("startTimeMs", 28_800_000L)
        .put("startTime", "08:00:00")
        .put("intervalOnMs", 60_000L)
        .put("intervalOn", "00:01:00")
        .put("intervalOffMs", 120_000L)
        .put("intervalOff", "00:02:00")
        .put("repeatCount", 3)
        .put("pulseCountRuntime", 1)
        .put("pulseOffPending", false)
        .put("pulseRemainingMs", 30_000L)

    private fun runtime(): JSONObject = JSONObject()
        .put("module", "timer")
        .put("readOnly", false)
        .put("supportsConfigApply", true)
        .put("supportsChannelSet", true)
        .put("supportsSchedules", true)
        .put("supportsChannels", true)
        .put("event", "timer.status.changed")
}
