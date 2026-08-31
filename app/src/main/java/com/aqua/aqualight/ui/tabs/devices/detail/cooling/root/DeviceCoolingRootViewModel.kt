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
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlMode
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlOperations
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlResult
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingManualFanCapabilities
import com.aqua.aqualight.ui.common.devicepresence.DeviceConnectionVisualState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

typealias CoolingControlMode = DeviceCoolingControlMode

class DeviceCoolingRootViewModel(
    private val operations: DeviceRootOperations,
    private val controlOperations: DeviceCoolingControlOperations,
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
    private var controlObserveJob: Job? = null
    private var controlRefreshJob: Job? = null
    private var controlMutationJob: Job? = null

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
        _uiState.value = _uiState.value.withControlReadResult(
            controlOperations.currentControl(deviceUid)
        )
        automaticSettingsOperations
            ?.currentAutomaticSettings(deviceUid)
            ?.takeIf { snapshot -> snapshot.loaded }
            ?.let { snapshot ->
                _uiState.value = _uiState.value.withAutomaticSnapshot(snapshot)
            }
        operations.connect(deviceUid)
        loadOverviewHistory(deviceUid)
        observeCoolingControl(deviceUid)
        refreshCoolingControl(deviceUid)
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
        val state = _uiState.value
        if (
            !state.contentEnabled ||
            !state.controlAvailable ||
            !state.modeSelectionWritable ||
            mode !in state.supportedModes ||
            state.selectedMode == mode
        ) {
            return
        }
        val deviceUid = boundDeviceUid.takeIf(String::isNotBlank) ?: return
        controlMutationJob?.cancel()
        controlMutationJob = viewModelScope.launch {
            val result = controlOperations.setMode(deviceUid, mode)
            if (boundDeviceUid == deviceUid) {
                _uiState.update { current -> current.withControlMutationResult(result) }
            }
        }
    }

    fun updateManualFanPercent(percent: Int) {
        val state = _uiState.value
        val capabilities = state.manualFanCapabilities
        if (
            !state.contentEnabled ||
            !state.controlAvailable ||
            state.selectedMode != CoolingControlMode.MANUAL ||
            capabilities?.writable != true
        ) {
            return
        }
        val deviceUid = boundDeviceUid.takeIf(String::isNotBlank) ?: return
        val bounded = percent.coerceIn(capabilities.minimumPercent, capabilities.maximumPercent)
        controlMutationJob?.cancel()
        controlMutationJob = viewModelScope.launch {
            val result = controlOperations.setManualFanPercent(deviceUid, bounded)
            if (boundDeviceUid == deviceUid) {
                _uiState.update { current -> current.withControlMutationResult(result) }
            }
        }
    }

    private fun observeCoolingControl(deviceUid: String) {
        controlObserveJob = viewModelScope.launch {
            controlOperations.observeControl(deviceUid).collect { result ->
                if (boundDeviceUid != deviceUid) return@collect
                _uiState.update { state -> state.withControlReadResult(result) }
            }
        }
    }

    private fun refreshCoolingControl(deviceUid: String) {
        controlRefreshJob = viewModelScope.launch {
            val result = controlOperations.refreshControl(deviceUid)
            if (boundDeviceUid == deviceUid) {
                _uiState.update { state -> state.withControlReadResult(result) }
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
        controlObserveJob?.cancel()
        controlRefreshJob?.cancel()
        controlMutationJob?.cancel()
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
}

private fun DeviceCoolingRootUiState.withControlReadResult(
    result: DeviceCoolingControlResult
): DeviceCoolingRootUiState = when (result) {
    is DeviceCoolingControlResult.Available -> withControlSnapshot(result)
    is DeviceCoolingControlResult.Failed -> copy(
        controlAvailable = false,
        modeSelectionWritable = false,
        manualFanCapabilities = manualFanCapabilities?.copy(writable = false),
        fanPercentNow = null,
        tankTemperatureC = null
    )
}

private fun DeviceCoolingRootUiState.withControlMutationResult(
    result: DeviceCoolingControlResult
): DeviceCoolingRootUiState = when (result) {
    is DeviceCoolingControlResult.Available -> withControlSnapshot(result)
    is DeviceCoolingControlResult.Failed -> this
}

private fun DeviceCoolingRootUiState.withControlSnapshot(
    result: DeviceCoolingControlResult.Available
): DeviceCoolingRootUiState {
    val snapshot = result.snapshot
    return copy(
        controlAvailable = true,
        selectedMode = snapshot.mode,
        supportedModes = snapshot.capabilities.supportedModes,
        modeSelectionWritable = snapshot.capabilities.modeSelectionWritable,
        manualFanCapabilities = snapshot.capabilities.manualFan,
        manualFanPercent = snapshot.manualFanPercent,
        fanPercentNow = snapshot.actualFanPercent,
        tankTemperatureC = snapshot.tankTemperatureC
    )
}

private fun DeviceCoolingRootUiState.withAutomaticSnapshot(
    snapshot: DeviceCoolingAutomaticSettingsSnapshot
): DeviceCoolingRootUiState {
    if (!snapshot.available) return this
    return copy(
        autoStartTemperatureC = snapshot.startTemperatureC ?: autoStartTemperatureC,
        autoMaxTemperatureC = snapshot.maximumSpeedTemperatureC ?: autoMaxTemperatureC
    )
}

data class DeviceCoolingRootUiState(
    val title: String = "",
    val deviceUid: String = "",
    val connectionVisualState: DeviceConnectionVisualState = DeviceConnectionVisualState.OFFLINE,
    val contentEnabled: Boolean = false,
    val controlAvailable: Boolean = false,
    val selectedMode: CoolingControlMode? = null,
    val supportedModes: Set<CoolingControlMode> = emptySet(),
    val modeSelectionWritable: Boolean = false,
    val manualFanCapabilities: DeviceCoolingManualFanCapabilities? = null,
    val manualFanPercent: Int? = null,
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
