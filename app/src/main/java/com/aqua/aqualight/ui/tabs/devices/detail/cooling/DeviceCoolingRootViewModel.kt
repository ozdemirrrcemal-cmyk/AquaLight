package com.aqua.aqualight.ui.tabs.devices.detail.cooling

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootOperations
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.ui.common.devicepresence.DeviceConnectionVisualState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DeviceCoolingRootViewModel(
    private val operations: DeviceRootOperations
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceCoolingRootUiState())
    val uiState: StateFlow<DeviceCoolingRootUiState> = _uiState.asStateFlow()

    private var boundDeviceUid: String = ""
    private var observeJob: Job? = null

    fun bind(deviceUidText: String) {
        val deviceUid = deviceUidText.trim()
        if (deviceUid.isBlank()) {
            observeJob?.cancel()
            boundDeviceUid = ""
            _uiState.value = DeviceCoolingRootUiState()
            return
        }
        if (boundDeviceUid == deviceUid) return

        boundDeviceUid = deviceUid
        observeJob?.cancel()
        _uiState.value = DeviceCoolingRootUiState(deviceUid = deviceUid)
        operations.current(deviceUid)?.let { snapshot ->
            _uiState.value = snapshot.toRootUiState(_uiState.value)
        }
        operations.connect(deviceUid)
        observeJob = viewModelScope.launch {
            operations.observe(deviceUid).collect { snapshot ->
                if (boundDeviceUid != deviceUid) return@collect
                _uiState.value = snapshot?.toRootUiState(_uiState.value)
                    ?: _uiState.value.copy(
                        deviceUid = deviceUid,
                        connectionVisualState = DeviceConnectionVisualState.OFFLINE,
                        contentEnabled = false,
                        fanOutputCount = 0,
                        temperatureSensorCount = 0
                    )
            }
        }
    }

    fun selectMode(mode: CoolingControlMode) {
        _uiState.update { state ->
            if (!state.contentEnabled || state.selectedMode == mode) {
                state
            } else {
                state.copy(
                    selectedMode = mode,
                    fanPercentNow = if (mode == CoolingControlMode.MANUAL) {
                        state.manualFanPercent
                    } else {
                        null
                    }
                )
            }
        }
    }

    fun updateManualFanPercent(percent: Int) {
        _uiState.update { state ->
            if (!state.contentEnabled) {
                state
            } else {
                val clamped = percent.coerceIn(MIN_PERCENT, MAX_PERCENT)
                state.copy(
                    manualFanPercent = clamped,
                    fanPercentNow = if (state.selectedMode == CoolingControlMode.MANUAL) {
                        clamped
                    } else {
                        state.fanPercentNow
                    }
                )
            }
        }
    }

    fun selectProfile(profile: CoolingProfile) {
        _uiState.update { state ->
            if (!state.contentEnabled || state.selectedProfile == profile) state
            else state.copy(selectedProfile = profile)
        }
    }

    private fun DeviceRootSnapshot.toRootUiState(
        previous: DeviceCoolingRootUiState
    ): DeviceCoolingRootUiState {
        val contentEnabled =
            family == OwnerDeviceFamily.COOLING &&
                availability == OwnerDeviceAvailability.REACHABLE &&
                catalogState == DeviceRootCatalogState.VALID
        return previous.copy(
            title = title,
            deviceUid = deviceUid,
            connectionVisualState = if (contentEnabled) {
                DeviceConnectionVisualState.ONLINE
            } else {
                DeviceConnectionVisualState.OFFLINE
            },
            contentEnabled = contentEnabled,
            fanOutputCount = fanOutputCount,
            temperatureSensorCount = temperatureSensorCount
        )
    }

    private companion object {
        const val MIN_PERCENT = 0
        const val MAX_PERCENT = 100
    }
}

enum class CoolingControlMode {
    AUTOMATIC,
    MANUAL,
    PROGRAM
}

enum class CoolingProfile {
    QUIET,
    BALANCED,
    PERFORMANCE,
    BOOST
}

data class DeviceCoolingRootUiState(
    val title: String = "",
    val deviceUid: String = "",
    val connectionVisualState: DeviceConnectionVisualState = DeviceConnectionVisualState.OFFLINE,
    val contentEnabled: Boolean = false,
    val selectedMode: CoolingControlMode = CoolingControlMode.AUTOMATIC,
    val selectedProfile: CoolingProfile = CoolingProfile.BALANCED,
    val manualFanPercent: Int = 60,
    val autoStartTemperatureC: Double = 25.0,
    val autoMaxTemperatureC: Double = 27.0,
    val fanPercentNow: Int? = null,
    val tankTemperatureC: Double? = null,
    val roomTemperatureC: Double? = null,
    val humidityPercent: Double? = null,
    val powerWatts: Double? = null,
    val estimatedKwhPerDay: Double? = null,
    val temperatureHistoryC: List<Double> = emptyList(),
    val fanOutputCount: Int = 0,
    val temperatureSensorCount: Int = 0
)
