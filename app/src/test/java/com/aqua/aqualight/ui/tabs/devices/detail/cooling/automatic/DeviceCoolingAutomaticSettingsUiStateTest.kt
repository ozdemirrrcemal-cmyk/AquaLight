package com.aqua.aqualight.ui.tabs.devices.detail.cooling.automatic

import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAutomaticCommandResult
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAutomaticFailure
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAutomaticTemperaturePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCoolingAutomaticSettingsUiStateTest {

    @Test
    fun unsupportedSilentModeIsNotEditableAndDoesNotEnableSave() {
        val state = editableState(
            persistedSilentModeEnabled = null,
            draftSilentModeEnabled = true
        )

        assertFalse(state.silentModeFirmwareBacked)
        assertFalse(state.silentModeEditable)
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

        assertTrue(state.silentModeFirmwareBacked)
        assertTrue(state.silentModeEditable)
        assertTrue(state.hasSilentModeChanges)
        assertTrue(state.hasChanges)
        assertTrue(state.canSave)
    }

    @Test
    fun typedSaveFailurePreservesPersistedBaselineAndFailureReason() {
        val state = editableState(
            persistedSilentModeEnabled = null,
            draftSilentModeEnabled = false
        ).copy(draftStartTemperatureC = 25.5)
        val request = PendingAutomaticSettingsSave(
            deviceUid = "cooling-device",
            startTemperatureC = 25.5,
            maximumSpeedTemperatureC = 27.0,
            silentModeEnabled = null
        )

        val updated = state.afterSave(
            request = request,
            result = DeviceCoolingAutomaticCommandResult.Failed(
                DeviceCoolingAutomaticFailure.ReadOnly
            )
        )

        assertEquals(25.0, updated.persistedStartTemperatureC ?: 0.0, 0.0)
        assertEquals(25.5, updated.draftStartTemperatureC ?: 0.0, 0.0)
        assertEquals(DeviceCoolingAutomaticSaveState.ERROR, updated.saveState)
        assertEquals(DeviceCoolingAutomaticFailure.ReadOnly, updated.saveFailure)
    }

    @Test
    fun typedSaveSuccessAdvancesPersistedBaseline() {
        val state = editableState(
            persistedSilentModeEnabled = null,
            draftSilentModeEnabled = false
        ).copy(draftStartTemperatureC = 25.5)
        val request = PendingAutomaticSettingsSave(
            deviceUid = "cooling-device",
            startTemperatureC = 25.5,
            maximumSpeedTemperatureC = 27.0,
            silentModeEnabled = null
        )

        val updated = state.afterSave(
            request = request,
            result = DeviceCoolingAutomaticCommandResult.Success
        )

        assertEquals(25.5, updated.persistedStartTemperatureC ?: 0.0, 0.0)
        assertEquals(DeviceCoolingAutomaticSaveState.SAVED, updated.saveState)
        assertEquals(null, updated.saveFailure)
    }

    private fun editableState(
        persistedSilentModeEnabled: Boolean?,
        draftSilentModeEnabled: Boolean
    ): DeviceCoolingAutomaticSettingsUiState = DeviceCoolingAutomaticSettingsUiState(
        loadState = DeviceCoolingAutomaticLoadState.CONTENT,
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
