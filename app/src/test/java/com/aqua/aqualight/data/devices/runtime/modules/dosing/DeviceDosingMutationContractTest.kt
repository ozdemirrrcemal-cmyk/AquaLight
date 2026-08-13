package com.aqua.aqualight.data.devices.runtime.modules.dosing

import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingCalibrationState
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingRunSource
import com.aqua.aqualight.data.devices.runtime.modules.dosing.parsers.DeviceDosingMutationParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDosingMutationContractTest {

    @Test
    fun `persistent channel mutations parse executable API response shapes`() {
        val config = DeviceDosingMutationParser.parseChannelConfigApply(
            DeviceDosingRuntimeFixtures.channelConfigApplyResult()
        )
        val program = DeviceDosingMutationParser.parseProgramApply(
            DeviceDosingRuntimeFixtures.programApplyResult()
        )
        val reset = DeviceDosingMutationParser.parseChannelReset(
            DeviceDosingRuntimeFixtures.channelResetResult()
        )

        assertEquals("channelConfigApply", config.operation)
        assertEquals("programApply", program.operation)
        assertEquals("channelReset", reset.operation)
        assertTrue(config.saved)
        assertTrue(program.saved)
        assertTrue(reset.saved)
        assertEquals(8L, reset.channel.revision)
        assertNull(reset.channel.program)
    }

    @Test
    fun `prime manual and stop mutations expose decorated active run source`() {
        val prime = DeviceDosingMutationParser.parsePrimeStart(
            DeviceDosingRuntimeFixtures.primeStartResult()
        )
        val dose = DeviceDosingMutationParser.parseDoseNow(
            DeviceDosingRuntimeFixtures.doseNowResult()
        )
        val stop = DeviceDosingMutationParser.parseDoseStop(
            DeviceDosingRuntimeFixtures.doseStopResult()
        )

        assertTrue(prime.manualActive)
        assertEquals(DeviceDosingRunSource.PRIME, prime.channel.activeRun.source)
        assertTrue(dose.manualActive)
        assertEquals(DeviceDosingRunSource.MANUAL, dose.channel.activeRun.source)
        assertFalse(stop.manualActive)
        assertFalse(stop.channel.activeRun.active)
    }

    @Test
    fun `calibration mutations preserve pending verification transaction`() {
        val started = DeviceDosingMutationParser.parseCalibrationStart(
            DeviceDosingRuntimeFixtures.calibrationStartResult()
        )
        val finished = DeviceDosingMutationParser.parseCalibrationFinish(
            DeviceDosingRuntimeFixtures.calibrationFinishResult()
        )
        val confirmed = DeviceDosingMutationParser.parseCalibrationConfirm(
            DeviceDosingRuntimeFixtures.calibrationConfirmResult()
        )
        val cancelled = DeviceDosingMutationParser.parseCalibrationCancel(
            DeviceDosingRuntimeFixtures.calibrationCancelResult()
        )

        assertEquals(DeviceDosingCalibrationState.RUNNING, started.calibrationState)
        assertEquals(DeviceDosingCalibrationState.PENDING_VERIFICATION, finished.calibrationState)
        assertEquals(1_250L, finished.pendingDoseMsPerMl)
        assertEquals(DeviceDosingCalibrationState.IDLE, confirmed.calibrationState)
        assertEquals(8L, confirmed.revision)
        assertTrue(confirmed.saved)
        assertEquals(DeviceDosingCalibrationState.IDLE, cancelled.calibrationState)
        assertTrue(cancelled.discardedPendingCalibration)
    }

    @Test
    fun `verification dose uses pending calibration source`() {
        val result = DeviceDosingMutationParser.parseDoseNow(
            DeviceDosingRuntimeFixtures.doseNowResult(pending = true)
        )

        assertTrue(result.usePendingCalibration)
        assertEquals(4.0, result.amountMl, 0.0)
        assertEquals(5_000L, result.durationMs)
        assertEquals(DeviceDosingRunSource.VERIFICATION, result.channel.activeRun.source)
    }

    @Test
    fun `reservoir refill exposes physical before and after accounting`() {
        val result = DeviceDosingMutationParser.parseReservoirRefill(
            DeviceDosingRuntimeFixtures.reservoirRefillResult()
        )

        assertEquals(100.0, result.reservoirRemainingMlBefore, 0.0)
        assertEquals(450.0, result.reservoirRemainingMl, 0.0)
        assertTrue(result.persisted)
    }

    @Test
    fun `mutation parsers reject extra legacy response fields`() {
        val legacy = DeviceDosingRuntimeFixtures.programApplyResult()
            .put("changed", true)

        assertTrue(
            runCatching { DeviceDosingMutationParser.parseProgramApply(legacy) }.isFailure
        )
    }
}
