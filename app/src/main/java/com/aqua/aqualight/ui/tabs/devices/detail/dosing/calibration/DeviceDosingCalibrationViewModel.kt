package com.aqua.aqualight.ui.tabs.devices.detail.dosing.calibration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.DeviceDosingCalibrationChannelSnapshot
import com.aqua.aqualight.application.devices.DeviceDosingCalibrationOperations
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DeviceDosingCalibrationViewModel(
    private val operations: DeviceDosingCalibrationOperations
) : ViewModel() {

    private val _uiState = MutableStateFlow(DosingCalibrationUiState())
    val uiState: StateFlow<DosingCalibrationUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<DosingCalibrationEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<DosingCalibrationEvent> = _events.asSharedFlow()

    private var boundIdentity: Pair<String, String>? = null
    private var timedDoseJob: Job? = null
    private val pumpSafety by lazy(LazyThreadSafetyMode.NONE) {
        DosingCalibrationPumpSafety(
            operations = operations,
            scope = viewModelScope,
            currentState = _uiState::value,
            updateState = { state -> _uiState.value = state },
            events = _events,
            cancelTimedDose = { timedDoseJob?.cancel() },
            fail = ::fail
        )
    }

    fun bind(deviceUid: String, channelKey: String) {
        val identity = deviceUid.trim() to channelKey.trim().lowercase()
        if (identity.first.isBlank() || identity.second.isBlank()) {
            fail(R.string.device_dosing_calibration_error_unavailable)
            return
        }
        if (boundIdentity == identity) return
        boundIdentity = identity
        load(identity.first, identity.second)
    }

    fun dispatch(action: DosingCalibrationAction) {
        when (action) {
            is DosingCalibrationAction.NameChanged -> _uiState.value = _uiState.value.copy(
                displayNameInput = action.value,
                errorMessageRes = null
            )
            DosingCalibrationAction.ContinueName -> continueName()
            DosingCalibrationAction.PrimePressed -> pumpSafety.primePressed()
            DosingCalibrationAction.PrimeReleased -> pumpSafety.primeReleased()
            DosingCalibrationAction.ContinuePrime -> {
                if (!_uiState.value.primeActive && !_uiState.value.busy) {
                    _uiState.value = _uiState.value.copy(
                        step = DosingCalibrationStep.CALIBRATION_DOSE,
                        operation = DosingCalibrationOperation.IDLE,
                        errorMessageRes = null
                    )
                }
            }
            DosingCalibrationAction.StartCalibrationDose -> startCalibrationDose()
            is DosingCalibrationAction.MeasuredVolumeChanged -> _uiState.value = _uiState.value.copy(
                measuredMlInput = action.value,
                errorMessageRes = null
            )
            DosingCalibrationAction.SubmitMeasuredVolume -> submitMeasuredVolume()
            is DosingCalibrationAction.VerificationVolumeChanged -> _uiState.value = _uiState.value.copy(
                verificationMlInput = action.value,
                errorMessageRes = null
            )
            DosingCalibrationAction.StartVerificationDose -> startVerificationDose()
            DosingCalibrationAction.ConfirmCalibration -> confirmCalibration()
            DosingCalibrationAction.Recalibrate -> recalibrate()
            DosingCalibrationAction.Exit -> pumpSafety.exitCalibration()
        }
    }

    private fun load(deviceUid: String, channelKey: String) {
        _uiState.value = DosingCalibrationUiState(
            deviceUid = deviceUid,
            channelKey = channelKey,
            operation = DosingCalibrationOperation.LOADING
        )
        viewModelScope.launch {
            operations.loadChannel(deviceUid, channelKey)
                .onSuccess(::applyLoadedChannel)
                .onFailure { fail(R.string.device_dosing_calibration_error_connection) }
        }
    }

    private fun applyLoadedChannel(channel: DeviceDosingCalibrationChannelSnapshot) {
        if (!channel.calibrationEditable || !channel.supportsPrime || !channel.supportsManualDose) {
            fail(R.string.device_dosing_calibration_error_unavailable)
            return
        }
        _uiState.value = _uiState.value.copy(
            pumpCount = channel.pumpCount,
            channelNumber = channel.channelNumber,
            channelKey = channel.channelKey,
            originalDisplayName = channel.displayName,
            displayNameInput = channel.displayName,
            minimumMeasuredMl = channel.minimumMeasuredMl,
            maximumMeasuredMl = channel.maximumMeasuredMl,
            maximumVerificationDoseMl = channel.maximumVerificationDoseMl,
            loaded = true,
            operation = DosingCalibrationOperation.IDLE,
            errorMessageRes = null
        )
    }

    private fun continueName() {
        val state = _uiState.value
        if (!state.loaded || state.busy) return
        val displayName = state.displayNameInput.trim()
        if (displayName.isEmpty()) {
            fail(R.string.device_dosing_calibration_error_name_required)
            return
        }
        if (displayName == state.originalDisplayName) {
            _uiState.value = state.copy(step = DosingCalibrationStep.PRIME, errorMessageRes = null)
            return
        }
        _uiState.value = state.copy(operation = DosingCalibrationOperation.SAVING_NAME)
        viewModelScope.launch {
            operations.updateDisplayName(state.deviceUid, state.channelKey, displayName)
                .onSuccess { channel ->
                    _uiState.value = _uiState.value.copy(
                        originalDisplayName = channel.displayName,
                        displayNameInput = channel.displayName,
                        step = DosingCalibrationStep.PRIME,
                        operation = DosingCalibrationOperation.IDLE,
                        errorMessageRes = null
                    )
                }
                .onFailure { fail(R.string.device_dosing_calibration_error_command) }
        }
    }

    private fun startCalibrationDose() {
        val state = _uiState.value
        if (!state.loaded || state.step != DosingCalibrationStep.CALIBRATION_DOSE || state.busy) return
        timedDoseJob?.cancel()
        timedDoseJob = viewModelScope.launch {
            operations.startCalibrationDose(state.deviceUid, state.channelKey)
                .onSuccess { run ->
                    _uiState.value = _uiState.value.copy(
                        calibrationDurationMs = run.durationMs,
                        operation = DosingCalibrationOperation.CALIBRATION_DOSING,
                        errorMessageRes = null
                    )
                    delay(run.durationMs)
                    if (_uiState.value.operation == DosingCalibrationOperation.CALIBRATION_DOSING) {
                        _uiState.value = _uiState.value.copy(
                            step = DosingCalibrationStep.MEASURE,
                            operation = DosingCalibrationOperation.IDLE
                        )
                    }
                }
                .onFailure { fail(R.string.device_dosing_calibration_error_command) }
        }
    }

    private fun submitMeasuredVolume() {
        val state = _uiState.value
        if (!state.loaded || state.step != DosingCalibrationStep.MEASURE || state.busy) return
        val measuredMl = parseCalibrationDecimal(state.measuredMlInput)
        if (
            measuredMl == null ||
            measuredMl !in state.minimumMeasuredMl..state.maximumMeasuredMl
        ) {
            fail(R.string.device_dosing_calibration_error_measurement)
            return
        }
        _uiState.value = state.copy(operation = DosingCalibrationOperation.SAVING_MEASUREMENT)
        viewModelScope.launch {
            operations.finishCalibrationDose(state.deviceUid, state.channelKey, measuredMl)
                .onSuccess { candidate ->
                    _uiState.value = _uiState.value.copy(
                        pendingDoseMsPerMl = candidate.pendingDoseMsPerMl,
                        step = DosingCalibrationStep.VERIFY_DOSE,
                        operation = DosingCalibrationOperation.IDLE,
                        errorMessageRes = null
                    )
                }
                .onFailure { fail(R.string.device_dosing_calibration_error_command) }
        }
    }

    private fun startVerificationDose() {
        val state = _uiState.value
        if (!state.loaded || state.step != DosingCalibrationStep.VERIFY_DOSE || state.busy) return
        val amountMl = parseCalibrationDecimal(state.verificationMlInput)
        if (amountMl == null || amountMl <= 0.0 || amountMl > state.maximumVerificationDoseMl) {
            fail(R.string.device_dosing_calibration_error_verification_volume)
            return
        }
        timedDoseJob?.cancel()
        timedDoseJob = viewModelScope.launch {
            operations.startVerificationDose(state.deviceUid, state.channelKey, amountMl)
                .onSuccess { run ->
                    _uiState.value = _uiState.value.copy(
                        verificationDurationMs = run.durationMs,
                        operation = DosingCalibrationOperation.VERIFYING,
                        errorMessageRes = null
                    )
                    delay(run.durationMs)
                    operations.stopVerificationDose(state.deviceUid, state.channelKey)
                    if (_uiState.value.operation == DosingCalibrationOperation.VERIFYING) {
                        _uiState.value = _uiState.value.copy(
                            step = DosingCalibrationStep.CONFIRM,
                            operation = DosingCalibrationOperation.IDLE
                        )
                    }
                }
                .onFailure { fail(R.string.device_dosing_calibration_error_command) }
        }
    }

    private fun confirmCalibration() {
        val state = _uiState.value
        if (!state.loaded || state.step != DosingCalibrationStep.CONFIRM || state.busy) return
        _uiState.value = state.copy(operation = DosingCalibrationOperation.CONFIRMING)
        viewModelScope.launch {
            operations.confirmCalibration(state.deviceUid, state.channelKey)
                .onSuccess { channel ->
                    if (channel.calibrated) {
                        _events.emit(DosingCalibrationEvent.Completed)
                    } else {
                        fail(R.string.device_dosing_calibration_error_command)
                    }
                }
                .onFailure { fail(R.string.device_dosing_calibration_error_command) }
        }
    }

    private fun recalibrate() {
        val state = _uiState.value
        if (!state.loaded || state.busy) return
        timedDoseJob?.cancel()
        _uiState.value = state.copy(operation = DosingCalibrationOperation.RESETTING)
        viewModelScope.launch {
            operations.cancelCalibration(state.deviceUid, state.channelKey)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        step = DosingCalibrationStep.PRIME,
                        operation = DosingCalibrationOperation.IDLE,
                        measuredMlInput = "",
                        verificationMlInput = "",
                        calibrationDurationMs = 0L,
                        verificationDurationMs = 0L,
                        pendingDoseMsPerMl = 0L,
                        errorMessageRes = null
                    )
                }
                .onFailure { fail(R.string.device_dosing_calibration_error_command) }
        }
    }

    private fun fail(@androidx.annotation.StringRes messageRes: Int) {
        _uiState.value = _uiState.value.copy(
            operation = DosingCalibrationOperation.ERROR,
            errorMessageRes = messageRes
        )
    }
}

internal fun parseCalibrationDecimal(value: String): Double? = value
    .trim()
    .replace(',', '.')
    .takeIf(String::isNotEmpty)
    ?.toDoubleOrNull()
    ?.takeIf(Double::isFinite)
