package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.manual

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlOperations
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlResult
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingDataState
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingMutationState
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.root.beginControlRefresh
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.root.toRootControlState
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DeviceCoolingManualSettingsViewModel(
    private val operations: DeviceCoolingControlOperations
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceCoolingManualSettingsUiState())
    val uiState: StateFlow<DeviceCoolingManualSettingsUiState> = _uiState.asStateFlow()

    private var boundDeviceUid = ""
    private var observeJob: Job? = null
    private var refreshJob: Job? = null
    private var mutationJob: Job? = null

    fun bind(deviceUidText: String) {
        val deviceUid = deviceUidText.trim()
        if (deviceUid.isBlank()) {
            clearBinding()
            return
        }
        if (boundDeviceUid == deviceUid) return

        boundDeviceUid = deviceUid
        cancelJobs()
        val initialState = DeviceCoolingManualSettingsUiState(
            deviceUid = deviceUid,
            controlState = operations.currentControl(deviceUid).toRootControlState(
                previous = CoolingDataState.Initial
            )
        )
        _uiState.value = initialState

        observeJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            operations.observeControl(deviceUid).collect { result ->
                if (boundDeviceUid != deviceUid) return@collect
                _uiState.update { state ->
                    state.copy(
                        controlState = result.toRootControlState(state.controlState)
                    )
                }
            }
        }
        refresh()
    }

    fun refresh() {
        val deviceUid = boundDeviceUid.takeIf(String::isNotBlank) ?: return
        refreshJob?.cancel()
        _uiState.update { state ->
            state.copy(controlState = state.controlState.beginControlRefresh())
        }
        refreshJob = viewModelScope.launch {
            val result = operations.refreshControl(deviceUid)
            if (boundDeviceUid != deviceUid) return@launch
            _uiState.update { state ->
                state.copy(controlState = result.toRootControlState(state.controlState))
            }
        }
    }

    fun updateTargetPercent(percent: Int) {
        val state = _uiState.value
        val capabilities = state.capabilities
        val deviceUid = boundDeviceUid.takeIf(String::isNotBlank)

        if (capabilities != null && deviceUid != null && state.canWrite) {
            val bounded = percent.coerceIn(
                capabilities.minimumPercent,
                capabilities.maximumPercent
            )
            mutationJob?.cancel()
            _uiState.update { current ->
                current.copy(mutationState = CoolingMutationState.Saving)
            }
            mutationJob = viewModelScope.launch {
                val result = operations.setManualFanPercent(deviceUid, bounded)
                if (boundDeviceUid != deviceUid) return@launch
                _uiState.update { current -> current.afterMutation(result) }
            }
        }
    }

    private fun clearBinding() {
        cancelJobs()
        boundDeviceUid = ""
        _uiState.value = DeviceCoolingManualSettingsUiState()
    }

    private fun cancelJobs() {
        observeJob?.cancel()
        refreshJob?.cancel()
        mutationJob?.cancel()
        observeJob = null
        refreshJob = null
        mutationJob = null
    }
}

private fun DeviceCoolingManualSettingsUiState.afterMutation(
    result: DeviceCoolingControlResult
): DeviceCoolingManualSettingsUiState = when (result) {
    is DeviceCoolingControlResult.Available -> copy(
        controlState = result.toRootControlState(controlState),
        mutationState = CoolingMutationState.Saved
    )
    is DeviceCoolingControlResult.Failed -> copy(
        mutationState = CoolingMutationState.OperationError(result.failure)
    )
}
