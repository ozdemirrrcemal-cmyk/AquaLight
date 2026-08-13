package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel

import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperationResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelProgramSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgramDraft
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgramDraftConfig
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgramDraftMode
import com.aqua.aqualight.application.devices.dosing.DeviceDosingReservoirSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingSchedulingConstraints
import com.aqua.aqualight.application.devices.dosing.DeviceDosingUsageSnapshot
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.detail.DeviceDosingChannelDetailViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.plan.DosingPlanDraft
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.plan.DosingPlanScheduleMode
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.plan.DosingPlanScheduleUpdate
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.plan.DeviceDosingPlanViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.reservoir.DeviceDosingReservoirDraft
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.reservoir.DeviceDosingReservoirViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class DosingDraftViewModelBoundaryTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `plan owns one restored draft and subsequent edits`() = runTest {
        val viewModel = DeviceDosingPlanViewModel(FakeDosingChannelOperations(snapshot()))
        viewModel.bind(
            DEVICE_UID,
            SLOT_ID,
            DosingPlanDraft(distributedDailyDoseMicroliters = 2_000L)
        )
        viewModel.bind(
            DEVICE_UID,
            SLOT_ID,
            DosingPlanDraft(distributedDailyDoseMicroliters = 9_000L)
        )
        advanceUntilIdle()

        viewModel.setDailyDoseMicroliters(3_000L)
        viewModel.applyScheduleUpdate(DosingPlanScheduleUpdate.Hourly(3_600_000L))

        val draft = viewModel.currentDraft()
        assertEquals(3_000L, draft.distributedDailyDoseMicroliters)
        assertEquals(3_600_000L, draft.hourlyStartTimeMs)
        assertEquals(DosingPlanScheduleMode.HOURLY, draft.selectedScheduleMode)
    }

    @Test
    fun `reservoir validates restored draft before accepting state`() = runTest {
        val viewModel = DeviceDosingReservoirViewModel(FakeDosingChannelOperations(snapshot()))
        viewModel.bind(
            DEVICE_UID,
            SLOT_ID,
            DeviceDosingReservoirDraft(reservoirCapacityMl = 450.0)
        )
        advanceUntilIdle()

        viewModel.setCapacityMl(-1.0)
        assertEquals(450.0, viewModel.currentDraft().reservoirCapacityMl ?: error("missing capacity"), 0.0)

        viewModel.setCapacityMl(800.0)
        viewModel.setTrackingEnabled(true)
        assertEquals(800.0, viewModel.currentDraft().reservoirCapacityMl ?: error("missing capacity"), 0.0)
        assertTrue(viewModel.currentDraft().trackingEnabled)
    }

    @Test
    fun `detail owns route validity and canonical missed-dose setting`() = runTest {
        val invalid = DeviceDosingChannelDetailViewModel(
            FakeDosingChannelOperations(snapshot(calibrated = false, lastCalibratedAt = 0L))
        )
        invalid.bind(DEVICE_UID, SLOT_ID, routeCalibrationEpochSeconds = 0L)
        advanceUntilIdle()
        assertFalse(invalid.currentDraft().routeValid)

        val operations = FakeDosingChannelOperations(snapshot())
        val valid = DeviceDosingChannelDetailViewModel(operations)
        valid.bind(DEVICE_UID, SLOT_ID, routeCalibrationEpochSeconds = 100L)
        advanceUntilIdle()
        assertTrue(valid.currentDraft().routeValid)
        assertFalse(valid.currentDraft().missedDoseRecoveryEnabled)

        valid.setMissedDoseRecoveryEnabled(true)
        advanceUntilIdle()
        assertTrue(valid.currentDraft().missedDoseRecoveryEnabled)
    }

    class MainDispatcherRule(
        private val dispatcher: TestDispatcher = UnconfinedTestDispatcher()
    ) : TestWatcher() {
        override fun starting(description: Description) {
            Dispatchers.setMain(dispatcher)
        }

        override fun finished(description: Description) {
            Dispatchers.resetMain()
        }
    }

    private companion object {
        const val DEVICE_UID = "device-1"
        const val SLOT_ID = "dosing:channel1"
    }
}

private class FakeDosingChannelOperations(
    initial: DeviceDosingChannelSnapshot
) : DeviceDosingChannelOperations {
    private val state = MutableStateFlow<DeviceDosingChannelSnapshot?>(initial)

    override fun observe(deviceUid: String, slotId: String): Flow<DeviceDosingChannelSnapshot?> = state

    override suspend fun refresh(
        deviceUid: String,
        slotId: String
    ): DeviceDosingChannelOperationResult = success()

    override suspend fun saveProgram(
        deviceUid: String,
        slotId: String,
        program: DeviceDosingProgramDraft
    ): DeviceDosingChannelOperationResult {
        val current = current()
        state.value = current.copy(
            program = DeviceDosingChannelProgramSnapshot(
                revision = current.revision + 1L,
                enabled = program.enabled,
                weekdays = program.weekdays,
                mode = program.mode,
                missedDoseRecoveryEnabled = program.missedDoseRecoveryEnabled,
                config = program.config
            )
        )
        return success()
    }

    override suspend fun setMissedDoseRecoveryEnabled(
        deviceUid: String,
        slotId: String,
        enabled: Boolean
    ): DeviceDosingChannelOperationResult {
        val current = current()
        state.value = current.copy(
            program = current.program?.copy(missedDoseRecoveryEnabled = enabled)
        )
        return success()
    }

    override suspend fun dispenseManualDose(
        deviceUid: String,
        slotId: String,
        amountMl: Double
    ): DeviceDosingChannelOperationResult = success()

    override suspend fun resetChannel(
        deviceUid: String,
        slotId: String
    ): DeviceDosingChannelOperationResult = success()

    override suspend fun saveReservoir(
        deviceUid: String,
        slotId: String,
        trackingEnabled: Boolean,
        capacityMl: Double?
    ): DeviceDosingChannelOperationResult {
        val current = current()
        state.value = current.copy(
            reservoir = current.reservoir.copy(
                trackingEnabled = trackingEnabled,
                capacityMl = capacityMl
            )
        )
        return success()
    }

    override suspend fun refillReservoir(
        deviceUid: String,
        slotId: String
    ): DeviceDosingChannelOperationResult = success()

    private fun current(): DeviceDosingChannelSnapshot =
        state.value ?: error("Fake Dosing snapshot is missing")

    private fun success(): DeviceDosingChannelOperationResult =
        DeviceDosingChannelOperationResult.Success(current())
}

private fun snapshot(
    calibrated: Boolean = true,
    lastCalibratedAt: Long = 100L
): DeviceDosingChannelSnapshot = DeviceDosingChannelSnapshot(
    deviceUid = "device-1",
    slotId = "dosing:channel1",
    channelKey = "channel1",
    revision = 7L,
    channelTitle = "Nutrients",
    calibrated = calibrated,
    lastCalibratedAt = lastCalibratedAt,
    runtimeEnabled = true,
    runtimeReason = "none",
    program = DeviceDosingChannelProgramSnapshot(
        revision = 7L,
        enabled = true,
        weekdays = List(7) { true },
        mode = DeviceDosingProgramDraftMode.SINGLE,
        missedDoseRecoveryEnabled = false,
        config = DeviceDosingProgramDraftConfig.Distributed(
            dailyDoseMl = 2.0,
            startTimeMs = 3_600_000L
        )
    ),
    scheduling = DeviceDosingSchedulingConstraints(
        amountResolutionMl = 0.001,
        maxEventsPerChannel = 24,
        maxCustomPeriodsPerChannel = 8,
        missedDoseRecoveryWindowMs = 900_000L,
        minPumpRunDurationMs = 50L,
        maxPumpRunDurationMs = 3_600_000L,
        maxManualDoseMl = 1_000.0,
        supportsMissedDoseRecovery = true,
        supportsChannelReset = true,
        effectiveScheduledDoseMinMl = 0.05,
        effectiveScheduledDoseMaxMl = 100.0
    ),
    usageToday = DeviceDosingUsageSnapshot(
        localDate = null,
        scheduledDeliveredMl = 0.0,
        manualDeliveredMl = 0.0,
        totalDeliveredMl = 0.0
    ),
    reservoir = DeviceDosingReservoirSnapshot(
        trackingEnabled = false,
        capacityMl = 450.0,
        remainingMl = 400.0,
        accountingCertain = true,
        remainingPercent = 88.888
    ),
    active = false
)
