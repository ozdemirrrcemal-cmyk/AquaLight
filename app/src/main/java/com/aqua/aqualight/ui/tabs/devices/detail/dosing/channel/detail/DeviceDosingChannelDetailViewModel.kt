package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelCommittedResult
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

private data class PendingMissedDoseRecovery(
    val targetEnabled: Boolean,
    val committedRevision: Long? = null
)

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
    private var pendingMissedDoseRecovery: PendingMissedDoseRecovery? = null
    private var authoritativeMissedDoseRecoveryEnabled: Boolean = false
    private var authoritativeMissedDoseRecoveryEditable: Boolean = false

    fun bind(
        deviceUidText: String,
        slotIdText: String
    ) {
        val deviceUid = deviceUidText.trim()
        val slotId = slotIdText.trim()
        if (deviceUid.isBlank() || slotId.isBlank()) {
            observeJob?.cancel()
            refreshJob?.cancel()
            mutationJob?.cancel()
            boundDeviceUid = ""
            boundSlotId = ""
            resetMissedDoseRecoveryPresentation()
            mutableDraft.value = DeviceDosingChannelDetailDraft()
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
        resetMissedDoseRecoveryPresentation()
        mutableDraft.value = DeviceDosingChannelDetailDraft()
        observeJob = viewModelScope.launch {
            operations.observe(deviceUid, slotId).collect { snapshot ->
                if (boundDeviceUid != deviceUid || boundSlotId != slotId) return@collect
                if (snapshot == null) {
                    resetMissedDoseRecoveryPresentation()
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
            val result = runCatching { operations.refresh(deviceUid, slotId) }
                .getOrElse { DeviceDosingChannelOperationResult.Failed }
            if (boundDeviceUid != deviceUid || boundSlotId != slotId) return@launch
            if (result is DeviceDosingChannelOperationResult.Success) {
                applySnapshot(result.snapshot)
            }
        }
    }

    fun setMissedDoseRecoveryEnabled(enabled: Boolean) {
        val state = mutableDraft.value
        if (
            pendingMissedDoseRecovery != null ||
            state.operationInProgress ||
            !state.missedDoseRecoveryEditable ||
            state.missedDoseRecoveryEnabled == enabled
        ) {
            return
        }
        mutateMissedDoseRecovery(enabled)
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

    private fun mutateMissedDoseRecovery(enabled: Boolean) {
        val deviceUid = boundDeviceUid
        val slotId = boundSlotId
        if (deviceUid.isBlank() || slotId.isBlank()) return

        mutationJob?.cancel()
        pendingMissedDoseRecovery = PendingMissedDoseRecovery(targetEnabled = enabled)
        mutableDraft.value = mutableDraft.value.copy(
            missedDoseRecoveryEnabled = enabled,
            missedDoseRecoveryEditable = false,
            operationInProgress = true
        )
        mutationJob = viewModelScope.launch {
            val result = runCatching {
                operations.setMissedDoseRecoveryEnabled(deviceUid, slotId, enabled)
            }.getOrElse { DeviceDosingChannelOperationResult.Failed }
            if (boundDeviceUid != deviceUid || boundSlotId != slotId) return@launch
            applyMissedDoseRecoveryResult(result)
        }
    }

    private suspend fun applyMissedDoseRecoveryResult(
        result: DeviceDosingChannelOperationResult
    ) {
        when (result) {
            is DeviceDosingChannelOperationResult.Success -> {
                applySnapshot(result.snapshot)
                mutableDraft.value = mutableDraft.value.copy(operationInProgress = false)
                if (pendingMissedDoseRecovery == null) {
                    eventChannel.send(DeviceDosingChannelDetailEvent.MissedDoseRecoverySaved)
                } else {
                    rollbackMissedDoseRecovery()
                    eventChannel.send(
                        DeviceDosingChannelDetailEvent.OperationFailed(
                            DeviceDosingChannelDetailFailure.STATE_CHANGED
                        )
                    )
                }
            }
            is DeviceDosingChannelCommittedResult -> {
                pendingMissedDoseRecovery = pendingMissedDoseRecovery?.copy(
                    committedRevision = result.revision
                )
                mutableDraft.value = mutableDraft.value.copy(
                    missedDoseRecoveryEditable = authoritativeMissedDoseRecoveryEditable &&
                        pendingMissedDoseRecovery == null,
                    operationInProgress = false
                )
                eventChannel.send(DeviceDosingChannelDetailEvent.MissedDoseRecoverySaved)
                refreshAuthoritative()
            }
            is DeviceDosingChannelOperationResult.Rejected -> {
                rollbackMissedDoseRecovery()
                mutableDraft.value = mutableDraft.value.copy(operationInProgress = false)
                eventChannel.send(
                    DeviceDosingChannelDetailEvent.OperationFailed(result.reason.toDetailFailure())
                )
            }
            DeviceDosingChannelOperationResult.Unavailable -> {
                rollbackMissedDoseRecovery()
                mutableDraft.value = mutableDraft.value.copy(operationInProgress = false)
                eventChannel.send(
                    DeviceDosingChannelDetailEvent.OperationFailed(
                        DeviceDosingChannelDetailFailure.UNAVAILABLE
                    )
                )
            }
            DeviceDosingChannelOperationResult.Failed -> {
                rollbackMissedDoseRecovery()
                mutableDraft.value = mutableDraft.value.copy(operationInProgress = false)
                eventChannel.send(
                    DeviceDosingChannelDetailEvent.OperationFailed(
                        DeviceDosingChannelDetailFailure.TRY_AGAIN
                    )
                )
            }
        }
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
        is DeviceDosingChannelCommittedResult -> {
            // Persisted write is complete; central readback will update the draft when authoritative
            // state is available again. Never project mutation response data as a fake snapshot.
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
        authoritativeMissedDoseRecoveryEnabled =
            snapshot.program?.missedDoseRecoveryEnabled == true
        authoritativeMissedDoseRecoveryEditable = snapshot.program != null &&
            snapshot.scheduling.supportsMissedDoseRecovery &&
            snapshot.controls.programEditable

        val pending = pendingMissedDoseRecovery
        val pendingConfirmed = pending != null &&
            authoritativeMissedDoseRecoveryEnabled == pending.targetEnabled &&
            (pending.committedRevision == null || snapshot.revision >= pending.committedRevision)
        val pendingContradicted = pending?.committedRevision?.let { committedRevision ->
            snapshot.revision >= committedRevision &&
                authoritativeMissedDoseRecoveryEnabled != pending.targetEnabled
        } == true

        if (pendingConfirmed || pendingContradicted) {
            pendingMissedDoseRecovery = null
        }
        val activePending = pendingMissedDoseRecovery

        mutableDraft.value = mutableDraft.value.copy(
            routeValid = snapshot.calibrated && DeviceDosingChannelDetailDraftPolicy
                .isValidCalibrationEpochSeconds(snapshot.lastCalibratedAtEpochSeconds),
            authoritativeStateAvailable = true,
            channelTitle = snapshot.channelTitle,
            lastCalibratedAtEpochSeconds = snapshot.lastCalibratedAtEpochSeconds,
            missedDoseRecoveryEnabled = activePending?.targetEnabled
                ?: authoritativeMissedDoseRecoveryEnabled,
            missedDoseRecoveryEditable = authoritativeMissedDoseRecoveryEditable &&
                activePending == null,
            manualDoseActive = snapshot.activeRun.active &&
                snapshot.activeRun.source == DeviceDosingRunSource.MANUAL &&
                snapshot.controls.stopDoseSupported,
            manualDoseEnabled = snapshot.calibrated &&
                snapshot.controls.manualDoseSupported &&
                !snapshot.activeRun.active,
            resetEnabled = snapshot.controls.resetSupported
        )

        if (pendingContradicted) {
            viewModelScope.launch {
                eventChannel.send(
                    DeviceDosingChannelDetailEvent.OperationFailed(
                        DeviceDosingChannelDetailFailure.STATE_CHANGED
                    )
                )
            }
        }
    }

    private fun rollbackMissedDoseRecovery() {
        pendingMissedDoseRecovery = null
        mutableDraft.value = mutableDraft.value.copy(
            missedDoseRecoveryEnabled = authoritativeMissedDoseRecoveryEnabled,
            missedDoseRecoveryEditable = authoritativeMissedDoseRecoveryEditable
        )
    }

    private fun resetMissedDoseRecoveryPresentation() {
        pendingMissedDoseRecovery = null
        authoritativeMissedDoseRecoveryEnabled = false
        authoritativeMissedDoseRecoveryEditable = false
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
