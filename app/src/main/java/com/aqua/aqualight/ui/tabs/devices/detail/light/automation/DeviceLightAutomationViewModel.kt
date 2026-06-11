package com.aqua.aqualight.ui.tabs.devices.detail.light.automation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.data.devices.light.automation.LightAutomationDataStoreManager
import com.aqua.aqualight.data.devices.light.automation.model.CloudSimulationSettings
import com.aqua.aqualight.data.devices.light.automation.model.MoonlightSettings
import com.aqua.aqualight.data.devices.light.runtime.Esp32LightAutomationCommandManager
import com.aqua.aqualight.ui.tabs.devices.detail.light.automation.model.DeviceLightAutomationUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DeviceLightAutomationViewModel(application: Application) : AndroidViewModel(application) {
    private val automationStore = LightAutomationDataStoreManager(application.applicationContext)
    private val automationCommandManager = Esp32LightAutomationCommandManager(application.applicationContext)
    private val _uiState = MutableStateFlow(DeviceLightAutomationUiState())
    val uiState: StateFlow<DeviceLightAutomationUiState> = _uiState.asStateFlow()
    private var deviceId: Long = 0L
    private var observeJob: Job? = null
    fun initialize(deviceId: Long) {
        val id = deviceId.coerceAtLeast(0L)
        if (this.deviceId == id && observeJob != null) return
        this.deviceId = id
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            automationStore.observeSettings(id).collect { settings ->
                _uiState.update { DeviceLightAutomationUiState(settings.moonlight, settings.cloudSimulation, settings.pendingDeviceSync, settings.updatedAt) }
            }
        }
    }
    fun updateMoonlight(settings: MoonlightSettings) { val id = deviceId; if (id <= 0L) return; viewModelScope.launch { val saved = automationStore.updateMoonlight(id, settings); syncAutomationSettings(id, saved) } }
    fun updateCloudSimulation(settings: CloudSimulationSettings) { val id = deviceId; if (id <= 0L) return; viewModelScope.launch { val saved = automationStore.updateCloudSimulation(id, settings); syncAutomationSettings(id, saved) } }
    private suspend fun syncAutomationSettings(deviceId: Long, settings: com.aqua.aqualight.data.devices.light.automation.model.LightAutomationSettings) { val result = automationCommandManager.applyAutomationSettings(deviceId, settings); if (result.isSuccess) { automationStore.markSynced(deviceId) } }
}
