package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model

import com.aqua.aqualight.data.devices.light.programs.capability.LightProgramFirmwareCapabilities
import com.aqua.aqualight.data.devices.light.programs.model.RepeatMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceLightProgramEditorUiStateTest {

    @Test
    fun currentEsp32FirmwareKeepsEveryDayRepeatLocked() {
        val state = DeviceLightProgramEditorUiState.default()

        assertEquals(RepeatMode.EVERY, state.repeatMode)
        assertEquals(setOf(1, 2, 3, 4, 5, 6, 7), state.selectedDays)
        assertFalse(state.repeatSelectionEnabled)
        assertNotNull(state.repeatUnavailableReason)
    }

    @Test
    fun futureFirmwareCapabilityCanEnableWeeklyRepeatWithoutChangingUiModel() {
        val state = DeviceLightProgramEditorUiState.default(
            capabilities = LightProgramFirmwareCapabilities(
                supportsWeeklySchedule = true,
                supportsNativeTransition = false
            )
        )

        assertEquals(RepeatMode.EVERY, state.repeatMode)
        assertEquals(setOf(1, 2, 3, 4, 5, 6, 7), state.selectedDays)
        assertTrue(state.repeatSelectionEnabled)
        assertNull(state.repeatUnavailableReason)
    }
}
