package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelCommittedResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelDetailDraftPolicy
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperationResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperations
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
    val missedDoseRecoverySyncing: Boolean = false,
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

    private val missedDoseIntent = DeviceDosingMissedDoseRecoveryIntentState()
    private var missedDoseAuthority = DeviceDosingMissedDoseRecoveryAuthority()
    private var boundDeviceUid: String = ""
    private var boundSlotId: String = ""
    private var observeJob: Job? = null
    private var refreshJob: Job? = null
    private var mutationJob: Job? = null

    fun bind(deviceUidText: String, slotIdText: String) {
        val deviceUid = deviceUidText.trim()
        val slotId = slotIdText.trim()
        if (boundDeviceUid == deviceUid && boundSlotId == slotId && deviceUid.isNotBlank()) {
            refreshAuthoritative()
            return
        }

        observeJob?.cancel()
        refreshJob?.cancel()
        mutationJob?.cancel()
        missedDoseIntent.reset()
        missedDoseAuthority = DeviceDosingMissedDoseRecoveryAuthority()
        mutableDraft.value = DeviceDosingChannelDetailDraft()
        boundDeviceUid = deviceUid.takeIf { it.isNotBlank() && slotId.isNotBlank() }.orEmpty()
        boundSlotId = slotId.takeIf { boundDeviceUid.isNotBlank() }.orEmpty()
        if (boundDeviceUid.isBlank()) return

        observeJob = viewModelScope.launch {
            operations.observe(deviceUid, slotId).collect { snapshot ->
                if (boundDeviceUid != deviceUid || boundSlotId != slotId) return@collect
                if (snapshot == null) {
                    missedDoseIntent.reset()
                    missedDoseAuthority = DeviceDosingMissedDoseRecoveryAuthority()
                    mutableDraft.value = DeviceDosingChannelDetailDraft()
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
        val blockedByOtherOperation = state.operationInProgress && !state.missedDoseRecoverySyncing
        val shouldAccept = state.missedDoseRecoveryEditable &&
            !blockedByOtherOperation &&
            state.missedDoseRecoveryEnabled != enabled
        if (shouldAccept) {
            missedDoseIntent.request(enabled)
            mutableDraft.value = missedDoseIntent.present(mutableDraft.value, missedDoseAuthority)
            driveMissedDoseRecovery()
        }
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
        mutate(operations::doseStop, DeviceDosingChannelDetailEvent.ManualDoseStopped)
    }

    fun resetChannel() {
        if (!mutableDraft.value.resetEnabled) return
        mutate(operations::reset, DeviceDosingChannelDetailEvent.ChannelReset)
    }

    private fun driveMissedDoseRecovery() {
        when (val action = missedDoseIntent.nextAction(missedDoseAuthority)) {
            DeviceDosingMissedDoseRecoveryAction.Idle -> {
                mutableDraft.value = missedDoseIntent.present(
                    mutableDraft.value,
                    missedDoseAuthority
                )
            }
            is DeviceDosingMissedDoseRecoveryAction.Fail -> {
                mutableDraft.value = missedDoseIntent.present(
                    mutableDraft.value,
                    missedDoseAuthority
                )
                viewModelScope.launch {
                    eventChannel.send(DeviceDosingChannelDetailEvent.OperationFailed(action.failure))
                }
            }
            is DeviceDosingMissedDoseRecoveryAction.Write -> {
                val deviceUid = boundDeviceUid
                val slotId = boundSlotId
                mutableDraft.value = missedDoseIntent.present(
                    mutableDraft.value,
                    missedDoseAuthority
                )
                mutationJob = viewModelScope.launch {
                    val result = if (deviceUid.isBlank() || slotId.isBlank()) {
                        DeviceDosingChannelOperationResult.Unavailable
                    } else {
                        runCatching {
                            operations.setMissedDoseRecoveryEnabled(
                                deviceUid,
                                slotId,
                                action.targetEnabled
                            )
                        }.getOrElse { DeviceDosingChannelOperationResult.Failed }
                    }
                    if (boundDeviceUid != deviceUid || boundSlotId != slotId) return@launch
                    val resolution = missedDoseIntent.onOperationResult(
                        action.targetEnabled,
                        result,
                        missedDoseAuthority
                    )
                    val snapshot = resolution.snapshot
                    if (snapshot != null && snapshot.revision >= missedDoseAuthority.revision) {
                        applySnapshot(snapshot, resolution.failure)
                    } else {
                        mutableDraft.value = missedDoseIntent.present(
                            mutableDraft.value,
                            missedDoseAuthority
                        )
                        resolution.failure?.let {
                            eventChannel.send(DeviceDosingChannelDetailEvent.OperationFailed(it))
                        }
                        driveMissedDoseRecovery()
                    }
                }
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
            successEvent?.let { eventChannel.send(it) }
            true
        }
        is DeviceDosingChannelCommittedResult -> {
            mutableDraft.value = mutableDraft.value.copy(operationInProgress = false)
            successEvent?.let { eventChannel.send(it) }
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

    private fun applySnapshot(
        snapshot: DeviceDosingChannelSnapshot,
        priorMissedDoseFailure: DeviceDosingChannelDetailFailure? = null
    ) {
        val staleSnapshot = mutableDraft.value.authoritativeStateAvailable &&
            snapshot.revision < missedDoseAuthority.revision
        if (staleSnapshot) {
            mutableDraft.value = missedDoseIntent.present(mutableDraft.value, missedDoseAuthority)
            driveMissedDoseRecovery()
            return
        }
        missedDoseAuthority = DeviceDosingMissedDoseRecoveryAuthority(
            enabled = snapshot.program?.missedDoseRecoveryEnabled == true,
            editable = snapshot.program != null &&
                snapshot.scheduling.supportsMissedDoseRecovery &&
                snapshot.controls.programEditable,
            revision = snapshot.revision
        )
        val missedDoseFailure = priorMissedDoseFailure
            ?: missedDoseIntent.onAuthorityChanged(missedDoseAuthority)

        mutableDraft.value = mutableDraft.value.copy(
            routeValid = snapshot.calibrated && DeviceDosingChannelDetailDraftPolicy
                .isValidCalibrationEpochSeconds(snapshot.lastCalibratedAtEpochSeconds),
            authoritativeStateAvailable = true,
            channelTitle = snapshot.channelTitle,
            lastCalibratedAtEpochSeconds = snapshot.lastCalibratedAtEpochSeconds,
            manualDoseActive = snapshot.activeRun.active &&
                snapshot.activeRun.source == DeviceDosingRunSource.MANUAL &&
                snapshot.controls.stopDoseSupported,
            manualDoseEnabled = snapshot.calibrated &&
                snapshot.controls.manualDoseSupported &&
                !snapshot.activeRun.active,
            resetEnabled = snapshot.controls.resetSupported
        )
        mutableDraft.value = missedDoseIntent.present(mutableDraft.value, missedDoseAuthority)
        if (missedDoseFailure != null) {
            viewModelScope.launch {
                eventChannel.send(DeviceDosingChannelDetailEvent.OperationFailed(missedDoseFailure))
            }
        }
        driveMissedDoseRecovery()
    }
}
