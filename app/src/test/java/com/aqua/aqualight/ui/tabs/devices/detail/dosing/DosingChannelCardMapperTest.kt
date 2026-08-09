package com.aqua.aqualight.ui.tabs.devices.detail.dosing

import com.aqua.aqualight.application.devices.DeviceChannelWireKey
import com.aqua.aqualight.application.devices.DeviceDosingChannelSlot
import com.aqua.aqualight.application.devices.DeviceSlotIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DosingChannelCardMapperTest {

    @Test
    fun `initial card identity and empty dosing summary come from exact catalog slot`() {
        val slot = DeviceDosingChannelSlot(
            index = DeviceSlotIndex(1),
            wireKey = DeviceChannelWireKey("channel2"),
            defaultDisplayName = "Channel 2",
            displayNameEditable = true
        )

        val state = slot.toInitialDosingChannelCardUiState()

        assertEquals("dosing:channel2", state.slotId)
        assertEquals(2, state.channelNumber)
        assertEquals("channel2", state.wireKey)
        assertEquals("Channel 2", state.displayName)
        assertEquals(DosingChannelVisualState.NOT_CONFIGURED, state.visualState)
        assertTrue(state.scheduleDays.isEveryDay)
        assertEquals(7, state.scheduleDays.selectedDays.size)
        assertEquals(0.0, state.doseProgress.dailyDoseMl, 0.0)
        assertEquals(0.0, state.doseProgress.deliveredTodayMl, 0.0)
        assertTrue(state.doseProgress.doseMilestonesMl.isEmpty())
        assertEquals(DosingDoseProgressVisualState.EMPTY, state.doseProgress.visualState)
    }
}
