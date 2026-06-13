package com.aqua.aqualight.ui.tabs.devices.detail.light.automation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.light.automation.model.DeviceLightAutomationUiState
import com.aqua.aqualight.ui.tabs.devices.detail.light.core.automation.model.CloudSimulationSettings
import com.aqua.aqualight.ui.tabs.devices.detail.light.core.automation.model.MoonlightSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Temporary UI shell for light automation.
 *
 * Local automation DataStore and ESP32 automation commands were removed. This
 * state is intentionally in-memory/default only and is not connected to any
 * firmware or persistence source.
 */
class DeviceLightAutomationViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(
        DeviceLightAutomationUiState()
    )

    val uiState: StateFlow<DeviceLightAutomationUiState> =
        _uiState.asStateFlow()

    fun initialize(
        deviceId: Long
    ) = Unit

    fun updateMoonlight(
        settings: MoonlightSettings
    ) = Unit

    fun updateCloudSimulation(
        settings: CloudSimulationSettings
    ) = Unit
}
