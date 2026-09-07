package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.automatic

import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAutomaticCommandResult
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAutomaticFailure
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAutomaticSettingsSnapshot
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAutomaticTemperaturePolicy
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingDataFreshness
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingDataState
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingMutationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        val state = editableState(null, false).copy(draftStartTemperatureC = 25.5)
        val request = pendingRequest(startTemperatureC = 25.5)

        val updated = state.afterSave(
            request = request,
            result = DeviceCoolingAutomaticCommandResult.Failed(
                DeviceCoolingAutomaticFailure.ReadOnly
            )
        )

        assertEquals(25.0, updated.persistedStartTemperatureC ?: 0.0, 0.0)
        assertEquals(25.5, updated.draftStartTemperatureC ?: 0.0, 0.0)
        assertEquals(DeviceCoolingAutomaticSaveState.IDLE, updated.saveState)
        assertEquals(DeviceCoolingAutomaticFailure.ReadOnly, updated.saveFailure)
        assertTrue(updated.mutationState is CoolingMutationState.OperationError)
    }

    @Test
    fun typedSaveSuccessAdvancesPersistedBaseline() {
        val state = editableState(null, false).copy(draftStartTemperatureC = 25.5)

        val updated = state.afterSave(
            request = pendingRequest(startTemperatureC = 25.5),
            result = DeviceCoolingAutomaticCommandResult.Success
        )

        assertEquals(25.5, updated.persistedStartTemperatureC ?: 0.0, 0.0)
        assertEquals(DeviceCoolingAutomaticSaveState.SAVED, updated.saveState)
        assertNull(updated.saveFailure)
        assertEquals(CoolingMutationState.Saved, updated.mutationState)
    }

    @Test
    fun invalidConfigurationSaveBecomesValidationError() {
        val state = editableState(null, false).copy(draftStartTemperatureC = 25.5)

        val updated = state.afterSave(
            pendingRequest(startTemperatureC = 25.5),
            DeviceCoolingAutomaticCommandResult.Failed(
                DeviceCoolingAutomaticFailure.InvalidConfiguration
            )
        )

        assertEquals(DeviceCoolingAutomaticSaveState.IDLE, updated.saveState)
        assertEquals(CoolingMutationState.ValidationError, updated.mutationState)
        assertEquals(DeviceCoolingAutomaticFailure.InvalidConfiguration, updated.saveFailure)
    }

    @Test
    fun refreshKeepsAuthoritativeSnapshotWithoutFlickerAndDisablesWrites() {
        val refreshing = editableState(null, false).beginRefresh()
        val data = refreshing.dataState as CoolingDataState.Content<
            DeviceCoolingAutomaticSettingsSnapshot,
            DeviceCoolingAutomaticFailure
            >

        assertEquals(CoolingDataFreshness.REFRESHING, data.freshness)
        assertEquals(25.0, refreshing.persistedStartTemperatureC ?: 0.0, 0.0)
        assertEquals(27.0, refreshing.persistedMaximumSpeedTemperatureC ?: 0.0, 0.0)
        assertFalse(refreshing.editable)
        assertFalse(refreshing.canSave)
    }

    @Test
    fun transientRefreshFailureKeepsLastAuthoritativeSnapshotStale() {
        val stale = editableState(null, false)
            .beginRefresh()
            .afterRefreshFailure(DeviceCoolingAutomaticFailure.TemporaryFailure)
        val data = stale.dataState as CoolingDataState.Content<
            DeviceCoolingAutomaticSettingsSnapshot,
            DeviceCoolingAutomaticFailure
            >

        assertEquals(CoolingDataFreshness.STALE, data.freshness)
        assertEquals(DeviceCoolingAutomaticFailure.TemporaryFailure, data.refreshFailure)
        assertEquals(25.0, stale.persistedStartTemperatureC ?: 0.0, 0.0)
        assertFalse(stale.editable)
    }

    @Test
    fun partialIncomingSnapshotCannotReplaceLastValidatedConfiguration() {
        val current = editableState(null, false)
        val partial = current.withSnapshot(
            automaticSnapshot().copy(
                startTemperatureC = 26.0,
                maximumSpeedTemperatureC = null
            )
        )
        val data = partial.dataState as CoolingDataState.Content<
            DeviceCoolingAutomaticSettingsSnapshot,
            DeviceCoolingAutomaticFailure
            >

        assertEquals(CoolingDataFreshness.STALE, data.freshness)
        assertEquals(DeviceCoolingAutomaticFailure.InvalidConfiguration, data.refreshFailure)
        assertEquals(25.0, partial.persistedStartTemperatureC ?: 0.0, 0.0)
        assertEquals(27.0, partial.persistedMaximumSpeedTemperatureC ?: 0.0, 0.0)
        assertFalse(partial.editable)
    }

    @Test
    fun unsupportedRefreshIsTerminalAndClearsOldConfiguration() {
        val unsupported = editableState(null, false)
            .beginRefresh()
            .afterRefreshFailure(DeviceCoolingAutomaticFailure.Unsupported)

        assertEquals(CoolingDataState.Unsupported, unsupported.dataState)
        assertNull(unsupported.persistedStartTemperatureC)
        assertNull(unsupported.persistedMaximumSpeedTemperatureC)
        assertFalse(unsupported.editable)
    }

    @Test
    fun continuousRuntimeFanPercentReachesAutomaticPresentationWithoutQuantization() {
        val state = DeviceCoolingAutomaticSettingsUiState().withSnapshot(
            automaticSnapshot().copy(fanPercentNow = CONTINUOUS_AUTOMATIC_PERCENT)
        )

        assertEquals(CONTINUOUS_AUTOMATIC_PERCENT, state.fanPercentNow ?: 0.0, 0.0)
    }

    @Test
    fun invalidRuntimeFanPercentFailsAtApplicationBoundary() {
        assertTrue(
            runCatching {
                automaticSnapshot().copy(fanPercentNow = 100.01)
            }.isFailure
        )
    }

    private fun editableState(
        persistedSilentModeEnabled: Boolean?,
        draftSilentModeEnabled: Boolean
    ): DeviceCoolingAutomaticSettingsUiState {
        val snapshot = automaticSnapshot().copy(
            silentModeEnabled = persistedSilentModeEnabled
        )
        return DeviceCoolingAutomaticSettingsUiState(
            dataState = CoolingDataState.Content(snapshot),
            editable = true,
            persistedStartTemperatureC = 25.0,
            persistedMaximumSpeedTemperatureC = 27.0,
            draftStartTemperatureC = 25.0,
            draftMaximumSpeedTemperatureC = 27.0,
            persistedSilentModeEnabled = persistedSilentModeEnabled,
            draftSilentModeEnabled = draftSilentModeEnabled,
            silentModeMaximumFanPercent = snapshot.silentModeMaximumFanPercent,
            policy = snapshot.policy
        )
    }

    private fun automaticSnapshot() = DeviceCoolingAutomaticSettingsSnapshot(
        available = true,
        loaded = true,
        editable = true,
        startTemperatureC = 25.0,
        maximumSpeedTemperatureC = 27.0,
        silentModeMaximumFanPercent = 50,
        policy = DeviceCoolingAutomaticTemperaturePolicy(
            startMinimumC = 18.0,
            startMaximumC = 30.0,
            maximumSpeedMinimumC = 18.5,
            maximumSpeedMaximumC = 32.0,
            stepC = 0.5,
            minimumGapC = 0.5,
            hysteresisC = 0.5
        )
    )

    private fun pendingRequest(startTemperatureC: Double) = PendingAutomaticSettingsSave(
        deviceUid = DEVICE_UID,
        startTemperatureC = startTemperatureC,
        maximumSpeedTemperatureC = 27.0,
        silentModeEnabled = null
    )

    private companion object {
        const val DEVICE_UID = "cooling-device"
        const val CONTINUOUS_AUTOMATIC_PERCENT = 35.95
    }
}
