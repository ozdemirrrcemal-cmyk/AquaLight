package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.root

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationOperations
import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationRequest
import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationResult
import com.aqua.aqualight.application.devices.DeviceMenuUnavailableReason
import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootOperations
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAutomaticSettingsOperations
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTemperatureHistoryLoadResult
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTemperatureHistoryOperations
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTemperatureHistoryRange
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlOperations
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlResult
import com.aqua.aqualight.ui.common.devicepresence.DeviceConnectionVisualState
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingDataState
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingMutationState
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DeviceCoolingRootViewModel(
    private val operations: DeviceRootOperations,
    private val controlOperations: DeviceCoolingControlOperations,
    private val historyOperations: DeviceCoolingTemperatureHistoryOperations,
    private val automaticSettingsOperations: DeviceCoolingAutomaticSettingsOperations,
    private val controlSurfacePreparationOperations: DeviceControlSurfacePreparationOperations
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceCoolingRootUiState())
    val uiState: StateFlow<DeviceCoolingRootUiState> = _uiState.asStateFlow()
    private val surfaceUnavailableEventChannel = Channel<DeviceMenuUnavailableReason>(
        capacity = Channel.BUFFERED
    )
    val surfaceUnavailableEvents: Flow<DeviceMenuUnavailableReason> =
        surfaceUnavailableEventChannel.receiveAsFlow()

    private var boundDeviceUid = ""
    private var liveHistoryRefreshRequested = false
    private val jobs = DeviceCoolingRootJobs()

    fun bind(deviceUidText: String) {
        val deviceUid = deviceUidText.trim()
        if (deviceUid.isBlank()) {
            clearBinding()
            return
        }
        if (boundDeviceUid == deviceUid) return

        val preparedHandoff = controlSurfacePreparationOperations.consumeFreshPreparation(
            deviceUid = deviceUid,
            family = OwnerDeviceFamily.COOLING
        )

        boundDeviceUid = deviceUid
        liveHistoryRefreshRequested = false
        jobs.cancelAll()

        var initialState = DeviceCoolingRootUiState(
            deviceUid = deviceUid,
            historyState = CoolingDataState.Loading
        )
        operations.current(deviceUid)?.let { snapshot ->
            initialState = snapshot.toRootUiState(initialState)
        }
        operations.connect(deviceUid)
        val initialControl = controlOperations.currentControl(deviceUid)
        initialState = initialState.copy(
            controlState = initialControl.toRootControlState(
                previous = initialState.controlState
            ),
            dashboardOverviewState = initialControl.toRootDashboardOverviewState(
                previous = initialState.dashboardOverviewState
            ),
            automaticSummaryState = automaticSettingsOperations
                .currentAutomaticSettings(deviceUid)
                .toRootAutomaticState(initialState.automaticSummaryState),
            surfacePreparationPending = !(
                preparedHandoff && initialControl is DeviceCoolingControlResult.Available
                )
        )
        initialControl.liveWaterSampleOrNull()?.let { sample ->
            initialState = initialState.copy(
                temperatureTimelineState = initialState.temperatureTimelineState
                    .accept(sample)
                    .state
            )
        }

        // Runtime/domain bootstrap owns live Cooling freshness. Screen entry only seeds the last
        // presentation snapshot and observes the central owner; it never requests status.
        _uiState.value = initialState

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
        if (initialState.surfacePreparationPending) {
            prepareRestoredSurface(deviceUid)
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
                val sample = result.liveWaterSampleOrNull()
                var sourceReset = false
                _uiState.update { state ->
                    val timelineUpdate = sample?.let(state.temperatureTimelineState::accept)
                    sourceReset = timelineUpdate?.sourceReset == true
                    state.copy(
                        controlState = result.toRootControlState(state.controlState),
                        dashboardOverviewState = result.toRootDashboardOverviewState(
                            state.dashboardOverviewState
                        ),
                        temperatureTimelineState = timelineUpdate
                            ?.state
                            ?: state.temperatureTimelineState
                    )
                }
                if (sample != null) {
                    requestHistoryForLiveSample(
                        deviceUid = deviceUid,
                        sourceReset = sourceReset
                    )
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
    }

    private fun loadOverviewHistory(deviceUid: String): Boolean {
        if (jobs.historyJob?.isActive == true) return false
        jobs.historyJob = viewModelScope.launch {
            val result = historyOperations.loadTemperatureHistory(
                deviceUid = deviceUid,
                range = DeviceCoolingTemperatureHistoryRange.HOURS_24
            )
            if (boundDeviceUid != deviceUid) return@launch
            _uiState.update { state ->
                val anchoredTimeline = when (result) {
                    is DeviceCoolingTemperatureHistoryLoadResult.Loaded ->
                        state.temperatureTimelineState.withHistoryAnchor(
                            result.snapshot.generatedAtEpochMillis
                        )
                    DeviceCoolingTemperatureHistoryLoadResult.Unsupported,
                    DeviceCoolingTemperatureHistoryLoadResult.Unavailable,
                    is DeviceCoolingTemperatureHistoryLoadResult.Rejected ->
                        state.temperatureTimelineState
                }
                state.copy(
                    historyState = result.toRootHistoryState(state.historyState),
                    temperatureTimelineState = anchoredTimeline
                )
            }
        }
        return true
    }

    private fun requestHistoryForLiveSample(
        deviceUid: String,
        sourceReset: Boolean
    ) {
        if (sourceReset) {
            jobs.historyJob?.cancel()
            liveHistoryRefreshRequested = false
        }
        val historyNeedsLiveSeed = when (_uiState.value.historyState) {
            is CoolingDataState.Empty,
            CoolingDataState.Unavailable -> true
            is CoolingDataState.Content,
            CoolingDataState.Initial,
            CoolingDataState.Loading,
            CoolingDataState.Unsupported,
            is CoolingDataState.OperationError -> false
        }
        if ((sourceReset || historyNeedsLiveSeed) && !liveHistoryRefreshRequested) {
            liveHistoryRefreshRequested = loadOverviewHistory(deviceUid)
        }
    }

    private fun prepareRestoredSurface(deviceUid: String) {
        if (jobs.surfacePreparationJob?.isActive == true) return
        _uiState.update { state -> state.copy(surfacePreparationPending = true) }
        jobs.surfacePreparationJob = viewModelScope.launch {
            val result = controlSurfacePreparationOperations.prepare(
                DeviceControlSurfacePreparationRequest(
                    deviceUid = deviceUid,
                    family = OwnerDeviceFamily.COOLING
                )
            )
            if (boundDeviceUid != deviceUid) return@launch

            when (result) {
                DeviceControlSurfacePreparationResult.Ready -> {
                    controlSurfacePreparationOperations.consumeFreshPreparation(
                        deviceUid = deviceUid,
                        family = OwnerDeviceFamily.COOLING
                    )
                    when (val control = controlOperations.currentControl(deviceUid)) {
                        is DeviceCoolingControlResult.Available -> _uiState.update { state ->
                            state.copy(
                                controlState = control.toRootControlState(state.controlState),
                                dashboardOverviewState = control.toRootDashboardOverviewState(
                                    state.dashboardOverviewState
                                ),
                                surfacePreparationPending = false
                            )
                        }
                        is DeviceCoolingControlResult.Failed -> finishUnavailablePreparation()
                    }
                }
                is DeviceControlSurfacePreparationResult.Unavailable ->
                    finishUnavailablePreparation(result.reason)
            }
        }
    }

    private suspend fun finishUnavailablePreparation(
        reason: DeviceMenuUnavailableReason = DeviceMenuUnavailableReason.CURRENT_LIVENESS_NOT_PROVEN
    ) {
        _uiState.update { state -> state.copy(surfacePreparationPending = false) }
        surfaceUnavailableEventChannel.send(reason)
    }

    private fun clearBinding() {
        jobs.cancelAll()
        boundDeviceUid = ""
        liveHistoryRefreshRequested = false
        _uiState.value = DeviceCoolingRootUiState()
    }
}

private fun DeviceCoolingControlResult.liveWaterSampleOrNull() =
    (this as? DeviceCoolingControlResult.Available)
        ?.snapshot
        ?.telemetry
        ?.waterTemperatureSample

private class DeviceCoolingRootJobs {
    var observeJob: Job? = null
    var historyJob: Job? = null
    var automaticObserveJob: Job? = null
    var controlObserveJob: Job? = null
    var controlMutationJob: Job? = null
    var surfacePreparationJob: Job? = null

    fun cancelAll() {
        observeJob?.cancel()
        historyJob?.cancel()
        automaticObserveJob?.cancel()
        controlObserveJob?.cancel()
        controlMutationJob?.cancel()
        surfacePreparationJob?.cancel()

        observeJob = null
        historyJob = null
        automaticObserveJob = null
        controlObserveJob = null
        controlMutationJob = null
        surfacePreparationJob = null
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
        dashboardOverviewState = result.toRootDashboardOverviewState(dashboardOverviewState),
        controlMutationState = CoolingMutationState.Saved
    )
    is DeviceCoolingControlResult.Failed -> copy(
        controlMutationState = CoolingMutationState.OperationError(result.failure)
    )
}
