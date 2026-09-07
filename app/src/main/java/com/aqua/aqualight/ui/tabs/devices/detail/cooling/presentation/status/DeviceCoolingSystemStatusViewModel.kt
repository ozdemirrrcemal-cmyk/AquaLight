package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.status

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.application.devices.DeviceRootOperations
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlFailure
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlOperations
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlResult
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlSnapshot
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingDataFreshness
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingDataState
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.isCoolingContentAvailable
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DeviceCoolingSystemStatusViewModel(
    private val rootOperations: DeviceRootOperations,
    private val controlOperations: DeviceCoolingControlOperations
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceCoolingSystemStatusUiState())
    val uiState: StateFlow<DeviceCoolingSystemStatusUiState> = _uiState.asStateFlow()

    private var boundDeviceUid = ""
    private var rootJob: Job? = null
    private var controlJob: Job? = null

    fun bind(deviceUidText: String) {
        val deviceUid = deviceUidText.trim()
        if (deviceUid.isBlank()) {
            clearBinding()
            return
        }
        if (boundDeviceUid == deviceUid) return

        clearJobs()
        boundDeviceUid = deviceUid
        _uiState.value = DeviceCoolingSystemStatusUiState(
            deviceUid = deviceUid,
            online = rootOperations.current(deviceUid).isCoolingContentAvailable(),
            dataState = controlOperations.currentControl(deviceUid).toStatusDataState(
                CoolingDataState.Initial
            )
        )
        observeRoot(deviceUid)
        observeControl(deviceUid)
    }

    private fun observeRoot(deviceUid: String) {
        rootJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            rootOperations.observe(deviceUid).collect { snapshot ->
                if (boundDeviceUid != deviceUid) return@collect
                _uiState.update { state ->
                    state.copy(online = snapshot.isCoolingContentAvailable())
                }
            }
        }
    }

    private fun observeControl(deviceUid: String) {
        controlJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            controlOperations.observeControl(deviceUid).collect { result ->
                if (boundDeviceUid != deviceUid) return@collect
                _uiState.update { state ->
                    state.copy(dataState = result.toStatusDataState(state.dataState))
                }
            }
        }
    }

    private fun clearBinding() {
        clearJobs()
        boundDeviceUid = ""
        _uiState.value = DeviceCoolingSystemStatusUiState()
    }

    private fun clearJobs() {
        rootJob?.cancel()
        controlJob?.cancel()
        rootJob = null
        controlJob = null
    }
}

private fun DeviceCoolingControlResult.toStatusDataState(
    previous: CoolingDataState<DeviceCoolingControlSnapshot, DeviceCoolingControlFailure>
): CoolingDataState<DeviceCoolingControlSnapshot, DeviceCoolingControlFailure> = when (this) {
    is DeviceCoolingControlResult.Available -> CoolingDataState.Content(snapshot)
    is DeviceCoolingControlResult.Failed -> previous.preserveStatusOrResolve(failure)
}

private fun CoolingDataState<DeviceCoolingControlSnapshot, DeviceCoolingControlFailure>
    .preserveStatusOrResolve(
    failure: DeviceCoolingControlFailure
): CoolingDataState<DeviceCoolingControlSnapshot, DeviceCoolingControlFailure> = when (this) {
    is CoolingDataState.Content -> copy(
        freshness = CoolingDataFreshness.STALE,
        refreshFailure = failure
    )
    is CoolingDataState.Empty -> copy(
        freshness = CoolingDataFreshness.STALE,
        refreshFailure = failure
    )
    CoolingDataState.Initial,
    CoolingDataState.Loading,
    CoolingDataState.Unavailable,
    CoolingDataState.Unsupported,
    is CoolingDataState.OperationError -> failure.toTerminalStatusState()
}

private fun DeviceCoolingControlFailure.toTerminalStatusState(): CoolingDataState<
    DeviceCoolingControlSnapshot,
    DeviceCoolingControlFailure
    > = when (this) {
    DeviceCoolingControlFailure.Unsupported -> CoolingDataState.Unsupported
    DeviceCoolingControlFailure.Unavailable,
    DeviceCoolingControlFailure.NotConnected -> CoolingDataState.Unavailable
    is DeviceCoolingControlFailure.Rejected,
    DeviceCoolingControlFailure.InvalidData -> CoolingDataState.OperationError(this)
}
