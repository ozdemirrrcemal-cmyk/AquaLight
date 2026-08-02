package com.aqua.aqualight.data.devices.runtime.modules.timer

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceTimerMutationContractTest {
    @Test
    fun `exact config result validates channel name and full schedule replacement`() {
        val schedule = DeviceTimerRuntimeFixtures.schedulePayload(name = "Night Filter")
        val payload = DeviceTimerConfigApplyPayload(
            channels = listOf(
                DeviceTimerChannelConfig("channel1", displayName = "Return Pump")
            ),
            schedules = listOf(schedule),
            save = true
        )
        val result = DeviceTimerMutationParser.parseConfigApply(
            DeviceTimerRuntimeFixtures.configApply(
                channelOneDisplayNameOverride = "Return Pump",
                schedules = JSONArray().put(
                    DeviceTimerRuntimeFixtures.configSchedule(name = "Night Filter")
                )
            )
        )

        DeviceTimerCommandValidation.validateConfigResult(
            payload,
            result,
            DeviceTimerStatusParser.parse(DeviceTimerRuntimeFixtures.status()),
            SUPPORTED_ACCESS
        )

        assertEquals("Return Pump", result.config.channels.first().displayNameOverride)
        assertEquals("Night Filter", result.config.schedules.single().name)
        assertTrue(result.saved)
    }

    @Test
    fun `config save echo mismatch is rejected`() {
        val payload = DeviceTimerConfigApplyPayload(
            schedules = emptyList(),
            save = false
        )
        val result = DeviceTimerMutationParser.parseConfigApply(
            DeviceTimerRuntimeFixtures.configApply(
                save = true,
                appliedChannels = false,
                appliedSchedules = true,
                schedules = JSONArray()
            )
        )

        assertTrue(
            runCatching {
                DeviceTimerCommandValidation.validateConfigResult(
                    payload,
                    result,
                    DeviceTimerStatusParser.parse(DeviceTimerRuntimeFixtures.status()),
                    SUPPORTED_ACCESS
                )
            }.isFailure
        )
    }

    @Test
    fun `mutation parser rejects internally inconsistent persistence echo`() {
        val response = DeviceTimerRuntimeFixtures.configApply(save = false)
            .put("saved", true)

        assertTrue(
            runCatching { DeviceTimerMutationParser.parseConfigApply(response) }.isFailure
        )
    }

    @Test
    fun `channel set validates exact key regime and persistence echo`() {
        val payload = DeviceTimerChannelSetPayload(
            channelKey = " CHANNEL1 ",
            regime = DeviceTimerRegime.ON,
            save = true
        )
        val result = DeviceTimerMutationParser.parseChannelSet(
            DeviceTimerRuntimeFixtures.channelSet(regime = "On", save = true)
        )

        DeviceTimerCommandValidation.validateChannelResult(
            payload,
            result,
            DeviceTimerStatusParser.parse(DeviceTimerRuntimeFixtures.status()),
            SUPPORTED_ACCESS
        )

        assertEquals(DeviceTimerRegime.ON, result.channel.channel.regime)
        assertEquals(0, result.channel.listIndex)
    }

    @Test
    fun `duplicate normalized channel keys are rejected before gateway`() {
        val failure = runCatching {
            DeviceTimerConfigApplyPayload(
                channels = listOf(
                    DeviceTimerChannelConfig("channel1", displayName = "One"),
                    DeviceTimerChannelConfig(" CHANNEL1 ", displayName = "Two")
                )
            )
        }

        assertTrue(failure.isFailure)
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
