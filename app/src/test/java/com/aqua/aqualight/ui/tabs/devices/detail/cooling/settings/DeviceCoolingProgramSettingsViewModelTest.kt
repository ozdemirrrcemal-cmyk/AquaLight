package com.aqua.aqualight.ui.tabs.devices.detail.cooling.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCoolingProgramSettingsViewModelTest {

    @Test
    fun rejectsPartiallyOverlappingProgramRange() {
        val viewModel = DeviceCoolingProgramSettingsViewModel()

        assertTrue(viewModel.updateEndTime(INTENSIVE_SLOT_ID, hour(16)))
        assertFalse(viewModel.updateStartTime(INTENSIVE_SLOT_ID, hour(12)))

        val intensive = viewModel.slot(INTENSIVE_SLOT_ID)
        assertEquals(hour(14), intensive.startMinutes)
        assertEquals(hour(16), intensive.endMinutes)
    }

    @Test
    fun allowsProgramsToTouchAtTheirBoundaries() {
        val viewModel = DeviceCoolingProgramSettingsViewModel()
        val boundary = hour(14) + 30

        assertTrue(viewModel.updateStartTime(INTENSIVE_SLOT_ID, boundary))
        assertTrue(viewModel.updateEndTime(QUIET_SLOT_ID, boundary))

        assertEquals(boundary, viewModel.slot(QUIET_SLOT_ID).endMinutes)
        assertEquals(boundary, viewModel.slot(INTENSIVE_SLOT_ID).startMinutes)
    }

    @Test
    fun handlesOvernightRangesWhenCheckingOverlap() {
        val viewModel = DeviceCoolingProgramSettingsViewModel()

        viewModel.addTimeSlot()
        assertTrue(viewModel.updateStartTime(CUSTOM_SLOT_ID, hour(23) + 30))
        assertTrue(viewModel.updateEndTime(CUSTOM_SLOT_ID, hour(8)))

        val adjacentOvernight = viewModel.slot(CUSTOM_SLOT_ID)
        assertEquals(hour(23) + 30, adjacentOvernight.startMinutes)
        assertEquals(hour(8), adjacentOvernight.endMinutes)

        assertFalse(viewModel.updateEndTime(CUSTOM_SLOT_ID, hour(9)))

        assertEquals(hour(8), viewModel.slot(CUSTOM_SLOT_ID).endMinutes)
    }

    @Test
    fun deletesPersistedCustomProgramAndMarksDraftDirty() {
        val viewModel = DeviceCoolingProgramSettingsViewModel()

        viewModel.addTimeSlot()
        viewModel.saveDraft()
        assertFalse(viewModel.uiState.value.hasChanges)

        assertTrue(viewModel.deleteTimeSlot(CUSTOM_SLOT_ID))

        assertFalse(viewModel.uiState.value.slots.any { slot -> slot.id == CUSTOM_SLOT_ID })
        assertNull(viewModel.uiState.value.selectedSlotId)
        assertTrue(viewModel.uiState.value.hasChanges)
        assertEquals(DeviceCoolingProgramSaveState.IDLE, viewModel.uiState.value.saveState)
    }

    @Test
    fun doesNotDeleteBuiltInPrograms() {
        val viewModel = DeviceCoolingProgramSettingsViewModel()

        assertFalse(viewModel.deleteTimeSlot(QUIET_SLOT_ID))

        assertEquals(DeviceCoolingProgramSlotLabel.QUIET, viewModel.slot(QUIET_SLOT_ID).label)
        assertFalse(viewModel.uiState.value.hasChanges)
    }

    private fun DeviceCoolingProgramSettingsViewModel.slot(
        slotId: String
    ): DeviceCoolingProgramSlot = uiState.value.slots.first { slot -> slot.id == slotId }

    private companion object {
        const val QUIET_SLOT_ID = "quiet"
        const val INTENSIVE_SLOT_ID = "intensive"
        const val CUSTOM_SLOT_ID = "custom-1"
        const val MINUTES_PER_HOUR = 60

        fun hour(value: Int): Int = value * MINUTES_PER_HOUR
    }
}
