package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.adaptation

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.light.adaptation.DeviceLightAdaptationFailure
import com.aqua.aqualight.application.devices.light.adaptation.DeviceLightAdaptationMutationResult
import com.aqua.aqualight.application.devices.light.adaptation.DeviceLightAdaptationOperations
import com.aqua.aqualight.application.devices.light.adaptation.DeviceLightAdaptationReadResult
import com.aqua.aqualight.application.devices.light.adaptation.DeviceLightAdaptationSnapshot
import com.aqua.aqualight.application.devices.light.adaptation.DeviceLightAdaptationState
import com.aqua.aqualight.ui.common.devicepresence.DeviceConnectionVisualState
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.toCommercialLightError
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class DeviceLightAdaptationViewModel(
    private val operations: DeviceLightAdaptationOperations
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceLightAdaptationUiState())
    val uiState: StateFlow<DeviceLightAdaptationUiState> = _uiState.asStateFlow()
    private val effectEmitter = DeviceLightAdaptationEffectEmitter()
    val effects: SharedFlow<DeviceLightAdaptationEffect> = effectEmitter.effects

    private var boundDeviceUid = ""
    private var draftDirty = false
    private var observeJob: Job? = null

    fun bind(deviceUidText: String) {
        val deviceUid = deviceUidText.trim()
        require(deviceUid.isNotBlank()) { "Adaptation destination deviceUid must not be blank." }
        if (boundDeviceUid == deviceUid) return
        observeJob?.cancel()
        boundDeviceUid = deviceUid
        draftDirty = false
        _uiState.value = DeviceLightAdaptationUiState(
            deviceUid = deviceUid,
            initialLoading = true
        )
        observeJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            operations.observe(deviceUid).collect { result ->
                when (result) {
                    is DeviceLightAdaptationReadResult.Available -> applySnapshot(result.snapshot)
                    is DeviceLightAdaptationReadResult.Failed -> if (!_uiState.value.initialLoading) {
                        _uiState.applyFailure(result.failure, effectEmitter::closeUnavailable)
                    }
                }
            }
        }
        refresh(showLoading = true, showFailure = true)
    }

    fun refresh() {
        if (
            boundDeviceUid.isBlank() ||
            _uiState.value.initialLoading ||
            _uiState.value.operationInProgress
        ) {
            return
        }
        refresh(showLoading = false, showFailure = false)
    }

    fun updateStartPercent(value: Int) {
        val policy = _uiState.value.snapshot?.policy ?: return
        draftDirty = true
        _uiState.update { state ->
            state.copy(selectedStartPercent = value.coercePolicyStep(
                policy.startPercentMin,
                policy.startPercentMax,
                policy.startPercentStep
            ))
        }
    }

    fun updateDurationDays(value: Int) {
        val policy = _uiState.value.snapshot?.policy ?: return
        draftDirty = true
        _uiState.update { state ->
            state.copy(selectedDurationDays = value.coercePolicyStep(
                policy.durationDaysMin,
                policy.durationDaysMax,
                policy.durationDaysStep
            ))
        }
    }

    fun start() {
        val state = _uiState.value
        val snapshot = state.snapshot ?: return
        if (!state.canStart) return
        mutate {
            operations.start(
                deviceUid = boundDeviceUid,
                expectedRevision = snapshot.revision,
                startPercent = state.selectedStartPercent,
                durationDays = state.selectedDurationDays
            )
        }
    }

    fun stop() {
        val state = _uiState.value
        val snapshot = state.snapshot ?: return
        if (!state.canStop) return
        mutate(successMessage = R.string.device_light_adaptation_stopped_success) {
            operations.stop(boundDeviceUid, snapshot.revision)
        }
    }

    fun configureAgain() {
        val snapshot = _uiState.value.snapshot ?: return
        if (
            snapshot.state != DeviceLightAdaptationState.COMPLETED ||
            !snapshot.firmwareWriteAuthoritative ||
            _uiState.value.operationInProgress
        ) {
            return
        }
        draftDirty = false
        _uiState.update { state ->
            state.copy(
                selectedStartPercent = snapshot.policy.defaultStartPercent,
                selectedDurationDays = snapshot.policy.defaultDurationDays,
                configureAfterCompletion = true
            )
        }
    }

    private fun mutate(
        @StringRes successMessage: Int? = null,
        operation: suspend () -> DeviceLightAdaptationMutationResult
    ) {
        viewModelScope.launch {
            _uiState.update { state -> state.copy(operationInProgress = true) }
            when (val result = operation()) {
                is DeviceLightAdaptationMutationResult.Success -> {
                    draftDirty = false
                    applySnapshot(result.snapshot, operationFinished = true)
                    successMessage?.let { effectEmitter.showMessage(it, true) }
                }
                is DeviceLightAdaptationMutationResult.Failed -> {
                    _uiState.update { state -> state.copy(operationInProgress = false) }
                    effectEmitter.showMessage(
                        result.failure.toCommercialLightError().messageRes,
                        false
                    )
                    if (result.failure == DeviceLightAdaptationFailure.STALE_REVISION) {
                        refresh(showLoading = false, showFailure = false)
                    }
                }
            }
        }
    }

    private fun refresh(showLoading: Boolean, showFailure: Boolean) {
        val deviceUid = boundDeviceUid.takeIf(String::isNotBlank) ?: return
        viewModelScope.launch {
            if (showLoading) _uiState.update { state -> state.copy(initialLoading = true) }
            when (val result = operations.refresh(deviceUid)) {
                is DeviceLightAdaptationReadResult.Available -> applySnapshot(result.snapshot)
                is DeviceLightAdaptationReadResult.Failed -> {
                    _uiState.applyFailure(result.failure, effectEmitter::closeUnavailable)
                    if (showFailure) {
                        effectEmitter.showMessage(
                            result.failure.toCommercialLightError().messageRes,
                            false
                        )
                    }
                }
            }
        }
    }

    private fun applySnapshot(
        snapshot: DeviceLightAdaptationSnapshot,
        operationFinished: Boolean = false
    ) {
        _uiState.update { state ->
            val keepDraft = draftDirty && state.screenState == DeviceLightAdaptationScreenState.SETUP
            state.copy(
                deviceUid = snapshot.deviceUid,
                connectionVisualState = if (snapshot.firmwareWriteAuthoritative) {
                    DeviceConnectionVisualState.ONLINE
                } else {
                    DeviceConnectionVisualState.OFFLINE
                },
                snapshot = snapshot,
                selectedStartPercent = if (keepDraft) {
                    state.selectedStartPercent
                } else {
                    snapshot.startPercent
                },
                selectedDurationDays = if (keepDraft) {
                    state.selectedDurationDays
                } else {
                    snapshot.durationDays
                },
                configureAfterCompletion = if (operationFinished) false else state.configureAfterCompletion,
                contentEnabled = true,
                initialLoading = false,
                operationInProgress = if (operationFinished) false else state.operationInProgress
            )
        }
    }

}

private class DeviceLightAdaptationEffectEmitter {
    private val mutableEffects = MutableSharedFlow<DeviceLightAdaptationEffect>(
        extraBufferCapacity = 2,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val effects: SharedFlow<DeviceLightAdaptationEffect> = mutableEffects.asSharedFlow()

    fun showMessage(@StringRes messageRes: Int, success: Boolean) {
        mutableEffects.tryEmit(DeviceLightAdaptationEffect.ShowMessage(messageRes, success))
    }

    fun closeUnavailable() {
        mutableEffects.tryEmit(DeviceLightAdaptationEffect.CloseUnavailable)
    }
}

internal sealed interface DeviceLightAdaptationEffect {
    data class ShowMessage(
        @StringRes val messageRes: Int,
        val success: Boolean
    ) : DeviceLightAdaptationEffect

    data object CloseUnavailable : DeviceLightAdaptationEffect
}

private fun Int.coercePolicyStep(minimum: Int, maximum: Int, step: Int): Int {
    val clamped = coerceIn(minimum, maximum)
    return minimum + ((clamped - minimum + step / 2) / step) * step
}

private fun DeviceLightAdaptationFailure.connectionState(): DeviceConnectionVisualState =
    if (this == DeviceLightAdaptationFailure.NOT_CONNECTED) {
        DeviceConnectionVisualState.OFFLINE
    } else {
        DeviceConnectionVisualState.WARNING
    }

private fun MutableStateFlow<DeviceLightAdaptationUiState>.applyFailure(
    failure: DeviceLightAdaptationFailure,
    closeUnavailable: () -> Unit
) {
    update { state ->
        state.copy(
            connectionVisualState = failure.connectionState(),
            contentEnabled = false,
            initialLoading = false,
            operationInProgress = false
        )
    }
    if (failure == DeviceLightAdaptationFailure.UNSUPPORTED) closeUnavailable()
}
