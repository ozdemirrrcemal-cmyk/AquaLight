package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.manual

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlOperations
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlResult
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingDataState
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingMutationState
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.root.beginControlRefresh
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.root.toRootControlState
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DeviceCoolingManualSettingsViewModel(
    private val operations: DeviceCoolingControlOperations
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceCoolingManualSettingsUiState())
    val uiState: StateFlow<DeviceCoolingManualSettingsUiState> = _uiState.asStateFlow()

    private var boundDeviceUid = ""
    private var observeJob: Job? = null
    private var refreshJob: Job? = null
    private var mutationJob: Job? = null
    private var pendingTargetPercent: Int? = null

    fun bind(deviceUidText: String) {
        val deviceUid = deviceUidText.trim()
        if (deviceUid.isBlank()) {
            clearBinding()
            return
        }
        if (boundDeviceUid == deviceUid) return

        boundDeviceUid = deviceUid
        cancelJobs()
        val initialState = DeviceCoolingManualSettingsUiState(
            deviceUid = deviceUid,
            controlState = operations.currentControl(deviceUid).toRootControlState(
                previous = CoolingDataState.Initial
            )
        )
        _uiState.value = initialState

        observeJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            operations.observeControl(deviceUid).collect { result ->
                if (boundDeviceUid != deviceUid) return@collect
                _uiState.update { state ->
                    state.copy(
                        controlState = result.toRootControlState(state.controlState)
                    )
                }
            }
        }
        refresh()
    }

    fun refresh() {
        val deviceUid = boundDeviceUid.takeIf(String::isNotBlank) ?: return
        refreshJob?.cancel()
        _uiState.update { state ->
            state.copy(controlState = state.controlState.beginControlRefresh())
        }
        refreshJob = viewModelScope.launch {
            val result = operations.refreshControl(deviceUid)
            if (boundDeviceUid != deviceUid) return@launch
            _uiState.update { state ->
                state.copy(controlState = result.toRootControlState(state.controlState))
            }
        }
    }

    fun updateTargetPercent(percent: Int) {
        val state = _uiState.value
        val capabilities = state.capabilities
        val deviceUid = boundDeviceUid.takeIf(String::isNotBlank)

        if (capabilities != null && deviceUid != null && state.canWrite) {
            val bounded = percent.coerceIn(
                capabilities.minimumPercent,
                capabilities.maximumPercent
            )
            _uiState.update { current ->
                current.copy(
                    draftTargetPercent = bounded,
                    mutationState = current.mutationState.afterDraftChange()
                )
            }
        }
    }

    fun commitTargetPercent() {
        val state = _uiState.value
        val deviceUid = boundDeviceUid.takeIf(String::isNotBlank)
        val targetPercent = state.draftTargetPercent
        if (deviceUid != null && targetPercent != null && state.canWrite) {
            pendingTargetPercent = targetPercent
            if (mutationJob?.isActive != true) {
                mutationJob = viewModelScope.launch {
                    writePendingTargets(deviceUid)
                }
            }
        }
    }

    private suspend fun writePendingTargets(deviceUid: String) {
        var continueWriting = true
        while (continueWriting && boundDeviceUid == deviceUid) {
            val targetPercent = pendingTargetPercent
            pendingTargetPercent = null
            when {
                targetPercent == null -> continueWriting = false
                targetPercent == _uiState.value.authoritativeTargetPercent -> {
                    _uiState.update { state -> state.afterRedundantCommit(targetPercent) }
                }
                else -> {
                    _uiState.update { state ->
                        state.copy(mutationState = CoolingMutationState.Saving)
                    }
                    val result = operations.setManualFanPercent(deviceUid, targetPercent)
                    if (boundDeviceUid == deviceUid) {
                        _uiState.update { state -> state.afterMutation(result, targetPercent) }
                        if (result is DeviceCoolingControlResult.Failed) {
                            pendingTargetPercent = null
                            continueWriting = false
                        }
                    } else {
                        continueWriting = false
                    }
                }
            }
        }
    }

    private fun clearBinding() {
        cancelJobs()
        boundDeviceUid = ""
        _uiState.value = DeviceCoolingManualSettingsUiState()
    }

    private fun cancelJobs() {
        observeJob?.cancel()
        refreshJob?.cancel()
        mutationJob?.cancel()
        pendingTargetPercent = null
        observeJob = null
        refreshJob = null
        mutationJob = null
    }
}

private fun DeviceCoolingManualSettingsUiState.afterMutation(
    result: DeviceCoolingControlResult,
    committedTargetPercent: Int
): DeviceCoolingManualSettingsUiState = when (result) {
    is DeviceCoolingControlResult.Available -> copy(
        controlState = result.toRootControlState(controlState),
        mutationState = CoolingMutationState.Saved,
        draftTargetPercent = draftTargetPercent.takeUnless {
            it == committedTargetPercent
        }
    )
    is DeviceCoolingControlResult.Failed -> copy(
        mutationState = CoolingMutationState.OperationError(result.failure)
    )
}

private fun DeviceCoolingManualSettingsUiState.afterRedundantCommit(
    committedTargetPercent: Int
): DeviceCoolingManualSettingsUiState = copy(
    mutationState = CoolingMutationState.Saved,
    draftTargetPercent = draftTargetPercent.takeUnless {
        it == committedTargetPercent
    }
)

private fun CoolingMutationState<DeviceCoolingControlFailure>.afterDraftChange():
    CoolingMutationState<DeviceCoolingControlFailure> = when (this) {
        is CoolingMutationState.OperationError -> CoolingMutationState.Idle
        CoolingMutationState.Idle,
        CoolingMutationState.Saving,
        CoolingMutationState.Saved,
        CoolingMutationState.ValidationError -> this
    }
