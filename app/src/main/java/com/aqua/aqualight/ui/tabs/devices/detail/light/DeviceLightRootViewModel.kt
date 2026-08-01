package com.aqua.aqualight.ui.tabs.devices.detail.light

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.application.devices.DeviceRootOperations
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DeviceLightRootViewModel(
    private val rootOperations: DeviceRootOperations
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceLightRootUiState())
    val uiState: StateFlow<DeviceLightRootUiState> = _uiState.asStateFlow()

    private var boundDeviceUid: String = ""
    private var observeJob: Job? = null

    fun bind(
        deviceUidText: String,
        fallbackTitle: String
    ) {
        val deviceUid = deviceUidText.trim()
        if (deviceUid.isBlank()) {
            observeJob?.cancel()
            boundDeviceUid = ""
            _uiState.value = DeviceLightRootUiState(title = fallbackTitle)
            return
        }
        if (boundDeviceUid == deviceUid) return

        boundDeviceUid = deviceUid
        observeJob?.cancel()
        _uiState.value = DeviceLightRootUiState(
            title = rootOperations.current(deviceUid)
                ?.title
                .orEmpty()
                .ifBlank { fallbackTitle }
        )
        rootOperations.connect(deviceUid)
        observeJob = viewModelScope.launch {
            rootOperations.observe(deviceUid).collect { snapshot ->
                _uiState.value = DeviceLightRootUiState(
                    title = snapshot
                        ?.title
                        .orEmpty()
                        .ifBlank { fallbackTitle }
                )
            }
        }
    }
}

data class DeviceLightRootUiState(
    val title: String = ""
)
