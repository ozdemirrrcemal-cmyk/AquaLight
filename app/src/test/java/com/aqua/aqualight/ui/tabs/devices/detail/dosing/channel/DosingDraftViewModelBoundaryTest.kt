package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel

import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgramSchedule
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.detail.DeviceDosingChannelDetailViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.plan.DosingPlanDraft
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.plan.DosingPlanScheduleMode
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.plan.DosingPlanScheduleUpdate
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.plan.DeviceDosingPlanViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.reservoir.DeviceDosingReservoirDraft
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.reservoir.DeviceDosingReservoirViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DosingDraftViewModelBoundaryTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `plan owns restored draft and saves fixture mutation`() = runTest(dispatcher) {
        val operations = FakeDeviceDosingChannelOperations()
        val viewModel = DeviceDosingPlanViewModel(operations)
        viewModel.bind(
            deviceUidText = "device-1",
            slotIdText = "dosing:channel2",
            restoredDraft = DosingPlanDraft(distributedDailyDoseMicroliters = 2_000L)
        )
        viewModel.bind(
            deviceUidText = "device-1",
            slotIdText = "dosing:channel2",
            restoredDraft = DosingPlanDraft(distributedDailyDoseMicroliters = 9_000L)
        )

        viewModel.setDailyDoseMicroliters(3_000L)
        viewModel.applyScheduleUpdate(DosingPlanScheduleUpdate.Hourly(3_600_000L))
        viewModel.save()

        val draft = viewModel.currentDraft()
        assertEquals(3_000L, draft.distributedDailyDoseMicroliters)
        assertEquals(3_600_000L, draft.hourlyStartTimeMs)
        assertEquals(DosingPlanScheduleMode.HOURLY, draft.selectedScheduleMode)
        val savedSchedule = operations.lastProgram?.schedule as? DeviceDosingProgramSchedule.Hourly24
        assertNotNull(savedSchedule)
        assertEquals(3_000L, savedSchedule?.dailyDoseMicroliters)
        assertTrue(viewModel.currentEditorState().canSave)
    }

    @Test
    fun `disabled valid plan remains saveable`() = runTest(dispatcher) {
        val operations = FakeDeviceDosingChannelOperations()
        val viewModel = DeviceDosingPlanViewModel(operations)
        viewModel.bind("device-1", "dosing:channel2", restoredDraft = null)

        viewModel.setScheduleEnabled(false)

        assertTrue(viewModel.currentEditorState().canSave)
        viewModel.save()
        assertFalse(operations.lastProgram?.enabled ?: true)
    }

    @Test
    fun `reservoir validates draft before accepting state`() {
        val viewModel = DeviceDosingReservoirViewModel()
        viewModel.bindInitial(DeviceDosingReservoirDraft(reservoirCapacityMl = 450.0))

        viewModel.setCapacityMl(-1.0)
        assertEquals(450.0, viewModel.currentDraft().reservoirCapacityMl, 0.0)

        viewModel.setCapacityMl(800.0)
        viewModel.setTrackingEnabled(true)
        assertEquals(800.0, viewModel.currentDraft().reservoirCapacityMl, 0.0)
        assertTrue(viewModel.currentDraft().trackingEnabled)
    }

    @Test
    fun `detail owns route validity and delegates setting mutation`() = runTest(dispatcher) {
        val operations = FakeDeviceDosingChannelOperations(initialSnapshot = null)
        val invalid = DeviceDosingChannelDetailViewModel(operations)
        invalid.bind(
            deviceUidText = "device-1",
            slotIdText = "dosing:channel2",
            lastCalibratedAtEpochSeconds = 0L,
            restoredMissedDoseRecoveryEnabled = false
        )
        assertFalse(invalid.currentDraft().routeValid)

        val validOperations = FakeDeviceDosingChannelOperations()
        val valid = DeviceDosingChannelDetailViewModel(validOperations)
        valid.bind(
            deviceUidText = "device-1",
            slotIdText = "dosing:channel2",
            lastCalibratedAtEpochSeconds = 100L,
            restoredMissedDoseRecoveryEnabled = false
        )
        valid.setMissedDoseRecoveryEnabled(true)
        assertTrue(valid.currentDraft().routeValid)
        assertTrue(valid.currentDraft().missedDoseRecoveryEnabled)
        assertEquals(true, validOperations.lastMissedDoseRecoveryEnabled)

        valid.startManualDose(2_500L)
        assertEquals(2_500L, validOperations.lastManualDoseMicroliters)
        assertTrue(valid.currentDraft().manualDoseActive)

        valid.stopManualDose()
        assertFalse(valid.currentDraft().manualDoseActive)

        valid.resetChannel()
        assertFalse(valid.currentDraft().routeValid)
    }
}
