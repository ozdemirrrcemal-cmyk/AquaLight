package com.aqua.aqualight.ui.tabs.devices.detail.cooling.root

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootOperations
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAutomaticSettingsOperations
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAutomaticSettingsSnapshot
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTemperatureHistoryLoadResult
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTemperatureHistoryOperations
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTemperatureHistoryRange
import com.aqua.aqualight.ui.common.devicepresence.DeviceConnectionVisualState
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DeviceCoolingRootViewModel(
    private val operations: DeviceRootOperations,
    private val historyOperations: DeviceCoolingTemperatureHistoryOperations? = null,
    private val automaticSettingsOperations: DeviceCoolingAutomaticSettingsOperations? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceCoolingRootUiState())
    val uiState: StateFlow<DeviceCoolingRootUiState> = _uiState.asStateFlow()

    private var boundDeviceUid: String = ""
    private var observeJob: Job? = null
    private var historyJob: Job? = null
    private var automaticObserveJob: Job? = null
    private var automaticRefreshJob: Job? = null

    fun bind(deviceUidText: String) {
        val deviceUid = deviceUidText.trim()
        if (deviceUid.isBlank()) {
            cancelJobs()
            boundDeviceUid = ""
            _uiState.value = DeviceCoolingRootUiState()
            return
        }
        if (boundDeviceUid == deviceUid) return

        boundDeviceUid = deviceUid
        cancelJobs()
        _uiState.value = DeviceCoolingRootUiState(deviceUid = deviceUid)
        operations.current(deviceUid)?.let { snapshot ->
            _uiState.value = snapshot.toRootUiState(_uiState.value)
        }
        automaticSettingsOperations
            ?.currentAutomaticSettings(deviceUid)
            ?.takeIf { snapshot -> snapshot.loaded }
            ?.let { snapshot ->
                _uiState.value = _uiState.value.withAutomaticSnapshot(snapshot)
            }
        operations.connect(deviceUid)
        loadOverviewHistory(deviceUid)
        observeAutomaticSettings(deviceUid)
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
                        state.fanPercentNow
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

    private fun observeAutomaticSettings(deviceUid: String) {
        val automatic = automaticSettingsOperations ?: return
        automaticObserveJob = viewModelScope.launch {
            automatic.observeAutomaticSettings(deviceUid).collect { snapshot ->
                if (boundDeviceUid != deviceUid || !snapshot.loaded) return@collect
                _uiState.update { state -> state.withAutomaticSnapshot(snapshot) }
            }
        }
        automaticRefreshJob = viewModelScope.launch {
            automatic.refreshAutomaticSettings(deviceUid)
        }
    }

    private fun loadOverviewHistory(deviceUid: String) {
        val history = historyOperations ?: return
        historyJob = viewModelScope.launch {
            val result = history.loadTemperatureHistory(
                deviceUid = deviceUid,
                range = DeviceCoolingTemperatureHistoryRange.HOURS_24
            )
            if (boundDeviceUid != deviceUid) return@launch
            if (result is DeviceCoolingTemperatureHistoryLoadResult.Loaded) {
                _uiState.update { state ->
                    state.copy(
                        temperatureHistoryC = result.snapshot.points.map { point ->
                            point.temperatureC
                        }
                    )
                }
            }
        }
    }

    private fun cancelJobs() {
        observeJob?.cancel()
        historyJob?.cancel()
        automaticObserveJob?.cancel()
        automaticRefreshJob?.cancel()
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

private fun DeviceCoolingRootUiState.withAutomaticSnapshot(
    snapshot: DeviceCoolingAutomaticSettingsSnapshot
): DeviceCoolingRootUiState {
    if (!snapshot.available) return this
    val firmwareFanPercent = snapshot.fanPercentNow
        ?.takeIf(Double::isFinite)
        ?.roundToInt()
        ?.coerceIn(0, 100)
    return copy(
        autoStartTemperatureC = snapshot.startTemperatureC ?: autoStartTemperatureC,
        autoMaxTemperatureC = snapshot.maximumSpeedTemperatureC ?: autoMaxTemperatureC,
        tankTemperatureC = snapshot.tankTemperatureC ?: tankTemperatureC,
        fanPercentNow = if (selectedMode == CoolingControlMode.MANUAL) {
            fanPercentNow
        } else {
            firmwareFanPercent ?: fanPercentNow
        }
    )
}

enum class CoolingControlMode {
    AUTOMATIC,
    MANUAL,
    PROGRAM
}

data class DeviceCoolingRootUiState(
    val title: String = "",
    val deviceUid: String = "",
    val connectionVisualState: DeviceConnectionVisualState = DeviceConnectionVisualState.OFFLINE,
    val contentEnabled: Boolean = false,
    val selectedMode: CoolingControlMode = CoolingControlMode.AUTOMATIC,
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
