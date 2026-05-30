package com.aqua.aqualight.ui.tabs.devices.detail.light

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.data.devices.light.LightOverviewRepository
import com.aqua.aqualight.data.devices.light.LightOverviewRepositoryProvider
import com.aqua.aqualight.data.devices.light.model.LightOverviewSnapshot
import com.aqua.aqualight.ui.tabs.devices.detail.light.model.LightOverviewUiMapper
import com.aqua.aqualight.ui.tabs.devices.detail.light.model.LightOverviewUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DeviceLightViewModel(
    private val deviceId: Long,
    private val repository: LightOverviewRepository
) : ViewModel() {

    private val _uiState =
        MutableStateFlow(
            LightOverviewUiMapper.map(
                LightOverviewSnapshot.loading(
                    deviceId = deviceId
                )
            )
        )

    val uiState: StateFlow<LightOverviewUiState> =
        _uiState.asStateFlow()

    init {
        observeLightOverview()
    }

    fun refresh() {
        viewModelScope.launch {
            repository.refresh(
                deviceId = deviceId
            )
        }
    }

    fun setProgramEnabled(
        enabled: Boolean
    ) {
        viewModelScope.launch {
            repository.setProgramEnabled(
                deviceId = deviceId,
                enabled = enabled
            )
        }
    }

    fun applyTemporaryScene(
        sceneName: String,
        outputPercent: Int,
        durationLabel: String,
        resumeLabel: String
    ) {
        viewModelScope.launch {
            repository.applyTemporaryScene(
                deviceId = deviceId,
                sceneName = sceneName,
                outputPercent = outputPercent,
                durationLabel = durationLabel,
                resumeLabel = resumeLabel
            )
        }
    }

    fun restoreAutoProgram() {
        viewModelScope.launch {
            repository.restoreAutoProgram(
                deviceId = deviceId
            )
        }
    }

    private fun observeLightOverview() {
        viewModelScope.launch {
            repository
                .observeOverview(
                    deviceId = deviceId
                )
                .collect { snapshot ->
                    _uiState.value =
                        LightOverviewUiMapper.map(
                            snapshot = snapshot
                        )
                }
        }
    }

    class Factory(
        private val deviceId: Long,
        private val repository: LightOverviewRepository =
            LightOverviewRepositoryProvider.get()
    ) : ViewModelProvider.Factory {

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(
            modelClass: Class<T>
        ): T {
            if (modelClass.isAssignableFrom(DeviceLightViewModel::class.java)) {
                return DeviceLightViewModel(
                    deviceId = deviceId,
                    repository = repository
                ) as T
            }

            throw IllegalArgumentException(
                "Unknown ViewModel class: ${modelClass.name}"
            )
        }
    }
}