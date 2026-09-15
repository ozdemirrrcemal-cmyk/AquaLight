package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.system

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.light.system.DeviceLightFanMode
import com.aqua.aqualight.application.devices.light.system.DeviceLightSystemFailure
import com.aqua.aqualight.application.devices.light.system.DeviceLightSystemMutationResult
import com.aqua.aqualight.application.devices.light.system.DeviceLightSystemOperations
import com.aqua.aqualight.application.devices.light.system.DeviceLightSystemReadResult
import com.aqua.aqualight.application.devices.light.system.DeviceLightSystemSettings
import com.aqua.aqualight.application.devices.light.system.DeviceLightSystemSnapshot
import com.aqua.aqualight.ui.common.devicepresence.DeviceConnectionVisualState
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

internal class DeviceLightSystemViewModel(
    private val operations: DeviceLightSystemOperations
) : ViewModel() {

    private val mutableUiState = MutableStateFlow(DeviceLightSystemUiState())
    val uiState: StateFlow<DeviceLightSystemUiState> = mutableUiState.asStateFlow()
    private val effectEmitter = DeviceLightSystemEffectEmitter()
    val effects: SharedFlow<DeviceLightSystemEffect> = effectEmitter.effects

    private var boundDeviceUid = ""
    private var draftDirty = false
    private var observeJob: Job? = null

    fun bind(deviceUidText: String) {
        val deviceUid = deviceUidText.trim()
        require(deviceUid.isNotBlank()) { "System destination deviceUid must not be blank." }
        if (boundDeviceUid == deviceUid) return
        observeJob?.cancel()
        boundDeviceUid = deviceUid
        draftDirty = false
        mutableUiState.value = DeviceLightSystemUiState(
            deviceUid = deviceUid,
            initialLoading = true
        )
        observeJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            operations.observe(deviceUid).collect { result ->
                when (result) {
                    is DeviceLightSystemReadResult.Available -> applySnapshot(result.snapshot)
                    is DeviceLightSystemReadResult.Failed -> {
                        if (!mutableUiState.value.initialLoading) applyFailure(result.failure)
                    }
                }
            }
        }
        refresh(showLoading = true, showFailure = true)
    }

    fun refresh() {
        if (
            boundDeviceUid.isBlank() ||
            mutableUiState.value.initialLoading ||
            mutableUiState.value.operationInProgress
        ) {
            return
        }
        refresh(showLoading = false, showFailure = false)
    }

    fun updateMode(mode: DeviceLightFanMode) {
        if (!mutableUiState.value.controlsEnabled) return
        draftDirty = true
        mutableUiState.update { state -> state.copy(selectedMode = mode) }
    }

    fun updateStartTemperature(value: Int) {
        val state = mutableUiState.value
        val snapshot = state.snapshot ?: return
        if (!state.controlsEnabled) return
        val maximum = minOf(
            snapshot.startTemperaturePolicy.maximum,
            state.selectedFullSpeedTemperatureCelsius - MINIMUM_TEMPERATURE_GAP
        )
        draftDirty = true
        mutableUiState.update { current ->
            current.copy(
                selectedStartTemperatureCelsius = value.coerceIn(
                    snapshot.startTemperaturePolicy.minimum,
                    maximum
                )
            )
        }
    }

    fun updateFullSpeedTemperature(value: Int) {
        val state = mutableUiState.value
        val snapshot = state.snapshot ?: return
        if (!state.controlsEnabled) return
        val minimum = maxOf(
            snapshot.fullSpeedTemperaturePolicy.minimum,
            state.selectedStartTemperatureCelsius + MINIMUM_TEMPERATURE_GAP
        )
        draftDirty = true
        mutableUiState.update { current ->
            current.copy(
                selectedFullSpeedTemperatureCelsius = value.coerceIn(
                    minimum,
                    snapshot.fullSpeedTemperaturePolicy.maximum
                )
            )
        }
    }

    fun updateProtectionThreshold(value: Int) {
        val state = mutableUiState.value
        val snapshot = state.snapshot ?: return
        if (!state.controlsEnabled) return
        draftDirty = true
        mutableUiState.update { current ->
            current.copy(
                selectedProtectionThresholdCelsius = value.coerceIn(
                    snapshot.protectionThresholdPolicy.minimum,
                    snapshot.protectionThresholdPolicy.maximum
                )
            )
        }
    }

    fun save() {
        val state = mutableUiState.value
        if (!state.canSave) return
        viewModelScope.launch {
            mutableUiState.update { current -> current.copy(operationInProgress = true) }
            val settings = DeviceLightSystemSettings(
                mode = state.selectedMode,
                startTemperatureCelsius = state.selectedStartTemperatureCelsius,
                fullSpeedTemperatureCelsius = state.selectedFullSpeedTemperatureCelsius,
                protectionThresholdCelsius = state.selectedProtectionThresholdCelsius
            )
            when (val result = operations.save(boundDeviceUid, settings)) {
                is DeviceLightSystemMutationResult.Success -> {
                    draftDirty = false
                    applySnapshot(result.snapshot, operationFinished = true)
                    effectEmitter.showMessage(R.string.device_light_system_save_success, true)
                }
                is DeviceLightSystemMutationResult.Failed -> {
                    mutableUiState.update { current ->
                        current.copy(operationInProgress = false)
                    }
                    val message = if (result.partialApplyPossible) {
                        R.string.device_light_system_partial_save_error
                    } else {
                        result.failure.messageRes()
                    }
                    effectEmitter.showMessage(message, false)
                    if (result.partialApplyPossible) {
                        draftDirty = false
                        refresh(showLoading = false, showFailure = false)
                    }
                }
            }
        }
    }

    private fun refresh(showLoading: Boolean, showFailure: Boolean) {
        val deviceUid = boundDeviceUid.takeIf(String::isNotBlank) ?: return
        viewModelScope.launch {
            if (showLoading) {
                mutableUiState.update { state -> state.copy(initialLoading = true) }
            }
            when (val result = operations.refresh(deviceUid)) {
                is DeviceLightSystemReadResult.Available -> applySnapshot(result.snapshot)
                is DeviceLightSystemReadResult.Failed -> {
                    applyFailure(result.failure)
                    if (showFailure) {
                        effectEmitter.showMessage(result.failure.messageRes(), false)
                    }
                }
            }
        }
    }

    private fun applySnapshot(
        snapshot: DeviceLightSystemSnapshot,
        operationFinished: Boolean = false
    ) {
        mutableUiState.update { state ->
            val keepDraft = draftDirty && !operationFinished
            state.copy(
                deviceUid = snapshot.deviceUid,
                connectionVisualState = DeviceConnectionVisualState.ONLINE,
                snapshot = snapshot,
                selectedMode = if (keepDraft) state.selectedMode else snapshot.mode,
                selectedStartTemperatureCelsius = if (keepDraft) {
                    state.selectedStartTemperatureCelsius
                } else {
                    snapshot.startTemperatureCelsius
                },
                selectedFullSpeedTemperatureCelsius = if (keepDraft) {
                    state.selectedFullSpeedTemperatureCelsius
                } else {
                    snapshot.fullSpeedTemperatureCelsius
                },
                selectedProtectionThresholdCelsius = if (keepDraft) {
                    state.selectedProtectionThresholdCelsius
                } else {
                    snapshot.protectionThresholdCelsius
                },
                contentEnabled = true,
                initialLoading = false,
                operationInProgress = if (operationFinished) false else state.operationInProgress
            )
        }
    }

    private fun applyFailure(failure: DeviceLightSystemFailure) {
        mutableUiState.update { state ->
            state.copy(
                connectionVisualState = failure.connectionState(),
                contentEnabled = false,
                initialLoading = false,
                operationInProgress = false
            )
        }
        if (failure == DeviceLightSystemFailure.UNSUPPORTED) {
            effectEmitter.closeUnavailable()
        }
    }
}

internal sealed interface DeviceLightSystemEffect {
    data class ShowMessage(
        @StringRes val messageRes: Int,
        val success: Boolean
    ) : DeviceLightSystemEffect

    data object CloseUnavailable : DeviceLightSystemEffect
}

private class DeviceLightSystemEffectEmitter {
    private val mutableEffects = MutableSharedFlow<DeviceLightSystemEffect>(
        extraBufferCapacity = 2,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val effects: SharedFlow<DeviceLightSystemEffect> = mutableEffects.asSharedFlow()

    fun showMessage(@StringRes messageRes: Int, success: Boolean) {
        mutableEffects.tryEmit(DeviceLightSystemEffect.ShowMessage(messageRes, success))
    }

    fun closeUnavailable() {
        mutableEffects.tryEmit(DeviceLightSystemEffect.CloseUnavailable)
    }
}

private fun DeviceLightSystemFailure.connectionState(): DeviceConnectionVisualState =
    if (this == DeviceLightSystemFailure.NOT_CONNECTED) {
        DeviceConnectionVisualState.OFFLINE
    } else {
        DeviceConnectionVisualState.WARNING
    }

@StringRes
private fun DeviceLightSystemFailure.messageRes(): Int = when (this) {
    DeviceLightSystemFailure.NOT_CONNECTED -> R.string.device_light_system_not_connected_error
    DeviceLightSystemFailure.UNSUPPORTED -> R.string.device_light_system_unsupported_error
    DeviceLightSystemFailure.INVALID_DATA -> R.string.device_light_system_invalid_data_error
    DeviceLightSystemFailure.UNAVAILABLE,
    DeviceLightSystemFailure.REJECTED -> R.string.device_light_system_operation_error
}

private const val MINIMUM_TEMPERATURE_GAP = 1
