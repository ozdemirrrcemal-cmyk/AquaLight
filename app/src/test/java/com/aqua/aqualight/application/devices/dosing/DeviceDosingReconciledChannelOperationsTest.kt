package com.aqua.aqualight.application.devices.dosing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDosingReconciledChannelOperationsTest {

    @Test
    fun `committed plan save reconciles only the mutated channel before success`() = runTest {
        val desired = sampleSnapshot().program!!.copy(enabled = false)
        val authoritative = sampleSnapshot().copy(revision = 8L, program = desired)
        val delegate = FakeOperations(
            mutationResult = DeviceDosingChannelCommittedResult(8L),
            refreshResult = DeviceDosingChannelOperationResult.Success(authoritative)
        )
        val operations = DeviceDosingReconciledChannelOperations(delegate)

        val result = operations.applyProgramAtRevision(
            deviceUid = DEVICE_UID,
            slotId = SLOT_ID,
            program = desired,
            expectedRevision = 7L
        )

        assertTrue(result is DeviceDosingChannelOperationResult.Success)
        assertEquals(listOf(DEVICE_UID to SLOT_ID), delegate.refreshRequests)
        assertEquals(0, delegate.refreshAllCount)
    }

    @Test
    fun `already authoritative mutation result does not trigger helper readback`() = runTest {
        val authoritative = sampleSnapshot().copy(revision = 8L)
        val delegate = FakeOperations(
            mutationResult = DeviceDosingChannelOperationResult.Success(authoritative),
            refreshResult = DeviceDosingChannelOperationResult.Failed
        )
        val operations = DeviceDosingReconciledChannelOperations(delegate)

        val result = operations.applyProgram(
            deviceUid = DEVICE_UID,
            slotId = SLOT_ID,
            program = authoritative.program!!
        )

        assertEquals(DeviceDosingChannelOperationResult.Success(authoritative), result)
        assertTrue(delegate.refreshRequests.isEmpty())
        assertEquals(0, delegate.refreshAllCount)
    }

    @Test
    fun `committed readback older than ack revision fails closed`() = runTest {
        val desired = sampleSnapshot().program!!
        val delegate = FakeOperations(
            mutationResult = DeviceDosingChannelCommittedResult(9L),
            refreshResult = DeviceDosingChannelOperationResult.Success(
                sampleSnapshot().copy(revision = 8L, program = desired)
            )
        )
        val operations = DeviceDosingReconciledChannelOperations(delegate)

        val result = operations.applyProgram(DEVICE_UID, SLOT_ID, desired)

        assertEquals(DeviceDosingChannelOperationResult.Failed, result)
        assertEquals(listOf(DEVICE_UID to SLOT_ID), delegate.refreshRequests)
    }

    @Test
    fun `committed switch is not successful until authoritative target matches`() = runTest {
        val stale = sampleSnapshot().copy(
            revision = 9L,
            program = sampleSnapshot().program!!.copy(missedDoseRecoveryEnabled = false)
        )
        val delegate = FakeOperations(
            mutationResult = DeviceDosingChannelCommittedResult(9L),
            refreshResult = DeviceDosingChannelOperationResult.Success(stale)
        )
        val operations = DeviceDosingReconciledChannelOperations(delegate)

        val result = operations.setMissedDoseRecoveryEnabled(DEVICE_UID, SLOT_ID, true)

        assertEquals(DeviceDosingChannelOperationResult.Failed, result)
        assertEquals(listOf(DEVICE_UID to SLOT_ID), delegate.refreshRequests)
    }

    @Test
    fun `committed reservoir save requires the same authoritative reservoir assignment`() = runTest {
        val settings = DeviceDosingReservoirSettings(
            trackingEnabled = true,
            capacityMicroliters = 500_000L,
            lowLevelAlertEnabled = true
        )
        val authoritative = sampleSnapshot().copy(
            revision = 11L,
            reservoir = sampleSnapshot().reservoir.copy(
                trackingEnabled = true,
                capacityMicroliters = 500_000L,
                remainingMicroliters = 250_000L,
                lowLevelAlertEnabled = true
            )
        )
        val delegate = FakeOperations(
            mutationResult = DeviceDosingChannelCommittedResult(11L),
            refreshResult = DeviceDosingChannelOperationResult.Success(authoritative)
        )
        val operations = DeviceDosingReconciledChannelOperations(delegate)

        val result = operations.applyReservoirSettingsAtRevision(
            deviceUid = DEVICE_UID,
            slotId = SLOT_ID,
            settings = settings,
            expectedRevision = 10L
        )

        assertTrue(result is DeviceDosingChannelOperationResult.Success)
        assertEquals(listOf(DEVICE_UID to SLOT_ID), delegate.refreshRequests)
        assertEquals(0, delegate.refreshAllCount)
    }

    private class FakeOperations(
        private val mutationResult: DeviceDosingChannelOperationResult,
        private val refreshResult: DeviceDosingChannelOperationResult
    ) : DeviceDosingChannelOperations by NoopOperations,
        DeviceDosingProgramRevisionOperations,
        DeviceDosingReservoirRevisionOperations {

        val refreshRequests = mutableListOf<Pair<String, String>>()
        var refreshAllCount: Int = 0

        override suspend fun refresh(
            deviceUid: String,
            slotId: String
        ): DeviceDosingChannelOperationResult {
            refreshRequests += deviceUid to slotId
            return refreshResult
        }

        override suspend fun refreshAll(deviceUid: String): Boolean {
            refreshAllCount += 1
            return true
        }

        override suspend fun applyProgram(
            deviceUid: String,
            slotId: String,
            program: DeviceDosingProgram
        ): DeviceDosingChannelOperationResult = mutationResult

        override suspend fun applyProgramAtRevision(
            deviceUid: String,
            slotId: String,
            program: DeviceDosingProgram,
            expectedRevision: Long
        ): DeviceDosingChannelOperationResult = mutationResult

        override suspend fun setMissedDoseRecoveryEnabled(
            deviceUid: String,
            slotId: String,
            enabled: Boolean
        ): DeviceDosingChannelOperationResult = mutationResult

        override suspend fun applyReservoirSettings(
            deviceUid: String,
            slotId: String,
            settings: DeviceDosingReservoirSettings
        ): DeviceDosingChannelOperationResult = mutationResult

        override suspend fun applyReservoirSettingsAtRevision(
            deviceUid: String,
            slotId: String,
            settings: DeviceDosingReservoirSettings,
            expectedRevision: Long
        ): DeviceDosingChannelOperationResult = mutationResult

        override suspend fun setReservoirLowLevelAlertEnabled(
            deviceUid: String,
            slotId: String,
            enabled: Boolean
        ): DeviceDosingChannelOperationResult = mutationResult

        override suspend fun reset(
            deviceUid: String,
            slotId: String
        ): DeviceDosingChannelOperationResult = mutationResult
    }

    private object NoopOperations : DeviceDosingChannelOperations {
        override fun observe(
            deviceUid: String,
            slotId: String
        ): Flow<DeviceDosingChannelSnapshot?> = flowOf(null)

        override suspend fun refresh(
            deviceUid: String,
            slotId: String
        ): DeviceDosingChannelOperationResult = DeviceDosingChannelOperationResult.Failed

        override suspend fun applyProgram(
            deviceUid: String,
            slotId: String,
            program: DeviceDosingProgram
        ): DeviceDosingChannelOperationResult = DeviceDosingChannelOperationResult.Failed

        override suspend fun setMissedDoseRecoveryEnabled(
            deviceUid: String,
            slotId: String,
            enabled: Boolean
        ): DeviceDosingChannelOperationResult = DeviceDosingChannelOperationResult.Failed

        override suspend fun applyReservoirSettings(
            deviceUid: String,
            slotId: String,
            settings: DeviceDosingReservoirSettings
        ): DeviceDosingChannelOperationResult = DeviceDosingChannelOperationResult.Failed

        override suspend fun refillReservoir(
            deviceUid: String,
            slotId: String
        ): DeviceDosingChannelOperationResult = DeviceDosingChannelOperationResult.Failed

        override suspend fun doseNow(
            deviceUid: String,
            slotId: String,
            amountMicroliters: Long
        ): DeviceDosingChannelOperationResult = DeviceDosingChannelOperationResult.Failed

        override suspend fun doseStop(
            deviceUid: String,
            slotId: String
        ): DeviceDosingChannelOperationResult = DeviceDosingChannelOperationResult.Failed

        override suspend fun reset(
            deviceUid: String,
            slotId: String
        ): DeviceDosingChannelOperationResult = DeviceDosingChannelOperationResult.Failed
    }

    private companion object {
        const val DEVICE_UID = "device-reconciliation"
        const val SLOT_ID = "dosing:channel2"
    }
}

private fun sampleSnapshot(): DeviceDosingChannelSnapshot {
    val program = DeviceDosingProgram(
        enabled = true,
        weekdays = List(7) { true },
        schedule = DeviceDosingProgramSchedule.Single(
            dailyDoseMicroliters = 3_000L,
            startTimeMillis = 28_800_000L
        ),
        missedDoseRecoveryEnabled = false
    )
    return DeviceDosingChannelSnapshot(
        deviceUid = "device-reconciliation",
        slotId = "dosing:channel2",
        pumpCount = 2,
        channelNumber = 2,
        channelTitle = "Channel 2",
        revision = 7L,
        runtimeEnabled = true,
        runtimeReason = DeviceDosingRuntimeReason.NONE,
        deliveryAccountingCertain = true,
        calibrated = true,
        lastCalibratedAtEpochSeconds = 100L,
        scheduling = DeviceDosingSchedulingPolicy(),
        program = program,
        progress = DeviceDosingChannelProgress(executionCurrent = true),
        reservoir = DeviceDosingReservoirSnapshot(
            trackingEnabled = true,
            capacityMicroliters = 450_000L,
            remainingMicroliters = 250_000L,
            lowLevelAlertEnabled = false
        ),
        activeRun = DeviceDosingActiveRun(),
        controls = DeviceDosingChannelControls(
            programEditable = true,
            reservoirEditable = true,
            resetSupported = true
        )
    )
}
