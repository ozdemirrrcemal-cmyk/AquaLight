package com.aqua.aqualight.ui.tabs.devices.detail.cooling.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTemperatureHistoryLoadResult
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTemperatureHistoryOperations
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTemperatureHistoryRange
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTemperatureHistorySnapshot
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.common.CoolingDataFreshness
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.common.CoolingDataState
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.common.authoritativeValueOrNull
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
            clearBinding()
            return
        }
        if (boundDeviceUid == deviceUid) return

        boundDeviceUid = deviceUid
        loadJob?.cancel()
        _uiState.value = DeviceCoolingTemperatureHistoryUiState(
            deviceUid = deviceUid,
            dataState = CoolingDataState.Loading
        )
        loadSelectedRange()
    }

    fun selectRange(range: DeviceCoolingTemperatureHistoryRange) {
        val current = _uiState.value
        if (current.selectedRange == range) return

        _uiState.value = current.copy(
            selectedRange = range,
            dataState = current.dataState.beginHistoryRefresh()
        )
        loadSelectedRange()
    }

    fun retry() {
        if (boundDeviceUid.isBlank()) return
        _uiState.value = _uiState.value.copy(
            dataState = _uiState.value.dataState.beginHistoryRefresh()
        )
        loadSelectedRange()
    }

    private fun loadSelectedRange() {
        val deviceUid = boundDeviceUid
        if (deviceUid.isBlank()) return
        val range = _uiState.value.selectedRange
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val result = operations.loadTemperatureHistory(deviceUid, range)
            if (boundDeviceUid != deviceUid || _uiState.value.selectedRange != range) return@launch

            val current = _uiState.value
            val nextDataState = current.dataState.afterHistoryLoad(result)
            val resolvedRange = when {
                result is DeviceCoolingTemperatureHistoryLoadResult.Unavailable ->
                    nextDataState.authoritativeValueOrNull?.range ?: range
                else -> range
            }
            _uiState.value = current.copy(
                selectedRange = resolvedRange,
                dataState = nextDataState
            )
        }
    }

    private fun clearBinding() {
        loadJob?.cancel()
        boundDeviceUid = ""
        _uiState.value = DeviceCoolingTemperatureHistoryUiState()
    }
}

enum class DeviceCoolingTemperatureHistoryFailure {
    UNAVAILABLE
}

data class DeviceCoolingTemperatureHistoryUiState(
    val deviceUid: String = "",
    val selectedRange: DeviceCoolingTemperatureHistoryRange =
        DeviceCoolingTemperatureHistoryRange.HOURS_24,
    val dataState: CoolingDataState<
        DeviceCoolingTemperatureHistorySnapshot,
        DeviceCoolingTemperatureHistoryFailure
        > = CoolingDataState.Initial
) {
    val snapshot: DeviceCoolingTemperatureHistorySnapshot?
        get() = dataState.authoritativeValueOrNull

    val loadState: DeviceCoolingTemperatureHistoryLoadState
        get() = dataState.toHistoryLoadState()
}

enum class DeviceCoolingTemperatureHistoryLoadState {
    IDLE,
    LOADING,
    CONTENT,
    REFRESHING,
    UNSUPPORTED,
    UNAVAILABLE
}

private fun CoolingDataState<
    DeviceCoolingTemperatureHistorySnapshot,
    DeviceCoolingTemperatureHistoryFailure
    >.toHistoryLoadState(): DeviceCoolingTemperatureHistoryLoadState = when (this) {
    CoolingDataState.Initial -> DeviceCoolingTemperatureHistoryLoadState.IDLE
    CoolingDataState.Loading -> DeviceCoolingTemperatureHistoryLoadState.LOADING
    is CoolingDataState.Content -> freshness.toHistoryLoadedState()
    is CoolingDataState.Empty -> freshness.toHistoryLoadedState()
    CoolingDataState.Unsupported -> DeviceCoolingTemperatureHistoryLoadState.UNSUPPORTED
    CoolingDataState.Unavailable,
    is CoolingDataState.OperationError -> DeviceCoolingTemperatureHistoryLoadState.UNAVAILABLE
}

private fun CoolingDataFreshness.toHistoryLoadedState(): DeviceCoolingTemperatureHistoryLoadState =
    if (this == CoolingDataFreshness.REFRESHING) {
        DeviceCoolingTemperatureHistoryLoadState.REFRESHING
    } else {
        DeviceCoolingTemperatureHistoryLoadState.CONTENT
    }

private fun CoolingDataState<
    DeviceCoolingTemperatureHistorySnapshot,
    DeviceCoolingTemperatureHistoryFailure
    >.beginHistoryRefresh(): CoolingDataState<
    DeviceCoolingTemperatureHistorySnapshot,
    DeviceCoolingTemperatureHistoryFailure
    > = when (this) {
    is CoolingDataState.Content -> copy(
        freshness = CoolingDataFreshness.REFRESHING,
        refreshFailure = null
    )
    is CoolingDataState.Empty -> copy(
        freshness = CoolingDataFreshness.REFRESHING,
        refreshFailure = null
    )
    CoolingDataState.Initial,
    CoolingDataState.Loading,
    CoolingDataState.Unavailable,
    CoolingDataState.Unsupported,
    is CoolingDataState.OperationError -> CoolingDataState.Loading
}

private fun CoolingDataState<
    DeviceCoolingTemperatureHistorySnapshot,
    DeviceCoolingTemperatureHistoryFailure
    >.afterHistoryLoad(
    result: DeviceCoolingTemperatureHistoryLoadResult
): CoolingDataState<DeviceCoolingTemperatureHistorySnapshot, DeviceCoolingTemperatureHistoryFailure> =
    when (result) {
        is DeviceCoolingTemperatureHistoryLoadResult.Loaded -> result.snapshot.toHistoryDataState()
        DeviceCoolingTemperatureHistoryLoadResult.Unsupported -> CoolingDataState.Unsupported
        DeviceCoolingTemperatureHistoryLoadResult.Unavailable -> preserveHistoryOnUnavailable()
    }

private fun DeviceCoolingTemperatureHistorySnapshot.toHistoryDataState(): CoolingDataState<
    DeviceCoolingTemperatureHistorySnapshot,
    DeviceCoolingTemperatureHistoryFailure
    > = if (hasHistoryMeasurements()) {
    CoolingDataState.Content(this)
} else {
    CoolingDataState.Empty(this)
}

private fun DeviceCoolingTemperatureHistorySnapshot.hasHistoryMeasurements(): Boolean {
    val hasSeries = points.isNotEmpty() || dailySummaries.isNotEmpty()
    val hasSummary = minimumTemperatureC != null ||
        averageTemperatureC != null ||
        maximumTemperatureC != null
    return hasSeries || hasSummary
}

private fun CoolingDataState<
    DeviceCoolingTemperatureHistorySnapshot,
    DeviceCoolingTemperatureHistoryFailure
    >.preserveHistoryOnUnavailable(): CoolingDataState<
    DeviceCoolingTemperatureHistorySnapshot,
    DeviceCoolingTemperatureHistoryFailure
    > = when (this) {
    is CoolingDataState.Content -> copy(
        freshness = CoolingDataFreshness.STALE,
        refreshFailure = DeviceCoolingTemperatureHistoryFailure.UNAVAILABLE
    )
    is CoolingDataState.Empty -> copy(
        freshness = CoolingDataFreshness.STALE,
        refreshFailure = DeviceCoolingTemperatureHistoryFailure.UNAVAILABLE
    )
    CoolingDataState.Initial,
    CoolingDataState.Loading,
    CoolingDataState.Unavailable,
    CoolingDataState.Unsupported,
    is CoolingDataState.OperationError -> CoolingDataState.Unavailable
}
