package com.aqua.aqualight.data.devices.runtime.modules.dosing

import com.aqua.aqualight.data.devices.runtime.modules.dosing.contract.DeviceDosingRuntimeContract
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingCalibrationFinishPayload
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingCalibrationStartPayload
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingChannelConfigPayload
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingChannelResetPayload
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingDisplayNameMutation
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingDistributedProgramConfig
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingDoseNowPayload
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingProgram
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingProgramApplyPayload
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingProgramMode
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingReservoirConfig
import com.aqua.aqualight.data.devices.runtime.modules.dosing.parsers.DeviceDosingStatusParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDosingRuntimeContractTest {
    @Test
    fun `Dosing action catalog contains all thirteen authenticated commands`() {
        assertEquals(
            setOf(
                "status.get", "config.apply", "program.apply", "channel.reset",
                "prime.start", "prime.stop", "calibration.start", "calibration.finish",
                "calibration.confirm", "calibration.cancel", "dose.now", "dose.stop",
                "reservoir.refill"
            ),
            setOf(
                DeviceDosingRuntimeContract.Action.STATUS_GET,
                DeviceDosingRuntimeContract.Action.CONFIG_APPLY,
                DeviceDosingRuntimeContract.Action.PROGRAM_APPLY,
                DeviceDosingRuntimeContract.Action.CHANNEL_RESET,
                DeviceDosingRuntimeContract.Action.PRIME_START,
                DeviceDosingRuntimeContract.Action.PRIME_STOP,
                DeviceDosingRuntimeContract.Action.CALIBRATION_START,
                DeviceDosingRuntimeContract.Action.CALIBRATION_FINISH,
                DeviceDosingRuntimeContract.Action.CALIBRATION_CONFIRM,
                DeviceDosingRuntimeContract.Action.CALIBRATION_CANCEL,
                DeviceDosingRuntimeContract.Action.DOSE_NOW,
                DeviceDosingRuntimeContract.Action.DOSE_STOP,
                DeviceDosingRuntimeContract.Action.RESERVOIR_REFILL
            )
        )
    }

    @Test
    fun `global and channel status parse final program metadata`() {
        val global = DeviceDosingStatusParser.parseGlobal(DeviceDosingRuntimeFixtures.globalStatus())
        val channel = DeviceDosingStatusParser.parseChannel(DeviceDosingRuntimeFixtures.channelStatus())

        assertEquals(2, global.envelope.channelCount)
        assertEquals(24, global.scheduling.maxEventsPerChannel)
        assertEquals(8, global.scheduling.maxCustomPeriodsPerChannel)
        assertTrue(global.runtime.supportsProgramApply)
        assertTrue(global.runtime.supportsChannelScopedStatus)
        assertEquals(7L, channel.channel.revision)
        assertEquals(DeviceDosingProgramMode.SINGLE, channel.channel.program?.mode)
        assertEquals(10.0, (channel.channel.program?.config as DeviceDosingDistributedProgramConfig).dailyDoseMl, 0.0)
        assertEquals(4.0, channel.channel.usageToday.totalDeliveredMl, 0.0)
    }

    @Test
    fun `channel status accepts reset state with null program`() {
        val parsed = DeviceDosingStatusParser.parseChannel(
            DeviceDosingRuntimeFixtures.channelStatus(program = null)
        )
        assertNull(parsed.channel.program)
    }

    @Test
    fun `slim status change parses revision without pretending to be a full snapshot`() {
        val change = DeviceDosingStatusParser.parseStatusChange(
            DeviceDosingRuntimeFixtures.statusChange(revision = 9L)
        )
        assertEquals("channel1", change.channelKey)
        assertEquals(9L, change.revision)
        assertTrue(change.storageHealthy)
    }

    @Test
    fun `revision guarded channel config program and reset encode exact firmware keys`() {
        val config = DeviceDosingChannelConfigPayload(
            channelKey = "channel1",
            expectedRevision = 7L,
            displayName = DeviceDosingDisplayNameMutation.Set("Macro"),
            reservoir = DeviceDosingReservoirConfig(true, 500.0)
        ).toJson()
        val program = DeviceDosingProgram(
            enabled = true,
            weekdays = List(7) { true },
            mode = DeviceDosingProgramMode.SINGLE,
            missedDoseRecoveryEnabled = true,
            config = DeviceDosingDistributedProgramConfig(10.0, 28_800_000L)
        )
        val apply = DeviceDosingProgramApplyPayload("channel1", 7L, program).toJson()
        val reset = DeviceDosingChannelResetPayload("channel1", 7L).toJson()

        assertEquals(setOf("channelKey", "expectedRevision", "displayName", "reservoir"), config.keySet())
        assertEquals(setOf("trackingEnabled", "capacityMl"), config.getJSONObject("reservoir").keySet())
        assertEquals(setOf("channelKey", "expectedRevision", "program"), apply.keySet())
        assertEquals(
            setOf("enabled", "weekdays", "mode", "missedDoseRecoveryEnabled", "config"),
            apply.getJSONObject("program").keySet()
        )
        assertEquals(setOf("channelKey", "expectedRevision"), reset.keySet())
    }

    @Test
    fun `manual dose and calibration payloads reject unsafe values`() {
        assertTrue(runCatching { DeviceDosingDoseNowPayload("channel1", 0.0) }.isFailure)
        assertTrue(runCatching { DeviceDosingCalibrationStartPayload("channel1", 999L) }.isFailure)
        assertTrue(runCatching { DeviceDosingCalibrationFinishPayload("channel1", 0.049) }.isFailure)
    }

    private fun org.json.JSONObject.keySet(): Set<String> = keys().asSequence().toSet()
}
