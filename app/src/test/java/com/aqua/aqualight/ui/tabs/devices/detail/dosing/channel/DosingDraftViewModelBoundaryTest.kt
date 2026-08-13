package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel

import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.detail.DeviceDosingChannelDetailViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.plan.DosingPlanDraft
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.plan.DosingPlanScheduleMode
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.plan.DosingPlanScheduleUpdate
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.plan.DeviceDosingPlanViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.reservoir.DeviceDosingReservoirDraft
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.reservoir.DeviceDosingReservoirViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DosingDraftViewModelBoundaryTest {

    @Test
    fun `plan owns one restored draft and subsequent edits`() {
        val viewModel = DeviceDosingPlanViewModel()
        viewModel.bindInitial(DosingPlanDraft(distributedDailyDoseMicroliters = 2_000L))
        viewModel.bindInitial(DosingPlanDraft(distributedDailyDoseMicroliters = 9_000L))

        viewModel.setDailyDoseMicroliters(3_000L)
        viewModel.applyScheduleUpdate(DosingPlanScheduleUpdate.Hourly(3_600_000L))

        val draft = viewModel.currentDraft()
        assertEquals(3_000L, draft.distributedDailyDoseMicroliters)
        assertEquals(3_600_000L, draft.hourlyStartTimeMs)
        assertEquals(DosingPlanScheduleMode.HOURLY, draft.selectedScheduleMode)
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
    fun `detail owns route validity and pending setting draft`() {
        val invalid = DeviceDosingChannelDetailViewModel()
        invalid.bind(lastCalibratedAtEpochSeconds = 0L, restoredMissedDoseRecoveryEnabled = false)
        assertFalse(invalid.currentDraft().routeValid)

        val valid = DeviceDosingChannelDetailViewModel()
        valid.bind(lastCalibratedAtEpochSeconds = 100L, restoredMissedDoseRecoveryEnabled = false)
        valid.setMissedDoseRecoveryEnabled(true)
        assertTrue(valid.currentDraft().routeValid)
        assertTrue(valid.currentDraft().missedDoseRecoveryEnabled)
    }
}
