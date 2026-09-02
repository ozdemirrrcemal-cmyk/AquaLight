package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.automatic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAutomaticCommandResult
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAutomaticSettingsOperations
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingMutationState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DeviceCoolingAutomaticSettingsViewModel(
    private val operations: DeviceCoolingAutomaticSettingsOperations
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceCoolingAutomaticSettingsUiState())
    val uiState: StateFlow<DeviceCoolingAutomaticSettingsUiState> = _uiState.asStateFlow()

    private var boundDeviceUid = ""
    private var observeJob: Job? = null
    private var refreshJob: Job? = null
    private var saveJob: Job? = null

    fun bind(deviceUidText: String) {
        val deviceUid = deviceUidText.trim()
        if (deviceUid.isBlank()) {
            clearBinding()
            return
        }
        if (boundDeviceUid == deviceUid) return

        boundDeviceUid = deviceUid
        observeJob?.cancel()
        refreshJob?.cancel()
        saveJob?.cancel()
        _uiState.value = DeviceCoolingAutomaticSettingsUiState(deviceUid = deviceUid).beginRefresh()

        val current = operations.currentAutomaticSettings(deviceUid)
        if (current.loaded) {
            _uiState.value = _uiState.value.withSnapshot(current)
        }
        observeJob = viewModelScope.launch {
            operations.observeAutomaticSettings(deviceUid).collect { snapshot ->
                if (boundDeviceUid != deviceUid || !snapshot.loaded) return@collect
                _uiState.update { state -> state.withSnapshot(snapshot) }
            }
        }
        refresh()
    }

    fun refresh() {
        val deviceUid = boundDeviceUid.takeIf(String::isNotBlank) ?: return
        refreshJob?.cancel()
        _uiState.update(DeviceCoolingAutomaticSettingsUiState::beginRefresh)
        refreshJob = viewModelScope.launch {
            val result = operations.refreshAutomaticSettings(deviceUid)
            if (boundDeviceUid != deviceUid) return@launch
            when (result) {
                DeviceCoolingAutomaticCommandResult.Success -> {
                    val current = operations.currentAutomaticSettings(deviceUid)
                    _uiState.update { state -> state.withSnapshot(current) }
                }
                is DeviceCoolingAutomaticCommandResult.Failed -> {
                    _uiState.update { state -> state.afterRefreshFailure(result.failure) }
                }
            }
        }
    }

    fun updateStartTemperature(value: Double) {
        _uiState.update { state -> state.withUpdatedStartTemperature(value) }
    }

    fun updateMaximumSpeedTemperature(value: Double) {
        _uiState.update { state -> state.withUpdatedMaximumTemperature(value) }
    }

    fun updateSilentMode(enabled: Boolean) {
        _uiState.update { state -> state.withUpdatedSilentMode(enabled) }
    }

    fun save() {
        val state = _uiState.value
        val request = state.pendingSave(boundDeviceUid)
        if (request == null) {
            if (state.hasChanges && state.isCurrentAuthoritative) {
                _uiState.update { current ->
                    current.copy(mutationState = CoolingMutationState.ValidationError)
                }
            }
            return
        }

        saveJob?.cancel()
        _uiState.update { current ->
            current.copy(mutationState = CoolingMutationState.Saving)
        }
        saveJob = viewModelScope.launch {
            val result = operations.saveAutomaticSettings(
                deviceUid = request.deviceUid,
                startTemperatureC = request.startTemperatureC,
                maximumSpeedTemperatureC = request.maximumSpeedTemperatureC,
                silentModeEnabled = request.silentModeEnabled
            )
            if (boundDeviceUid == request.deviceUid) {
                _uiState.update { current -> current.afterSave(request, result) }
            }
        }
    }

    private fun clearBinding() {
        observeJob?.cancel()
        refreshJob?.cancel()
        saveJob?.cancel()
        boundDeviceUid = ""
        _uiState.value = DeviceCoolingAutomaticSettingsUiState()
    }
}
