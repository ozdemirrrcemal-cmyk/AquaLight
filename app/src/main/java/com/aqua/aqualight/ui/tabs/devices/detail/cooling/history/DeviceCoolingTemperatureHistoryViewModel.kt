package com.aqua.aqualight.ui.tabs.devices.detail.cooling.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTemperatureHistoryLoadResult
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTemperatureHistoryOperations
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTemperatureHistoryRange
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTemperatureHistorySnapshot
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DeviceCoolingTemperatureHistoryViewModel(
    private val operations: DeviceCoolingTemperatureHistoryOperations
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceCoolingTemperatureHistoryUiState())
    val uiState: StateFlow<DeviceCoolingTemperatureHistoryUiState> = _uiState.asStateFlow()

    private var boundDeviceUid = ""
    private var loadJob: Job? = null

    fun bind(deviceUidText: String) {
        val deviceUid = deviceUidText.trim()
        if (deviceUid.isBlank()) {
            loadJob?.cancel()
            boundDeviceUid = ""
            _uiState.value = DeviceCoolingTemperatureHistoryUiState()
            return
        }
        if (boundDeviceUid == deviceUid) return
        boundDeviceUid = deviceUid
        _uiState.value = DeviceCoolingTemperatureHistoryUiState(deviceUid = deviceUid)
        loadSelectedRange()
    }

    fun selectRange(range: DeviceCoolingTemperatureHistoryRange) {
        if (_uiState.value.selectedRange == range) return
        _uiState.value = _uiState.value.copy(
            selectedRange = range,
            snapshot = null,
            loadState = DeviceCoolingTemperatureHistoryLoadState.CONTENT
        )
        loadSelectedRange()
    }

    fun retry() {
        if (boundDeviceUid.isNotBlank()) loadSelectedRange()
    }

    private fun loadSelectedRange() {
        val deviceUid = boundDeviceUid
        if (deviceUid.isBlank()) return
        val range = _uiState.value.selectedRange
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            when (val result = operations.loadTemperatureHistory(deviceUid, range)) {
                is DeviceCoolingTemperatureHistoryLoadResult.Loaded -> {
                    if (boundDeviceUid != deviceUid || _uiState.value.selectedRange != range) {
                        return@launch
                    }
                    _uiState.value = _uiState.value.copy(
                        loadState = DeviceCoolingTemperatureHistoryLoadState.CONTENT,
                        snapshot = result.snapshot
                    )
                }
                DeviceCoolingTemperatureHistoryLoadResult.Unsupported,
                DeviceCoolingTemperatureHistoryLoadResult.Unavailable -> {
                    if (boundDeviceUid == deviceUid && _uiState.value.selectedRange == range) {
                        // Device connectivity is gated before this destination is entered. A
                        // history capability/read failure must not create a second connection UI.
                        // The chart, summaries and table stay visible without invented values.
                        _uiState.value = _uiState.value.copy(
                            loadState = DeviceCoolingTemperatureHistoryLoadState.CONTENT,
                            snapshot = null
                        )
                    }
                }
            }
        }
    }
}

data class DeviceCoolingTemperatureHistoryUiState(
    val deviceUid: String = "",
    val selectedRange: DeviceCoolingTemperatureHistoryRange =
        DeviceCoolingTemperatureHistoryRange.HOURS_24,
    val loadState: DeviceCoolingTemperatureHistoryLoadState =
        DeviceCoolingTemperatureHistoryLoadState.CONTENT,
    val snapshot: DeviceCoolingTemperatureHistorySnapshot? = null
)

enum class DeviceCoolingTemperatureHistoryLoadState {
    IDLE,
    LOADING,
    CONTENT,
    UNSUPPORTED,
    UNAVAILABLE
}
