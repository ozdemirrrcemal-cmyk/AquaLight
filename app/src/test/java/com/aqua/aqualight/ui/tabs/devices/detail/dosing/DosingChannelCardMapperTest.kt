package com.aqua.aqualight.ui.tabs.devices.detail.dosing

import com.aqua.aqualight.application.devices.DeviceChannelWireKey
import com.aqua.aqualight.application.devices.DeviceDosingChannelSlot
import com.aqua.aqualight.application.devices.DeviceSlotIndex
import org.junit.Assert.assertEquals
import org.junit.Test

class DosingChannelCardMapperTest {

    @Test
    fun `initial card identity is inherited from exact catalog slot`() {
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
        assertEquals(DosingCalibrationUiState.REQUIRED, state.calibrationState)
        assertEquals(DosingSetupUiState.NOT_CONFIGURED, state.setupState)
        assertEquals(DosingChannelVisualState.SETUP_REQUIRED, state.visualState)
        assertEquals(DosingTimelineVisualState.EMPTY, state.timeline.visualState)
    }
}
