package com.aqua.aqualight.ui.tabs.devices.detail.cooling.automatic

import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAutomaticTemperaturePolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCoolingAutomaticSettingsUiStateTest {

    @Test
    fun unsupportedSilentModeIsPreviewOnlyAndDoesNotEnableSave() {
        val state = editableState(
            persistedSilentModeEnabled = null,
            draftSilentModeEnabled = true
        )

        assertTrue(state.hasPreviewOnlySilentModeChange)
        assertFalse(state.hasSilentModeChanges)
        assertFalse(state.hasChanges)
        assertFalse(state.canSave)
    }

    @Test
    fun firmwareBackedSilentModeChangeParticipatesInSaveState() {
        val state = editableState(
            persistedSilentModeEnabled = false,
            draftSilentModeEnabled = true
        )

        assertFalse(state.hasPreviewOnlySilentModeChange)
        assertTrue(state.hasSilentModeChanges)
        assertTrue(state.hasChanges)
        assertTrue(state.canSave)
    }

    private fun editableState(
        persistedSilentModeEnabled: Boolean?,
        draftSilentModeEnabled: Boolean
    ): DeviceCoolingAutomaticSettingsUiState = DeviceCoolingAutomaticSettingsUiState(
        editable = true,
        persistedStartTemperatureC = 25.0,
        persistedMaximumSpeedTemperatureC = 27.0,
        draftStartTemperatureC = 25.0,
        draftMaximumSpeedTemperatureC = 27.0,
        persistedSilentModeEnabled = persistedSilentModeEnabled,
        draftSilentModeEnabled = draftSilentModeEnabled,
        policy = DeviceCoolingAutomaticTemperaturePolicy(
            startMinimumC = 18.0,
            startMaximumC = 30.0,
            maximumSpeedMinimumC = 18.5,
            maximumSpeedMaximumC = 32.0,
            stepC = 0.5,
            minimumGapC = 0.5
        )
    )
}
