package com.aqua.aqualight.data.devices.dosing.v1

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@Suppress("LongMethod")
class DeviceDosingV1MutationContractTest {
    @Test
    fun `all mutation parsers accept the final handler response shapes`() {
        assertEquals(
            DeviceDosingV1Contract.Literal.CHANNEL_CONFIG_APPLY,
            DeviceDosingV1MutationParser.parseConfigApply(
                DeviceDosingV1TestFixtures.savedMutation(
                    DeviceDosingV1Contract.Literal.CHANNEL_CONFIG_APPLY
                )
            ).operation
        )
        assertEquals(
            DeviceDosingV1Contract.Literal.PROGRAM_APPLY,
            DeviceDosingV1MutationParser.parseProgramApply(
                DeviceDosingV1TestFixtures.savedMutation(
                    DeviceDosingV1Contract.Literal.PROGRAM_APPLY
                )
            ).operation
        )
        assertEquals(
            DeviceDosingV1Contract.Literal.CHANNEL_RESET,
            DeviceDosingV1MutationParser.parseChannelReset(
                DeviceDosingV1TestFixtures.savedMutation(
                    DeviceDosingV1Contract.Literal.CHANNEL_RESET
                )
            ).operation
        )
        assertTrue(
            DeviceDosingV1MutationParser.parsePrimeStart(
                DeviceDosingV1TestFixtures.primeStart()
            ).manualActive
        )
        assertFalse(
            DeviceDosingV1MutationParser.parsePrimeStop(
                DeviceDosingV1TestFixtures.simpleStop(
                    DeviceDosingV1Contract.Literal.PRIME_STOP
                )
            ).manualActive
        )
        assertTrue(
            DeviceDosingV1MutationParser.parseDoseNow(
                DeviceDosingV1TestFixtures.doseNow()
            ).usePendingCalibration
        )
        assertFalse(
            DeviceDosingV1MutationParser.parseDoseStop(
                DeviceDosingV1TestFixtures.simpleStop(
                    DeviceDosingV1Contract.Literal.DOSE_STOP
                )
            ).manualActive
        )
        assertEquals(
            "running",
            DeviceDosingV1MutationParser.parseCalibrationStart(
                DeviceDosingV1TestFixtures.calibrationStart()
            ).calibrationState.raw
        )
        assertEquals(
            "pendingVerification",
            DeviceDosingV1MutationParser.parseCalibrationFinish(
                DeviceDosingV1TestFixtures.calibrationFinish()
            ).calibrationState.raw
        )
        assertTrue(
            DeviceDosingV1MutationParser.parseCalibrationConfirm(
                DeviceDosingV1TestFixtures.calibrationConfirm()
            ).saved
        )
        assertTrue(
            DeviceDosingV1MutationParser.parseCalibrationCancel(
                DeviceDosingV1TestFixtures.calibrationCancel()
            ).discardedPendingCalibration
        )
        assertTrue(
            DeviceDosingV1MutationParser.parseReservoirRefill(
                DeviceDosingV1TestFixtures.reservoirRefill()
            ).persisted
        )
    }
}
