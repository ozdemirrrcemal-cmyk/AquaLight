@file:Suppress("MagicNumber")

package com.aqua.aqualight.data.devices.runtime.modules.timer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceTimerMutationContractTest {
    @Test
    fun `golden channel config result validates exact revision scope and replacement`() {
        val payload = DeviceTimerConfigApplyPayload(
            channelKey = "channel1",
            expectedRevision = 7L,
            displayName = DeviceTimerDisplayNameUpdate.Value("Return Pump"),
            schedules = listOf(
                DeviceTimerRuntimeFixtures.schedulePayload(slotId = 1),
                DeviceTimerRuntimeFixtures.schedulePayload(
                    slotId = 2,
                    name = "Night Filter",
                    startTimeMs = timerScheduleBoundaryMillis(20, 0),
                    endTimeMs = timerScheduleBoundaryMillis(22, 0)
                )
            )
        )
        val result = DeviceTimerMutationParser.parseConfigApply(
            DeviceTimerRuntimeFixtures.configApply()
        )

        DeviceTimerCommandValidation.validateConfigResult(
            payload,
            result,
            DeviceTimerStatusParser.parse(DeviceTimerRuntimeFixtures.globalStatus()),
            SUPPORTED_ACCESS
        )

        assertEquals("channel1", result.channelKey)
        assertEquals(8L, result.revision)
        assertEquals(2, result.channel.scheduleCount)
        assertTrue(result.appliedDisplayName)
        assertTrue(result.replacedSchedules)
    }

    @Test
    fun `golden temporary override keeps persistent regime and revision unchanged`() {
        val payload = DeviceTimerChannelSetPayload(
            channelKey = "channel1",
            expectedRevision = 8L,
            regime = DeviceTimerRegime.OFF,
            durationMs = 300_000L,
            save = false
        )
        val result = DeviceTimerMutationParser.parseChannelSet(
            DeviceTimerRuntimeFixtures.channelSet(revision = 8L)
        )

        DeviceTimerCommandValidation.validateChannelResult(
            payload,
            result,
            DeviceTimerStatusParser.parse(
                DeviceTimerRuntimeFixtures.globalStatus(revision = 8L)
            ),
            SUPPORTED_ACCESS
        )

        assertEquals(DeviceTimerRegime.AUTO, result.channel.regime)
        assertTrue(result.channel.temporaryOverrideActive)
        assertFalse(result.persistentChanged)
        assertEquals(8L, result.revision)
    }

    @Test
    fun `persistent channel result requires canonical operation and revision increment`() {
        val response = DeviceTimerRuntimeFixtures.channelSet(revision = 8L).also { data ->
            data.put("operation", "channelSet")
            data.put("persistentChanged", true)
            data.put("saved", true)
            data.put("saveRequested", true)
            data.put("regime", "On")
            data.put("durationMs", 0L)
            data.getJSONObject("channel").also { channel ->
                channel.put("regime", "On")
                channel.put("temporaryOverrideActive", false)
                channel.put("temporaryOverrideRemainingMs", 0L)
                channel.put("runtimeReason", "manualOn")
            }
        }
        val payload = DeviceTimerChannelSetPayload(
            channelKey = "channel1",
            expectedRevision = 7L,
            regime = DeviceTimerRegime.ON
        )
        val result = DeviceTimerMutationParser.parseChannelSet(response)

        DeviceTimerCommandValidation.validateChannelResult(
            payload,
            result,
            DeviceTimerStatusParser.parse(DeviceTimerRuntimeFixtures.globalStatus()),
            SUPPORTED_ACCESS
        )

        assertEquals(DeviceTimerRegime.ON, result.channel.regime)
        assertEquals(8L, result.revision)
    }

    @Test
    fun `mutation parsers reject old event and whole-device config fields`() {
        val config = DeviceTimerRuntimeFixtures.configApply().put("event", "timer.status.changed")
        val channel = DeviceTimerRuntimeFixtures.channelSet().put("config", true)

        assertTrue(runCatching { DeviceTimerMutationParser.parseConfigApply(config) }.isFailure)
        assertTrue(runCatching { DeviceTimerMutationParser.parseChannelSet(channel) }.isFailure)
    }

    @Test
    fun `temporary override request rejects persistence and Auto regime`() {
        assertTrue(
            runCatching {
                DeviceTimerChannelSetPayload(
                    channelKey = "channel1",
                    expectedRevision = 7L,
                    regime = DeviceTimerRegime.OFF,
                    durationMs = 1_000L,
                    save = true
                )
            }.isFailure
        )
        assertTrue(
            runCatching {
                DeviceTimerChannelSetPayload(
                    channelKey = "channel1",
                    expectedRevision = 7L,
                    regime = DeviceTimerRegime.AUTO,
                    durationMs = 1_000L,
                    save = false
                )
            }.isFailure
        )
    }

    private companion object {
        val SUPPORTED_ACCESS = DeviceTimerRuntimeAccess(
            supportsApi = true,
            channelCount = 2,
            supportsSchedules = true,
            supportsChannelState = true,
            supportsChannelDisplayName = true
        )
    }
}
