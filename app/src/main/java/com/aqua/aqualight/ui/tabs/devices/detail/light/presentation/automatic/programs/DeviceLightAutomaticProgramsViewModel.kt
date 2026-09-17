package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic.programs

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticFailure
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticMutationResult
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticOperations
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticReadResult
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticSnapshot
import com.aqua.aqualight.ui.common.devicepresence.DeviceConnectionVisualState
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.toCommercialLightError
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class DeviceLightAutomaticProgramsViewModel(
    private val operations: DeviceLightAutomaticOperations
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceLightAutomaticProgramsUiState())
    val uiState: StateFlow<DeviceLightAutomaticProgramsUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<DeviceLightAutomaticProgramsEffect>(
        extraBufferCapacity = 2,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val effects: SharedFlow<DeviceLightAutomaticProgramsEffect> = _effects.asSharedFlow()

    private var boundDeviceUid = ""
    private var observeJob: Job? = null

    fun bind(deviceUidText: String) {
        val deviceUid = deviceUidText.trim()
        require(deviceUid.isNotBlank()) { "Automatic Light destination deviceUid must not be blank." }
        if (boundDeviceUid == deviceUid) return
        observeJob?.cancel()
        boundDeviceUid = deviceUid
        _uiState.value = DeviceLightAutomaticProgramsUiState(
            deviceUid = deviceUid,
            initialLoading = true
        )
        observeJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            operations.observe(deviceUid).collect { result ->
                when (result) {
                    is DeviceLightAutomaticReadResult.Available -> applySnapshot(result.snapshot)
                    is DeviceLightAutomaticReadResult.Failed -> if (!_uiState.value.initialLoading) {
                        applyFailure(result.failure)
                    }
                }
            }
        }
        refresh(showLoading = true, showFailureMessage = true)
    }

    fun refresh() {
        if (boundDeviceUid.isBlank() ||
            _uiState.value.initialLoading ||
            _uiState.value.operationInProgress
        ) {
            return
        }
        refresh(showLoading = false, showFailureMessage = false)
    }

    fun setEnabled(programId: String, enabled: Boolean) {
        val state = _uiState.value
        val program = state.programs.singleOrNull { item -> item.programId == programId } ?: return
        if (!state.canMutate || program.enabled == enabled) return
        mutate {
            operations.setEnabled(
                deviceUid = boundDeviceUid,
                expectedRevision = state.revision,
                programId = programId,
                enabled = enabled
            )
        }
    }

    fun delete(programId: String) {
        val state = _uiState.value
        if (!state.canMutate) return
        if (state.programs.none { item -> item.programId == programId }) return
        mutate(successMessageRes = R.string.device_light_auto_deleted_success) {
            operations.delete(
                deviceUid = boundDeviceUid,
                expectedRevision = state.revision,
                programId = programId
            )
        }
    }

    private fun mutate(
        @StringRes successMessageRes: Int? = null,
        operation: suspend () -> DeviceLightAutomaticMutationResult
    ) {
        viewModelScope.launch {
            _uiState.update { state -> state.copy(operationInProgress = true) }
            when (val result = operation()) {
                DeviceLightAutomaticMutationResult.Success -> {
                    refreshAfterMutation()
                    successMessageRes?.let { messageRes ->
                        emit(DeviceLightAutomaticProgramsEffect.ShowMessage(messageRes, true))
                    }
                }
                is DeviceLightAutomaticMutationResult.Failed -> {
                    _uiState.update { state -> state.copy(operationInProgress = false) }
                    emit(
                        DeviceLightAutomaticProgramsEffect.ShowMessage(
                            result.failure.toCommercialLightError().messageRes,
                            false
                        )
                    )
                    refresh(showLoading = false, showFailureMessage = false)
                }
            }
        }
    }

    private suspend fun refreshAfterMutation() {
        when (val result = operations.read(boundDeviceUid)) {
            is DeviceLightAutomaticReadResult.Available -> applySnapshot(
                result.snapshot,
                operationFinished = true
            )
            is DeviceLightAutomaticReadResult.Failed -> {
                applyFailure(result.failure)
            }
        }
    }

    private fun refresh(showLoading: Boolean, showFailureMessage: Boolean) {
        val deviceUid = boundDeviceUid.takeIf(String::isNotBlank) ?: return
        viewModelScope.launch {
            if (showLoading) {
                _uiState.update { state -> state.copy(initialLoading = true) }
            }
            when (val result = operations.read(deviceUid)) {
                is DeviceLightAutomaticReadResult.Available -> applySnapshot(
                    result.snapshot,
                    operationFinished = true
                )
                is DeviceLightAutomaticReadResult.Failed -> {
                    applyFailure(result.failure)
                    if (showFailureMessage) {
                        emit(
                            DeviceLightAutomaticProgramsEffect.ShowMessage(
                                result.failure.toCommercialLightError().messageRes,
                                false
                            )
                        )
                    }
                }
            }
        }
    }

    private fun applySnapshot(
        snapshot: DeviceLightAutomaticSnapshot,
        operationFinished: Boolean = false
    ) {
        val operationInProgress = _uiState.value.operationInProgress && !operationFinished
        _uiState.value = DeviceLightAutomaticProgramsUiState(
            deviceUid = snapshot.deviceUid,
            revision = snapshot.revision,
            capacity = snapshot.policy.capacity,
            channels = snapshot.channels,
            programs = snapshot.programs,
            contentEnabled = true,
            firmwareWriteAuthoritative = snapshot.firmwareWriteAuthoritative,
            connectionVisualState = if (snapshot.firmwareWriteAuthoritative) {
                DeviceConnectionVisualState.ONLINE
            } else {
                DeviceConnectionVisualState.OFFLINE
            },
            initialLoading = false,
            operationInProgress = operationInProgress
        )
    }

    private fun applyFailure(failure: DeviceLightAutomaticFailure) {
        _uiState.update { state ->
            state.copy(
                connectionVisualState = failure.connectionState(),
                contentEnabled = state.programs.isNotEmpty() || state.capacity > 0,
                firmwareWriteAuthoritative = false,
                initialLoading = false,
                operationInProgress = false
            )
        }
    }

    private fun emit(effect: DeviceLightAutomaticProgramsEffect) {
        viewModelScope.launch { _effects.emit(effect) }
    }
}

internal sealed interface DeviceLightAutomaticProgramsEffect {
    data class ShowMessage(
        @StringRes val messageRes: Int,
        val success: Boolean
    ) : DeviceLightAutomaticProgramsEffect
}

private fun DeviceLightAutomaticFailure.connectionState(): DeviceConnectionVisualState = when (this) {
    DeviceLightAutomaticFailure.NOT_CONNECTED -> DeviceConnectionVisualState.OFFLINE
    DeviceLightAutomaticFailure.UNAVAILABLE -> DeviceConnectionVisualState.WARNING
    DeviceLightAutomaticFailure.UNSUPPORTED,
    DeviceLightAutomaticFailure.STALE_REVISION,
    DeviceLightAutomaticFailure.CAPACITY_REACHED,
    DeviceLightAutomaticFailure.OVERLAP,
    DeviceLightAutomaticFailure.NOT_FOUND,
    DeviceLightAutomaticFailure.REJECTED,
    DeviceLightAutomaticFailure.INVALID_DATA -> DeviceConnectionVisualState.WARNING
}
