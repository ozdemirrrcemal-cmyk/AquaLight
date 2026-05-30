package com.aqua.aqualight.ui.tabs.devices.detail.light

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.data.devices.light.LightOverviewRepository
import com.aqua.aqualight.data.devices.light.LightOverviewRepositoryProvider
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
            LightOverviewUiState.loading()
        )

    val uiState: StateFlow<LightOverviewUiState> =
        _uiState.asStateFlow()

    init {
        observeRepository()
    }

    private fun observeRepository() {
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

    fun refresh() {
        repository.refresh(
            deviceId = deviceId
        )
    }

    fun setProgramEnabled(
        enabled: Boolean
    ) {
        repository.setProgramEnabled(
            deviceId = deviceId,
            enabled = enabled
        )
    }

    fun applyTemporaryScene(
        sceneName: String,
        outputPercent: Int,
        durationLabel: String,
        resumeLabel: String
    ) {
        repository.applyTemporaryScene(
            deviceId = deviceId,
            sceneName = sceneName,
            outputPercent = outputPercent,
            durationLabel = durationLabel,
            resumeLabel = resumeLabel
        )
    }

    fun restoreAutoProgram() {
        repository.restoreAutoProgram(
            deviceId = deviceId
        )
    }

    class Factory(
        private val deviceId: Long
    ) : ViewModelProvider.Factory {

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(
            modelClass: Class<T>
        ): T {
            if (modelClass.isAssignableFrom(DeviceLightViewModel::class.java)) {
                return DeviceLightViewModel(
                    deviceId = deviceId,
                    repository = LightOverviewRepositoryProvider.provide()
                ) as T
            }

            throw IllegalArgumentException(
                "Unknown ViewModel class: ${modelClass.name}"
            )
        }
    }
}