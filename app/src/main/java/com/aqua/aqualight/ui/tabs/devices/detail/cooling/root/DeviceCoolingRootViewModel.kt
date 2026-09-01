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
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlSnapshot
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingManualFanCapabilities
import com.aqua.aqualight.ui.common.devicepresence.DeviceConnectionVisualState
import kotlinx.coroutines.CoroutineStart
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
    private val historyOperations: DeviceCoolingTemperatureHistoryOperations,
    private val automaticSettingsOperations: DeviceCoolingAutomaticSettingsOperations
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceCoolingRootUiState())
    val uiState: StateFlow<DeviceCoolingRootUiState> = _uiState.asStateFlow()

    private var boundDeviceUid: String = ""
    private var lastAuthoritativeControlSnapshot: DeviceCoolingControlSnapshot? = null
    private var lastAuthoritativeAutomaticSummary: CoolingAutomaticSummary? = null
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
            lastAuthoritativeControlSnapshot = null
            lastAuthoritativeAutomaticSummary = null
            _uiState.value = DeviceCoolingRootUiState()
            return
        }
        if (boundDeviceUid == deviceUid) return

        boundDeviceUid = deviceUid
        cancelJobs()
        lastAuthoritativeControlSnapshot = null
        lastAuthoritativeAutomaticSummary = null

        var initialState = DeviceCoolingRootUiState(deviceUid = deviceUid)
        operations.current(deviceUid)?.let { snapshot ->
            initialState = snapshot.toRootUiState(initialState)
        }

        val currentControl = controlOperations.currentControl(deviceUid)
        if (currentControl is DeviceCoolingControlResult.Available) {
            lastAuthoritativeControlSnapshot = currentControl.snapshot
        }
        initialState = initialState.withControlPresentation(
            snapshot = lastAuthoritativeControlSnapshot,
            current = currentControl is DeviceCoolingControlResult.Available
        )

        automaticSettingsOperations
            .currentAutomaticSettings(deviceUid)
            .toRootAutomaticSummaryOrNull()
            ?.let { summary ->
                lastAuthoritativeAutomaticSummary = summary
                initialState = initialState.withAutomaticSummary(summary)
            }

        // Match Dosing: publish one state assembled from current authoritative snapshots before
        // Compose starts collecting, rather than emitting synthetic runtime defaults first.
        _uiState.value = initialState

        operations.connect(deviceUid)
        loadOverviewHistory(deviceUid)
        observeCoolingControl(deviceUid)
        refreshCoolingControl(deviceUid)
        observeAutomaticSettings(deviceUid)
        observeJob = viewModelScope.launch {
            operations.observe(deviceUid).collect { snapshot ->
                if (boundDeviceUid != deviceUid) return@collect
                _uiState.update { current ->
                    snapshot?.toRootUiState(current) ?: current.copy(
                        deviceUid = deviceUid,
                        connectionVisualState = DeviceConnectionVisualState.OFFLINE,
                        contentEnabled = false
                    )
                }
            }
        }
    }

    fun selectMode(mode: CoolingControlMode) {
        val state = _uiState.value
        val selectionEnabled =
            state.contentEnabled && state.controlAvailable && state.modeSelectionWritable
        if (!selectionEnabled || mode !in state.supportedModes || state.selectedMode == mode) {
            return
        }
        val deviceUid = boundDeviceUid.takeIf(String::isNotBlank) ?: return
        controlMutationJob?.cancel()
        controlMutationJob = viewModelScope.launch {
            val result = controlOperations.setMode(deviceUid, mode)
            if (boundDeviceUid == deviceUid) {
                applyControlResult(result, readResult = false)
            }
        }
    }

    fun updateManualFanPercent(percent: Int) {
        val state = _uiState.value
        val capabilities = state.manualFanCapabilities
        val controlsEnabled = state.contentEnabled && state.controlAvailable
        if (
            !controlsEnabled ||
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
                applyControlResult(result, readResult = false)
            }
        }
    }

    private fun observeCoolingControl(deviceUid: String) {
        controlObserveJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            controlOperations.observeControl(deviceUid).collect { result ->
                if (boundDeviceUid != deviceUid) return@collect
                applyControlResult(result, readResult = true)
            }
        }
    }

    private fun refreshCoolingControl(deviceUid: String) {
        controlRefreshJob = viewModelScope.launch {
            val result = controlOperations.refreshControl(deviceUid)
            if (boundDeviceUid == deviceUid) {
                applyControlResult(result, readResult = true)
            }
        }
    }

    private fun applyControlResult(
        result: DeviceCoolingControlResult,
        readResult: Boolean
    ) {
        when (result) {
            is DeviceCoolingControlResult.Available -> {
                lastAuthoritativeControlSnapshot = result.snapshot
                _uiState.update { state ->
                    state.withControlPresentation(result.snapshot, current = true)
                }
            }
            is DeviceCoolingControlResult.Failed -> if (readResult) {
                _uiState.update { state ->
                    state.withControlPresentation(
                        snapshot = lastAuthoritativeControlSnapshot,
                        current = false
                    )
                }
            }
        }
    }

    private fun observeAutomaticSettings(deviceUid: String) {
        automaticObserveJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            automaticSettingsOperations.observeAutomaticSettings(deviceUid).collect { snapshot ->
                if (boundDeviceUid != deviceUid) return@collect
                snapshot.toRootAutomaticSummaryOrNull()?.let { summary ->
                    lastAuthoritativeAutomaticSummary = summary
                }
                lastAuthoritativeAutomaticSummary?.let { summary ->
                    _uiState.update { state -> state.withAutomaticSummary(summary) }
                }
            }
        }
        automaticRefreshJob = viewModelScope.launch {
            automaticSettingsOperations.refreshAutomaticSettings(deviceUid)
        }
    }

    private fun loadOverviewHistory(deviceUid: String) {
        historyJob = viewModelScope.launch {
            val result = historyOperations.loadTemperatureHistory(
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

private fun DeviceCoolingRootUiState.withControlPresentation(
    snapshot: DeviceCoolingControlSnapshot?,
    current: Boolean
): DeviceCoolingRootUiState {
    val availableSnapshot = snapshot ?: return copy(
        controlAvailable = false,
        selectedMode = null,
        supportedModes = emptySet(),
        modeSelectionWritable = false,
        manualFanCapabilities = null,
        manualFanPercent = null,
        fanPercentNow = null,
        tankTemperatureC = null
    )
    return copy(
        controlAvailable = current,
        selectedMode = availableSnapshot.mode,
        supportedModes = availableSnapshot.capabilities.supportedModes,
        modeSelectionWritable = current && availableSnapshot.capabilities.modeSelectionWritable,
        manualFanCapabilities = availableSnapshot.capabilities.manualFan?.let { capabilities ->
            if (current) capabilities else capabilities.copy(writable = false)
        },
        manualFanPercent = availableSnapshot.manualFanPercent,
        fanPercentNow = availableSnapshot.actualFanPercent,
        tankTemperatureC = availableSnapshot.tankTemperatureC
    )
}

private fun DeviceCoolingAutomaticSettingsSnapshot.toRootAutomaticSummaryOrNull():
    CoolingAutomaticSummary? {
    if (!loaded || !available) return null
    return startTemperatureC
        ?.takeIf(Double::isFinite)
        ?.let { start ->
            maximumSpeedTemperatureC
                ?.takeIf(Double::isFinite)
                ?.let { maximum ->
                    CoolingAutomaticSummary(
                        startTemperatureC = start,
                        maximumSpeedTemperatureC = maximum
                    )
                }
        }
}

private fun DeviceCoolingRootUiState.withAutomaticSummary(
    summary: CoolingAutomaticSummary
): DeviceCoolingRootUiState = copy(
    autoStartTemperatureC = summary.startTemperatureC,
    autoMaxTemperatureC = summary.maximumSpeedTemperatureC
)

private data class CoolingAutomaticSummary(
    val startTemperatureC: Double,
    val maximumSpeedTemperatureC: Double
)

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
    val autoStartTemperatureC: Double? = null,
    val autoMaxTemperatureC: Double? = null,
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
