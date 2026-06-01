package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.ui.tabs.devices.detail.light.domain.repository.LightDeviceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DeviceLightViewModel(
    private val deviceId: Long,
    private val repository: LightDeviceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceLightUiState())
    val uiState: StateFlow<DeviceLightUiState> = _uiState.asStateFlow()

    fun onEvent(
        event: DeviceLightEvent
    ) {
        when (event) {
            DeviceLightEvent.RefreshRequested -> refresh()

            is DeviceLightEvent.ProgramEnabledChanged -> {
                setProgramEnabled(event.enabled)
            }

            is DeviceLightEvent.TemporaryModeRequested -> {
                applyTemporaryMode(
                    sceneKey = event.sceneKey,
                    durationMinutes = event.durationMinutes,
                    untilNextEvent = event.untilNextEvent
                )
            }

            DeviceLightEvent.RestoreAutoRequested -> {
                restoreAutoProgram()
            }
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            // TODO: Read real light overview from ESP32 repository.
        }
    }

    private fun setProgramEnabled(
        enabled: Boolean
    ) {
        viewModelScope.launch {
            // TODO: Send enable / disable command to ESP32.
        }
    }

    private fun applyTemporaryMode(
        sceneKey: String,
        durationMinutes: Int?,
        untilNextEvent: Boolean
    ) {
        viewModelScope.launch {
            // TODO: Send temporary mode command to ESP32.
        }
    }

    private fun restoreAutoProgram() {
        viewModelScope.launch {
            // TODO: Send restore auto command to ESP32.
        }
    }

    class Factory(
        private val deviceId: Long,
        private val repository: LightDeviceRepository
    ) : ViewModelProvider.Factory {

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(
            modelClass: Class<T>
        ): T {
            return DeviceLightViewModel(
                deviceId = deviceId,
                repository = repository
            ) as T
        }
    }
}