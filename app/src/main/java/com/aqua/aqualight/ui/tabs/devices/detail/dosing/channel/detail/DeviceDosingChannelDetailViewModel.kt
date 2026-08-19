package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelDetailDraftPolicy
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperationResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelRejection
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingManualDoseDraftPolicy
import com.aqua.aqualight.application.devices.dosing.DeviceDosingRunSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class DeviceDosingChannelDetailDraft(
    val routeValid: Boolean = false,
    val authoritativeStateAvailable: Boolean = false,
    val channelTitle: String = "",
    val lastCalibratedAtEpochSeconds: Long = 0L,
    val missedDoseRecoveryEnabled: Boolean = false,
    val missedDoseRecoveryEditable: Boolean = false,
    val manualDoseActive: Boolean = false,
    val manualDoseEnabled: Boolean = false,
    val resetEnabled: Boolean = false,
    val operationInProgress: Boolean = false
)

enum class DeviceDosingChannelDetailFailure {
    INVALID_INPUT,
    NOT_EDITABLE,
    CALIBRATION_REQUIRED,
    BUSY,
    STATE_CHANGED,
    SAFETY_BLOCKED,
    UNAVAILABLE,
    TRY_AGAIN
}

sealed interface DeviceDosingChannelDetailEvent {
    data object MissedDoseRecoverySaved : DeviceDosingChannelDetailEvent
    data object ManualDoseStarted : DeviceDosingChannelDetailEvent
    data object ManualDoseStopped : DeviceDosingChannelDetailEvent
    data object ChannelReset : DeviceDosingChannelDetailEvent
    data class OperationFailed(
        val failure: DeviceDosingChannelDetailFailure
    ) : DeviceDosingChannelDetailEvent
}

/** State owner for one channel detail screen, backed only by the application boundary. */
internal class DeviceDosingChannelDetailViewModel(
    private val operations: DeviceDosingChannelOperations
) : ViewModel() {
    private val mutableDraft = MutableStateFlow(DeviceDosingChannelDetailDraft())
    val draft: StateFlow<DeviceDosingChannelDetailDraft> = mutableDraft.asStateFlow()

    private val eventChannel = Channel<DeviceDosingChannelDetailEvent>(Channel.BUFFERED)
    val events: Flow<DeviceDosingChannelDetailEvent> = eventChannel.receiveAsFlow()

    private var boundDeviceUid: String = ""
    private var boundSlotId: String = ""
    private var observeJob: Job? = null
    private var refreshJob: Job? = null
    private var mutationJob: Job? = null

    fun bind(
        deviceUidText: String,
        slotIdText: String
    ) {
        val deviceUid = deviceUidText.trim()
        val slotId = slotIdText.trim()
        if (deviceUid.isBlank() || slotId.isBlank()) {
            clearBinding()
            return
        }
        if (boundDeviceUid == deviceUid && boundSlotId == slotId) {
            refreshAuthoritative()
            return
        }

        observeJob?.cancel()
        refreshJob?.cancel()
        mutationJob?.cancel()
        boundDeviceUid = deviceUid
        boundSlotId = slotId
        mutableDraft.value = DeviceDosingChannelDetailDraft()
        observeJob = viewModelScope.launch {
            operations.observe(deviceUid, slotId).collect { snapshot ->
                if (boundDeviceUid != deviceUid || boundSlotId != slotId) return@collect
                if (snapshot == null) {
                    mutableDraft.value = mutableDraft.value.copy(
                        routeValid = false,
                        authoritativeStateAvailable = false,
                        missedDoseRecoveryEnabled = false,
                        missedDoseRecoveryEditable = false,
                        manualDoseActive = false,
                        manualDoseEnabled = false,
                        resetEnabled = false
                    )
                } else {
                    applySnapshot(snapshot)
                }
            }
        }
        refreshAuthoritative()
    }

    fun currentDraft(): DeviceDosingChannelDetailDraft = mutableDraft.value

    fun refreshAuthoritative() {
        val deviceUid = boundDeviceUid
        val slotId = boundSlotId
        if (deviceUid.isBlank() || slotId.isBlank() || refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            applyResult(operations.refresh(deviceUid, slotId), successEvent = null)
        }
    }

    fun setMissedDoseRecoveryEnabled(enabled: Boolean) {
        val state = mutableDraft.value
        if (!state.missedDoseRecoveryEditable || state.missedDoseRecoveryEnabled == enabled) return
        mutate(
            operation = { deviceUid, slotId ->
                operations.setMissedDoseRecoveryEnabled(deviceUid, slotId, enabled)
            },
            successEvent = DeviceDosingChannelDetailEvent.MissedDoseRecoverySaved
        )
    }

    fun startManualDose(rawAmount: String) {
        if (!mutableDraft.value.manualDoseEnabled) return
        val amountMicroliters = DeviceDosingManualDoseDraftPolicy.parseMicroliters(rawAmount)
        if (amountMicroliters == null) {
            viewModelScope.launch {
                eventChannel.send(
                    DeviceDosingChannelDetailEvent.OperationFailed(
                        DeviceDosingChannelDetailFailure.INVALID_INPUT
                    )
                )
            }
            return
        }
        mutate(
            operation = { deviceUid, slotId ->
                operations.doseNow(deviceUid, slotId, amountMicroliters)
            },
            successEvent = DeviceDosingChannelDetailEvent.ManualDoseStarted
        )
    }

    fun stopManualDose() {
        if (!mutableDraft.value.manualDoseActive) return
        mutate(
            operation = operations::doseStop,
            successEvent = DeviceDosingChannelDetailEvent.ManualDoseStopped
        )
    }

    fun resetChannel() {
        if (!mutableDraft.value.resetEnabled) return
        mutate(
            operation = operations::reset,
            successEvent = DeviceDosingChannelDetailEvent.ChannelReset
        )
    }

    private fun mutate(
        operation: suspend (String, String) -> DeviceDosingChannelOperationResult,
        successEvent: DeviceDosingChannelDetailEvent
    ) {
        val deviceUid = boundDeviceUid
        val slotId = boundSlotId
        if (deviceUid.isBlank() || slotId.isBlank() || mutableDraft.value.operationInProgress) return
        mutationJob?.cancel()
        mutableDraft.value = mutableDraft.value.copy(operationInProgress = true)
        mutationJob = viewModelScope.launch {
            val result = runCatching { operation(deviceUid, slotId) }
                .getOrElse { DeviceDosingChannelOperationResult.Failed }
            if (boundDeviceUid != deviceUid || boundSlotId != slotId) return@launch
            applyResult(result, successEvent)
        }
    }

    private suspend fun applyResult(
        result: DeviceDosingChannelOperationResult,
        successEvent: DeviceDosingChannelDetailEvent?
    ): Boolean = when (result) {
        is DeviceDosingChannelOperationResult.Success -> {
            applySnapshot(result.snapshot)
            mutableDraft.value = mutableDraft.value.copy(operationInProgress = false)
            successEvent?.let { event -> eventChannel.send(event) }
            true
        }
        is DeviceDosingChannelOperationResult.Rejected -> {
            mutableDraft.value = mutableDraft.value.copy(operationInProgress = false)
            successEvent?.let {
                eventChannel.send(
                    DeviceDosingChannelDetailEvent.OperationFailed(result.reason.toDetailFailure())
                )
            }
            false
        }
        DeviceDosingChannelOperationResult.Unavailable -> {
            mutableDraft.value = mutableDraft.value.copy(operationInProgress = false)
            successEvent?.let {
                eventChannel.send(
                    DeviceDosingChannelDetailEvent.OperationFailed(
                        DeviceDosingChannelDetailFailure.UNAVAILABLE
                    )
                )
            }
            false
        }
        DeviceDosingChannelOperationResult.Failed -> {
            mutableDraft.value = mutableDraft.value.copy(operationInProgress = false)
            successEvent?.let {
                eventChannel.send(
                    DeviceDosingChannelDetailEvent.OperationFailed(
                        DeviceDosingChannelDetailFailure.TRY_AGAIN
                    )
                )
            }
            false
        }
    }

    private fun applySnapshot(snapshot: DeviceDosingChannelSnapshot) {
        mutableDraft.value = mutableDraft.value.copy(
            routeValid = snapshot.calibrated && DeviceDosingChannelDetailDraftPolicy
                .isValidCalibrationEpochSeconds(snapshot.lastCalibratedAtEpochSeconds),
            authoritativeStateAvailable = true,
            channelTitle = snapshot.channelTitle,
            lastCalibratedAtEpochSeconds = snapshot.lastCalibratedAtEpochSeconds,
            missedDoseRecoveryEnabled = snapshot.program?.missedDoseRecoveryEnabled == true,
            missedDoseRecoveryEditable = snapshot.program != null &&
                snapshot.scheduling.supportsMissedDoseRecovery &&
                snapshot.controls.programEditable,
            manualDoseActive = snapshot.activeRun.active &&
                snapshot.activeRun.source == DeviceDosingRunSource.MANUAL &&
                snapshot.controls.stopDoseSupported,
            manualDoseEnabled = snapshot.calibrated &&
                snapshot.controls.manualDoseSupported &&
                !snapshot.activeRun.active,
            resetEnabled = snapshot.controls.resetSupported
        )
    }

    private fun clearBinding() {
        observeJob?.cancel()
        refreshJob?.cancel()
        mutationJob?.cancel()
        boundDeviceUid = ""
        boundSlotId = ""
        mutableDraft.value = DeviceDosingChannelDetailDraft()
    }
}

private fun DeviceDosingChannelRejection.toDetailFailure(): DeviceDosingChannelDetailFailure =
    when (this) {
        DeviceDosingChannelRejection.INVALID_DRAFT -> DeviceDosingChannelDetailFailure.INVALID_INPUT
        DeviceDosingChannelRejection.NOT_EDITABLE -> DeviceDosingChannelDetailFailure.NOT_EDITABLE
        DeviceDosingChannelRejection.NOT_CALIBRATED ->
            DeviceDosingChannelDetailFailure.CALIBRATION_REQUIRED
        DeviceDosingChannelRejection.BUSY -> DeviceDosingChannelDetailFailure.BUSY
        DeviceDosingChannelRejection.CONFLICT -> DeviceDosingChannelDetailFailure.STATE_CHANGED
        DeviceDosingChannelRejection.UNSAFE -> DeviceDosingChannelDetailFailure.SAFETY_BLOCKED
        DeviceDosingChannelRejection.UNKNOWN -> DeviceDosingChannelDetailFailure.TRY_AGAIN
    }
