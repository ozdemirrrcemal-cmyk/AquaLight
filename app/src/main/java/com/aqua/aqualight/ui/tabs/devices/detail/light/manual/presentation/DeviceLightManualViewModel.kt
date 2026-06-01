package com.aqua.aqualight.ui.tabs.devices.detail.light.manual.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.ui.tabs.devices.detail.light.manual.domain.model.ManualLightOutput
import com.aqua.aqualight.ui.tabs.devices.detail.light.manual.domain.repository.ManualLightRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DeviceLightManualViewModel(
    private val deviceId: Long,
    private val repository: ManualLightRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceLightManualUiState())
    val uiState: StateFlow<DeviceLightManualUiState> = _uiState.asStateFlow()

    fun onEvent(
        event: DeviceLightManualEvent
    ) {
        when (event) {
            DeviceLightManualEvent.RefreshRequested -> refresh()

            is DeviceLightManualEvent.ManualOutputChanged -> {
                updateLocalOutput(
                    masterPercent = event.masterPercent,
                    redPercent = event.redPercent,
                    greenPercent = event.greenPercent,
                    bluePercent = event.bluePercent,
                    whitePercent = event.whitePercent
                )
            }

            is DeviceLightManualEvent.ApplyTemporaryRequested -> {
                applyTemporaryOutput(
                    output = ManualLightOutput(
                        masterPercent = event.masterPercent,
                        redPercent = event.redPercent,
                        greenPercent = event.greenPercent,
                        bluePercent = event.bluePercent,
                        whitePercent = event.whitePercent
                    )
                )
            }

            is DeviceLightManualEvent.SaveAsPresetRequested -> {
                // TODO: Route to preset creation flow when Custom Preset screen is implemented.
            }
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            // TODO: Read real manual output from ESP32.
        }
    }

    private fun updateLocalOutput(
        masterPercent: Int,
        redPercent: Int,
        greenPercent: Int,
        bluePercent: Int,
        whitePercent: Int
    ) {
        _uiState.value = _uiState.value.copy(
            masterPercent = masterPercent,
            redPercent = redPercent,
            greenPercent = greenPercent,
            bluePercent = bluePercent,
            whitePercent = whitePercent
        )
    }

    private fun applyTemporaryOutput(
        output: ManualLightOutput
    ) {
        viewModelScope.launch {
            // TODO: Send temporary manual output to ESP32.
            repository.applyTemporaryOutput(
                deviceId = deviceId,
                output = output
            )
        }
    }

    class Factory(
        private val deviceId: Long,
        private val repository: ManualLightRepository
    ) : ViewModelProvider.Factory {

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(
            modelClass: Class<T>
        ): T {
            return DeviceLightManualViewModel(
                deviceId = deviceId,
                repository = repository
            ) as T
        }
    }
}