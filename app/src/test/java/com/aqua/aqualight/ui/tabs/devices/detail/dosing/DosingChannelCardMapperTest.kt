package com.aqua.aqualight.ui.tabs.devices.detail.dosing

import com.aqua.aqualight.application.devices.DeviceChannelWireKey
import com.aqua.aqualight.application.devices.DeviceDosingChannelSlot
import com.aqua.aqualight.application.devices.DeviceSlotIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DosingChannelCardMapperTest {

    @Test
    fun `initial card identity preserves an unconfigured presentation without runtime placeholders`() {
        val slot = DeviceDosingChannelSlot(
            index = DeviceSlotIndex(1),
            wireKey = DeviceChannelWireKey("channel2"),
            defaultDisplayName = "Channel 2",
            displayNameEditable = true
        )

        val state = slot.toInitialDosingChannelCardUiState()

        assertEquals("dosing:channel2", state.slotId)
        assertEquals(2, state.channelNumber)
        assertEquals("Channel 2", state.displayName)
        assertEquals(0.0, state.dailyDoseMl, 0.0)
        assertEquals(DosingChannelVisualState.NOT_CONFIGURED, state.visualState)
        assertFalse(state.scheduleDays.isEveryDay)
        assertTrue(state.scheduleDays.selectedDays.isEmpty())
    }
}
