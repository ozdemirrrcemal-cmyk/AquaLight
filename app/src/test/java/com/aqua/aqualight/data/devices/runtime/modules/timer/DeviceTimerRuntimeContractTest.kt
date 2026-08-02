package com.aqua.aqualight.data.devices.runtime.modules.timer

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceTimerRuntimeContractTest {
    @Test
    fun `status parser accepts exact standalone Timer snapshot`() {
        val status = DeviceTimerStatusParser.parse(DeviceTimerRuntimeFixtures.status())

        assertTrue(status.supported)
        assertEquals(2, status.channelCount)
        assertEquals("Filter", status.channels.first().displayName)
        assertEquals(DeviceTimerRegime.AUTO, status.channels.first().regime)
        assertEquals("08:00", status.schedules.single().startTime)
        assertTrue(status.schedules.single().runtimeEnabled)
        assertTrue(status.runtime.supportsSchedules)
    }

    @Test
    fun `status parser rejects extra fields and regime aliases`() {
        val extra = DeviceTimerRuntimeFixtures.status().put("unexpected", true)
        val alias = DeviceTimerRuntimeFixtures.status()
        alias.getJSONArray("channels").getJSONObject(0).put("regime", "schedule")

        assertTrue(runCatching { DeviceTimerStatusParser.parse(extra) }.isFailure)
        assertTrue(runCatching { DeviceTimerStatusParser.parse(alias) }.isFailure)
    }

    @Test
    fun `status parser rejects malformed weekdays and derived time text`() {
        val weekdays = DeviceTimerRuntimeFixtures.status()
        weekdays.getJSONArray("schedules").getJSONObject(0)
            .put("weekdays", JSONArray(listOf(true, false)))
        val time = DeviceTimerRuntimeFixtures.status()
        time.getJSONArray("schedules").getJSONObject(0).put("startTime", "8:00")

        assertTrue(runCatching { DeviceTimerStatusParser.parse(weekdays) }.isFailure)
        assertTrue(runCatching { DeviceTimerStatusParser.parse(time) }.isFailure)
    }

    @Test
    fun `config payload distinguishes omitted arrays from an empty schedule replacement`() {
        val deleteAll = DeviceTimerConfigApplyPayload(
            schedules = emptyList(),
            save = true
        ).toJson()

        assertEquals(setOf("schedules", "save"), deleteAll.keys().asSequence().toSet())
        assertEquals(0, deleteAll.getJSONArray("schedules").length())
        assertFalse(deleteAll.has("channels"))
    }

    @Test
    fun `channel display name payload normalizes names and preserves empty clear command`() {
        val renamed = DeviceTimerChannelConfig(" CHANNEL1 ", displayName = " Filter Pump ")
            .toJson()
        val cleared = DeviceTimerChannelConfig("channel1", displayName = "   ")
            .toJson()

        assertEquals(setOf("channelKey", "displayName"), renamed.keys().asSequence().toSet())
        assertEquals("channel1", renamed.getString("channelKey"))
        assertEquals("Filter Pump", renamed.getString("displayName"))
        assertEquals("", cleared.getString("displayName"))
    }

    @Test
    fun `config parser keeps absent display override distinct from effective status name`() {
        val result = DeviceTimerMutationParser.parseConfigApply(
            DeviceTimerRuntimeFixtures.configApply(channelOneDisplayNameOverride = null)
        )

        assertNull(result.config.channels.first().displayNameOverride)
    }

    @Test
    fun `standalone Timer config rejects dosing amount`() {
        val response = DeviceTimerRuntimeFixtures.configApply()
        response.getJSONObject("config")
            .getJSONArray("schedules")
            .getJSONObject(0)
            .put("amountMl", 2.5)

        assertTrue(runCatching { DeviceTimerMutationParser.parseConfigApply(response) }.isFailure)
    }

    @Test
    fun `enabled schedule rejects inert runtime configuration before encoding`() {
        val failure = runCatching {
            DeviceTimerScheduleConfig(
                enabled = true,
                name = "Invalid",
                channelKey = "channel1",
                weekdays = List(7) { false },
                startTimeMs = 0L,
                intervalOnMs = 0L,
                intervalOffMs = 0L,
                repeatCount = 0
            )
        }

        assertTrue(failure.isFailure)
    }
}
