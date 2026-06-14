package com.aqua.aqualight.ui.tabs.devices.detail.light.automation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.light.automation.model.DeviceLightAutomationUiState
import com.aqua.aqualight.ui.tabs.devices.detail.light.core.automation.model.CloudSimulationSettings
import com.aqua.aqualight.ui.tabs.devices.detail.light.core.automation.model.MoonlightSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Data-ready UI state holder for independent light automations.
 *
 * At this stage there is intentionally no device transport connected here.
 * User changes are kept as local draft state and passed through the same
 * update functions that will later enqueue/read real firmware sync commands.
 */
class DeviceLightAutomationViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(
        DeviceLightAutomationUiState()
    )

    val uiState: StateFlow<DeviceLightAutomationUiState> =
        _uiState.asStateFlow()

    private var initializedDeviceId: Long = 0L

    fun initialize(deviceId: Long) {
        initializedDeviceId = deviceId.coerceAtLeast(0L)

        _uiState.update { state ->
            state.copy(
                pendingDeviceSync = false,
                updatedAt = System.currentTimeMillis()
            )
        }

        refreshFromDeviceIfConnected()
    }

    fun updateMoonlight(settings: MoonlightSettings) {
        val sanitizedSettings = settings.copy(
            intensityPercent = settings.intensityPercent.coerceIn(1, 15)
        )

        _uiState.update { state ->
            state.copy(
                moonlight = sanitizedSettings,
                pendingDeviceSync = true,
                updatedAt = System.currentTimeMillis()
            )
        }

        syncMoonlightToDevice(sanitizedSettings)
    }

    fun updateCloudSimulation(settings: CloudSimulationSettings) {
        val sanitizedSettings = settings.copy(
            coveragePercent = settings.coveragePercent.coerceIn(5, 70)
        )

        _uiState.update { state ->
            state.copy(
                cloudSimulation = sanitizedSettings,
                pendingDeviceSync = true,
                updatedAt = System.currentTimeMillis()
            )
        }

        syncCloudSimulationToDevice(sanitizedSettings)
    }

    private fun refreshFromDeviceIfConnected() {
        // TODO: Connect this to the light data layer when firmware runtime support is ready.
        // The UI is intentionally usable as local draft state until that layer exists.
    }

    private fun syncMoonlightToDevice(settings: MoonlightSettings) {
        // TODO: Send moonlight settings to the light data layer.
    }

    private fun syncCloudSimulationToDevice(settings: CloudSimulationSettings) {
        // TODO: Send cloud simulation settings to the light data layer.
    }
}
