package com.aqua.aqualight.data.devices.runtime.modules.dosing

import com.aqua.aqualight.data.devices.runtime.modules.dosing.contract.DeviceDosingRuntimeContract
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingProgramMode
import com.aqua.aqualight.data.devices.runtime.modules.dosing.parsers.DeviceDosingStatusParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDosingRuntimeContractTest {

    @Test
    fun `Dosing action catalog exposes thirteen final authenticated commands`() {
        assertEquals(
            setOf(
                "status.get",
                "config.apply",
                "program.apply",
                "channel.reset",
                "prime.start",
                "prime.stop",
                "calibration.start",
                "calibration.finish",
                "calibration.confirm",
                "calibration.cancel",
                "dose.now",
                "dose.stop",
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
    fun `global status exposes firmware scheduling metadata and channel revisions`() {
        val status = DeviceDosingStatusParser.parseGlobal(
            DeviceDosingRuntimeFixtures.globalStatus()
        )

        assertEquals(DeviceDosingRuntimeContract.SCHEMA, status.envelope.schema)
        assertEquals(2, status.envelope.channelCount)
        assertEquals(24, status.scheduling.maxEventsPerChannel)
        assertEquals(24, status.scheduling.maxCustomPeriodsPerChannel)
        assertEquals(0.001, status.scheduling.amountResolutionMl, 0.0)
        assertEquals(DeviceDosingProgramMode.entries.toSet(), status.scheduling.supportedModes.toSet())
        assertEquals(7L, status.channels.first().revision)
        assertTrue(status.runtime.supportsChannelScopedStatus)
    }

    @Test
    fun `channel status owns one canonical optional program and usage`() {
        val status = DeviceDosingStatusParser.parseChannel(
            DeviceDosingRuntimeFixtures.channelStatus(
                effectiveName = "Nutrients"
            )
        )

        assertEquals("channel1", status.channel.channelKey)
        assertEquals("Nutrients", status.channel.effectiveName)
        assertEquals(7L, status.channel.revision)
        assertEquals(DeviceDosingProgramMode.SINGLE, status.channel.program?.mode)
        assertTrue(status.channel.calibration.confirmed)
        assertEquals(8.0, status.channel.usageToday.totalDeliveredMl, 0.0)
        assertEquals(300.0, status.channel.reservoir.remainingMl, 0.0)
    }

    @Test
    fun `disabled program remains configured while runtime is disabled`() {
        val status = DeviceDosingStatusParser.parseChannel(
            DeviceDosingRuntimeFixtures.channelStatus(
                program = DeviceDosingRuntimeFixtures.singleProgram(enabled = false)
            )
        )

        assertFalse(status.channel.runtimeEnabled)
        assertFalse(requireNotNull(status.channel.program).enabled)
    }

    @Test
    fun `channel reset state represents absent program instead of empty schedule list`() {
        val status = DeviceDosingStatusParser.parseChannel(
            DeviceDosingRuntimeFixtures.channelStatus(program = null)
        )

        assertNull(status.channel.program)
        assertFalse(status.channel.runtimeEnabled)
    }

    @Test
    fun `slim status event carries revision and runtime change only`() {
        val change = DeviceDosingStatusParser.parseStatusChange(
            DeviceDosingRuntimeFixtures.statusChanged(revision = 9L, sequence = 4L)
        )

        assertEquals("channel1", change.channelKey)
        assertEquals(9L, change.revision)
        assertEquals(4L, change.change.sequence)
        assertTrue(change.storageHealthy)
    }
}
