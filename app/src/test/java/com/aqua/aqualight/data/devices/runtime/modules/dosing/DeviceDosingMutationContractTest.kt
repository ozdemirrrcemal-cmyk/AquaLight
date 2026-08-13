package com.aqua.aqualight.data.devices.runtime.modules.dosing

import com.aqua.aqualight.data.devices.runtime.modules.dosing.contract.DeviceDosingRuntimeContract
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingCalibrationState
import com.aqua.aqualight.data.devices.runtime.modules.dosing.parsers.DeviceDosingMutationParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDosingMutationContractTest {
    @Test
    fun `all twelve Dosing mutation response schemas parse exactly`() {
        val results = listOf(
            DeviceDosingMutationParser.parseChannelConfigApply(DeviceDosingRuntimeFixtures.channelConfigApply()),
            DeviceDosingMutationParser.parseProgramApply(DeviceDosingRuntimeFixtures.programApply()),
            DeviceDosingMutationParser.parseChannelReset(DeviceDosingRuntimeFixtures.channelReset()),
            DeviceDosingMutationParser.parsePrimeStart(
                DeviceDosingRuntimeFixtures.pump(DeviceDosingRuntimeContract.Action.PRIME_START, true)
            ),
            DeviceDosingMutationParser.parsePrimeStop(
                DeviceDosingRuntimeFixtures.pump(DeviceDosingRuntimeContract.Action.PRIME_STOP, false)
            ),
            DeviceDosingMutationParser.parseCalibrationStart(DeviceDosingRuntimeFixtures.calibrationStart()),
            DeviceDosingMutationParser.parseCalibrationFinish(DeviceDosingRuntimeFixtures.calibrationFinish()),
            DeviceDosingMutationParser.parseCalibrationConfirm(DeviceDosingRuntimeFixtures.calibrationConfirm()),
            DeviceDosingMutationParser.parseCalibrationCancel(DeviceDosingRuntimeFixtures.calibrationCancel()),
            DeviceDosingMutationParser.parseDoseNow(DeviceDosingRuntimeFixtures.doseNow()),
            DeviceDosingMutationParser.parseDoseStop(
                DeviceDosingRuntimeFixtures.pump(DeviceDosingRuntimeContract.Action.DOSE_STOP, false)
            ),
            DeviceDosingMutationParser.parseReservoirRefill(DeviceDosingRuntimeFixtures.reservoirRefill())
        )

        assertEquals(12, results.size)
        assertTrue(results.all { it.channelKey == "channel1" })
        assertTrue(results.all { it.event == DeviceDosingRuntimeContract.STATUS_EVENT })
    }

    @Test
    fun `program apply returns authoritative channel revision and program`() {
        val result = DeviceDosingMutationParser.parseProgramApply(
            DeviceDosingRuntimeFixtures.programApply(revision = 11L, program = DeviceDosingRuntimeFixtures.timerProgram())
        )

        assertEquals(11L, result.channel.revision)
        assertEquals("timer", result.channel.program?.mode?.wireValue)
        assertTrue(result.saved)
    }

    @Test
    fun `channel reset requires canonical null program`() {
        val result = DeviceDosingMutationParser.parseChannelReset(DeviceDosingRuntimeFixtures.channelReset())
        assertNull(result.channel.program)
        assertFalse(result.channel.runtimeEnabled)
    }

    @Test
    fun `calibration finish remains pending until confirmed`() {
        val result = DeviceDosingMutationParser.parseCalibrationFinish(
            DeviceDosingRuntimeFixtures.calibrationFinish(measuredMl = 4.0, durationMs = 5_000L)
        )

        assertEquals(1_250L, result.pendingDoseMsPerMl)
        assertEquals(DeviceDosingCalibrationState.PENDING_VERIFICATION, result.calibrationState)
        assertEquals(DeviceDosingCalibrationState.PENDING_VERIFICATION, result.channel.calibration.state)
    }

    @Test
    fun `calibration confirm revision must match returned channel`() {
        val invalid = DeviceDosingRuntimeFixtures.calibrationConfirm(revision = 9L)
        invalid.getJSONObject("channel").put("revision", 10L)
        assertTrue(runCatching { DeviceDosingMutationParser.parseCalibrationConfirm(invalid) }.isFailure)
    }

    @Test
    fun `manual dose uses pending calibration only when explicitly requested`() {
        val result = DeviceDosingMutationParser.parseDoseNow(
            DeviceDosingRuntimeFixtures.doseNow(
                amountMl = DeviceDosingRuntimeContract.Limit.VERIFICATION_DOSE_ML,
                doseMsPerMl = 1_250L,
                usePendingCalibration = true
            )
        )

        assertEquals(5_000L, result.durationMs)
        assertTrue(result.usePendingCalibration)
        assertEquals("verification", result.channel.activeRun.source.wireValue)
        assertEquals(1_250L, result.channel.calibration.pendingDoseMsPerMl)
    }

    @Test
    fun `mutation parsers reject legacy or fabricated result fields`() {
        val extra = DeviceDosingRuntimeFixtures.programApply().put("saveRequested", true)
        val oldConfig = DeviceDosingRuntimeFixtures.channelConfigApply().put("appliedSchedules", true)

        assertTrue(runCatching { DeviceDosingMutationParser.parseProgramApply(extra) }.isFailure)
        assertTrue(runCatching { DeviceDosingMutationParser.parseChannelConfigApply(oldConfig) }.isFailure)
    }
}
