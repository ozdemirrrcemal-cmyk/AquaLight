package com.aqua.aqualight.ui.tabs.devices.detail.cooling.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCoolingProgramSettingsViewModelTest {

    @Test
    fun startsWithoutFixedProgramPeriods() {
        val viewModel = DeviceCoolingProgramSettingsViewModel()

        assertTrue(viewModel.uiState.value.slots.isEmpty())
        assertFalse(viewModel.uiState.value.hasChanges)
    }

    @Test
    fun rejectsPartiallyOverlappingProgramRange() {
        val viewModel = DeviceCoolingProgramSettingsViewModel()
        viewModel.addTimeSlot()
        viewModel.addTimeSlot()
        configurePeriod(viewModel, FIRST_PERIOD_ID, hour(8), hour(14))
        configurePeriod(viewModel, SECOND_PERIOD_ID, hour(14), hour(16))

        assertFalse(viewModel.updateStartTime(SECOND_PERIOD_ID, hour(12)))

        val second = viewModel.slot(SECOND_PERIOD_ID)
        assertEquals(hour(14), second.startMinutes)
        assertEquals(hour(16), second.endMinutes)
    }

    @Test
    fun allowsProgramsToTouchAtTheirBoundaries() {
        val viewModel = DeviceCoolingProgramSettingsViewModel()
        viewModel.addTimeSlot()
        viewModel.addTimeSlot()
        configurePeriod(viewModel, FIRST_PERIOD_ID, hour(8), hour(14))
        configurePeriod(viewModel, SECOND_PERIOD_ID, hour(14), hour(20))
        val boundary = hour(14) + 30

        assertTrue(viewModel.updateStartTime(SECOND_PERIOD_ID, boundary))
        assertTrue(viewModel.updateEndTime(FIRST_PERIOD_ID, boundary))

        assertEquals(boundary, viewModel.slot(FIRST_PERIOD_ID).endMinutes)
        assertEquals(boundary, viewModel.slot(SECOND_PERIOD_ID).startMinutes)
    }

    @Test
    fun handlesOvernightRangesWhenCheckingOverlap() {
        val viewModel = DeviceCoolingProgramSettingsViewModel()
        viewModel.addTimeSlot()
        viewModel.addTimeSlot()
        configurePeriod(viewModel, FIRST_PERIOD_ID, hour(8), hour(14))

        assertTrue(viewModel.updateStartTime(SECOND_PERIOD_ID, hour(23) + 30))
        assertTrue(viewModel.updateEndTime(SECOND_PERIOD_ID, hour(8)))

        val adjacentOvernight = viewModel.slot(SECOND_PERIOD_ID)
        assertEquals(hour(23) + 30, adjacentOvernight.startMinutes)
        assertEquals(hour(8), adjacentOvernight.endMinutes)

        assertFalse(viewModel.updateEndTime(SECOND_PERIOD_ID, hour(9)))
        assertEquals(hour(8), viewModel.slot(SECOND_PERIOD_ID).endMinutes)
    }

    @Test
    fun deletesPersistedUserPeriodAndMarksDraftDirty() {
        val viewModel = DeviceCoolingProgramSettingsViewModel()

        viewModel.addTimeSlot()
        viewModel.saveDraft()
        assertFalse(viewModel.uiState.value.hasChanges)

        assertTrue(viewModel.deleteTimeSlot(FIRST_PERIOD_ID))

        assertTrue(viewModel.uiState.value.slots.isEmpty())
        assertNull(viewModel.uiState.value.selectedSlotId)
        assertTrue(viewModel.uiState.value.hasChanges)
        assertEquals(DeviceCoolingProgramSaveState.IDLE, viewModel.uiState.value.saveState)
    }

    @Test
    fun fanLimitIsClampedAndSnappedToFivePercentSteps() {
        val viewModel = DeviceCoolingProgramSettingsViewModel()
        viewModel.addTimeSlot()

        viewModel.updateFanLimit(FIRST_PERIOD_ID, 63)
        assertEquals(65, viewModel.slot(FIRST_PERIOD_ID).fanLimitPercent)

        viewModel.updateFanLimit(FIRST_PERIOD_ID, -20)
        assertEquals(0, viewModel.slot(FIRST_PERIOD_ID).fanLimitPercent)

        viewModel.updateFanLimit(FIRST_PERIOD_ID, 120)
        assertEquals(100, viewModel.slot(FIRST_PERIOD_ID).fanLimitPercent)
    }

    private fun configurePeriod(
        viewModel: DeviceCoolingProgramSettingsViewModel,
        slotId: String,
        startMinutes: Int,
        endMinutes: Int
    ) {
        assertTrue(viewModel.updateStartTime(slotId, startMinutes))
        assertTrue(viewModel.updateEndTime(slotId, endMinutes))
    }

    private fun DeviceCoolingProgramSettingsViewModel.slot(
        slotId: String
    ): DeviceCoolingProgramSlot = uiState.value.slots.first { slot -> slot.id == slotId }

    private companion object {
        const val FIRST_PERIOD_ID = "period-1"
        const val SECOND_PERIOD_ID = "period-2"
        const val MINUTES_PER_HOUR = 60

        fun hour(value: Int): Int = value * MINUTES_PER_HOUR
    }
}
