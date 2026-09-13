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
            expectedRevision = TIMER_TEST_BASE_REVISION,
            displayName = DeviceTimerDisplayNameUpdate.Value("Return Pump"),
            schedules = listOf(
                DeviceTimerRuntimeFixtures.schedulePayload(slotId = 1),
                DeviceTimerRuntimeFixtures.schedulePayload(
                    slotId = 2,
                    name = "Night Filter",
                    startTimeMs = timerScheduleBoundaryMillis(TIMER_TEST_EVENING_START_HOUR, 0),
                    endTimeMs = timerScheduleBoundaryMillis(TIMER_TEST_EVENING_END_HOUR, 0)
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
        assertEquals(TIMER_TEST_APPLIED_REVISION, result.revision)
        assertEquals(2, result.channel.scheduleCount)
        assertTrue(result.appliedDisplayName)
        assertTrue(result.replacedSchedules)
    }

    @Test
    fun `golden temporary override keeps persistent regime and revision unchanged`() {
        val payload = DeviceTimerChannelSetPayload(
            channelKey = "channel1",
            expectedRevision = TIMER_TEST_APPLIED_REVISION,
            regime = DeviceTimerRegime.OFF,
            durationMs = TIMER_TEST_OVERRIDE_DURATION_MILLIS,
            save = false
        )
        val result = DeviceTimerMutationParser.parseChannelSet(
            DeviceTimerRuntimeFixtures.channelSet(revision = TIMER_TEST_APPLIED_REVISION)
        )

        DeviceTimerCommandValidation.validateChannelResult(
            payload,
            result,
            DeviceTimerStatusParser.parse(
                DeviceTimerRuntimeFixtures.globalStatus(revision = TIMER_TEST_APPLIED_REVISION)
            ),
            SUPPORTED_ACCESS
        )

        assertEquals(DeviceTimerRegime.AUTO, result.channel.regime)
        assertTrue(result.channel.temporaryOverrideActive)
        assertFalse(result.persistentChanged)
        assertEquals(TIMER_TEST_APPLIED_REVISION, result.revision)
    }

    @Test
    fun `persistent channel result requires canonical operation and revision increment`() {
        val response = DeviceTimerRuntimeFixtures.channelSet(
            revision = TIMER_TEST_APPLIED_REVISION
        ).also { data ->
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
            expectedRevision = TIMER_TEST_BASE_REVISION,
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
        assertEquals(TIMER_TEST_APPLIED_REVISION, result.revision)
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
                    expectedRevision = TIMER_TEST_BASE_REVISION,
                    regime = DeviceTimerRegime.OFF,
                    durationMs = TIMER_TEST_SUB_MINUTE_MILLIS,
                    save = true
                )
            }.isFailure
        )
        assertTrue(
            runCatching {
                DeviceTimerChannelSetPayload(
                    channelKey = "channel1",
                    expectedRevision = TIMER_TEST_BASE_REVISION,
                    regime = DeviceTimerRegime.AUTO,
                    durationMs = TIMER_TEST_SUB_MINUTE_MILLIS,
                    save = false
                )
            }.isFailure
        )
    }

    private companion object {
        val SUPPORTED_ACCESS = DeviceTimerRuntimeAccess(
            supportsApi = true,
            channelCount = TIMER_TEST_CHANNEL_COUNT,
            supportsSchedules = true,
            supportsChannelState = true,
            supportsChannelDisplayName = true
        )
    }
}
