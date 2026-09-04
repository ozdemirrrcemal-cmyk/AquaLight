package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.root

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootOperations
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAutomaticCommandResult
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAutomaticSettingsOperations
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTemperatureHistoryOperations
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTemperatureHistoryRange
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlOperations
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlResult
import com.aqua.aqualight.ui.common.devicepresence.DeviceConnectionVisualState
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingDataState
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingMutationState
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DeviceCoolingRootViewModel(
    private val operations: DeviceRootOperations,
    private val controlOperations: DeviceCoolingControlOperations,
    private val historyOperations: DeviceCoolingTemperatureHistoryOperations,
    private val automaticSettingsOperations: DeviceCoolingAutomaticSettingsOperations
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceCoolingRootUiState())
    val uiState: StateFlow<DeviceCoolingRootUiState> = _uiState.asStateFlow()

    private var boundDeviceUid = ""
    private val jobs = DeviceCoolingRootJobs()

    fun bind(deviceUidText: String) {
        val deviceUid = deviceUidText.trim()
        if (deviceUid.isBlank()) {
            clearBinding()
            return
        }
        if (boundDeviceUid == deviceUid) return

        boundDeviceUid = deviceUid
        jobs.cancelAll()

        var initialState = DeviceCoolingRootUiState(
            deviceUid = deviceUid,
            historyState = CoolingDataState.Loading
        )
        operations.current(deviceUid)?.let { snapshot ->
            initialState = snapshot.toRootUiState(initialState)
        }
        initialState = initialState.copy(
            controlState = controlOperations.currentControl(deviceUid).toRootControlState(
                previous = initialState.controlState
            ),
            automaticSummaryState = automaticSettingsOperations
                .currentAutomaticSettings(deviceUid)
                .toRootAutomaticState(initialState.automaticSummaryState)
        )

        // Runtime/domain bootstrap owns live control freshness. Publish the current authoritative
        // snapshot first and then observe; screen entry must never trigger status.get.
        _uiState.value = initialState

        operations.connect(deviceUid)
        loadOverviewHistory(deviceUid)
        observeCoolingControl(deviceUid)
        observeAutomaticSettings(deviceUid)
        jobs.observeJob = viewModelScope.launch {
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
        if (!state.canSelectMode(mode)) return
        val deviceUid = boundDeviceUid.takeIf(String::isNotBlank) ?: return
        launchControlMutation(deviceUid) {
            controlOperations.setMode(deviceUid, mode)
        }
    }

    private fun launchControlMutation(
        deviceUid: String,
        request: suspend () -> DeviceCoolingControlResult
    ) {
        jobs.controlMutationJob?.cancel()
        _uiState.update { state ->
            state.copy(controlMutationState = CoolingMutationState.Saving)
        }
        jobs.controlMutationJob = viewModelScope.launch {
            val result = request()
            if (boundDeviceUid == deviceUid) {
                _uiState.update { state -> state.afterControlMutation(result) }
            }
        }
    }

    private fun observeCoolingControl(deviceUid: String) {
        jobs.controlObserveJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            controlOperations.observeControl(deviceUid).collect { result ->
                if (boundDeviceUid != deviceUid) return@collect
                _uiState.update { state ->
                    state.copy(controlState = result.toRootControlState(state.controlState))
                }
            }
        }
    }

    private fun observeAutomaticSettings(deviceUid: String) {
        jobs.automaticObserveJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            automaticSettingsOperations.observeAutomaticSettings(deviceUid).collect { snapshot ->
                if (boundDeviceUid != deviceUid) return@collect
                _uiState.update { state ->
                    state.copy(
                        automaticSummaryState = snapshot.toRootAutomaticState(
                            state.automaticSummaryState
                        )
                    )
                }
            }
        }
        refreshAutomaticSettings(deviceUid)
    }

    private fun refreshAutomaticSettings(deviceUid: String) {
        _uiState.update { state ->
            state.copy(
                automaticSummaryState = state.automaticSummaryState.beginAutomaticRefresh()
            )
        }
        jobs.automaticRefreshJob = viewModelScope.launch {
            val result = automaticSettingsOperations.refreshAutomaticSettings(deviceUid)
            if (boundDeviceUid != deviceUid) return@launch
            _uiState.update { state ->
                val next = when (result) {
                    DeviceCoolingAutomaticCommandResult.Success ->
                        automaticSettingsOperations.currentAutomaticSettings(deviceUid)
                            .toRootAutomaticStateAfterRefresh(state.automaticSummaryState)
                    is DeviceCoolingAutomaticCommandResult.Failed ->
                        state.automaticSummaryState.afterAutomaticReadFailure(result.failure)
                }
                state.copy(automaticSummaryState = next)
            }
        }
    }

    private fun loadOverviewHistory(deviceUid: String) {
        jobs.historyJob = viewModelScope.launch {
            val result = historyOperations.loadTemperatureHistory(
                deviceUid = deviceUid,
                range = DeviceCoolingTemperatureHistoryRange.HOURS_24
            )
            if (boundDeviceUid != deviceUid) return@launch
            _uiState.update { state ->
                state.copy(historyState = result.toRootHistoryState())
            }
        }
    }

    private fun clearBinding() {
        jobs.cancelAll()
        boundDeviceUid = ""
        _uiState.value = DeviceCoolingRootUiState()
    }
}

private class DeviceCoolingRootJobs {
    var observeJob: Job? = null
    var historyJob: Job? = null
    var automaticObserveJob: Job? = null
    var automaticRefreshJob: Job? = null
    var controlObserveJob: Job? = null
    var controlMutationJob: Job? = null

    fun cancelAll() {
        observeJob?.cancel()
        historyJob?.cancel()
        automaticObserveJob?.cancel()
        automaticRefreshJob?.cancel()
        controlObserveJob?.cancel()
        controlMutationJob?.cancel()

        observeJob = null
        historyJob = null
        automaticObserveJob = null
        automaticRefreshJob = null
        controlObserveJob = null
        controlMutationJob = null
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

private fun DeviceCoolingRootUiState.canSelectMode(mode: CoolingControlMode): Boolean {
    val interactionReady = contentEnabled && controlWriteEnabled
    val modeAllowed = modeSelectionWritable && mode in supportedModes
    return interactionReady && modeAllowed && selectedMode != mode
}

private fun DeviceCoolingRootUiState.afterControlMutation(
    result: DeviceCoolingControlResult
): DeviceCoolingRootUiState = when (result) {
    is DeviceCoolingControlResult.Available -> copy(
        controlState = result.toRootControlState(controlState),
        controlMutationState = CoolingMutationState.Saved
    )
    is DeviceCoolingControlResult.Failed -> copy(
        controlMutationState = CoolingMutationState.OperationError(result.failure)
    )
}
