package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelDetailDraftPolicy
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperationResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelSnapshot
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
    val channelTitle: String = "",
    val lastCalibratedAtEpochSeconds: Long = 0L,
    val missedDoseRecoveryEnabled: Boolean = false,
    val missedDoseRecoveryEditable: Boolean = false,
    val manualDoseActive: Boolean = false,
    val manualDoseEnabled: Boolean = false,
    val resetEnabled: Boolean = false,
    val maximumManualDoseMicroliters: Long = 0L,
    val operationInProgress: Boolean = false
)

sealed interface DeviceDosingChannelDetailEvent {
    data object MissedDoseRecoverySaved : DeviceDosingChannelDetailEvent
    data object ManualDoseStarted : DeviceDosingChannelDetailEvent
    data object ManualDoseStopped : DeviceDosingChannelDetailEvent
    data object ChannelReset : DeviceDosingChannelDetailEvent
    data object OperationFailed : DeviceDosingChannelDetailEvent
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
        slotIdText: String,
        lastCalibratedAtEpochSeconds: Long,
        restoredMissedDoseRecoveryEnabled: Boolean
    ) {
        val deviceUid = deviceUidText.trim()
        val slotId = slotIdText.trim()
        if (deviceUid.isBlank() || slotId.isBlank()) {
            clearBinding()
            return
        }
        if (boundDeviceUid == deviceUid && boundSlotId == slotId) return

        observeJob?.cancel()
        refreshJob?.cancel()
        mutationJob?.cancel()
        boundDeviceUid = deviceUid
        boundSlotId = slotId
        mutableDraft.value = DeviceDosingChannelDetailDraft(
            routeValid = DeviceDosingChannelDetailDraftPolicy
                .isValidCalibrationEpochSeconds(lastCalibratedAtEpochSeconds),
            lastCalibratedAtEpochSeconds = lastCalibratedAtEpochSeconds,
            missedDoseRecoveryEnabled = restoredMissedDoseRecoveryEnabled
        )
        observeJob = viewModelScope.launch {
            operations.observe(deviceUid, slotId).collect { snapshot ->
                if (boundDeviceUid != deviceUid || boundSlotId != slotId) return@collect
                snapshot?.let(::applySnapshot)
            }
        }
        refreshJob = viewModelScope.launch {
            applyResult(operations.refresh(deviceUid, slotId), successEvent = null)
        }
    }

    fun currentDraft(): DeviceDosingChannelDetailDraft = mutableDraft.value

    fun setMissedDoseRecoveryEnabled(enabled: Boolean) {
        val previous = mutableDraft.value.missedDoseRecoveryEnabled
        if (!mutableDraft.value.missedDoseRecoveryEditable || previous == enabled) return
        mutableDraft.value = mutableDraft.value.copy(missedDoseRecoveryEnabled = enabled)
        mutate(
            operation = { deviceUid, slotId ->
                operations.setMissedDoseRecoveryEnabled(deviceUid, slotId, enabled)
            },
            successEvent = DeviceDosingChannelDetailEvent.MissedDoseRecoverySaved,
            onFailure = {
                mutableDraft.value = mutableDraft.value.copy(
                    missedDoseRecoveryEnabled = previous
                )
            }
        )
    }

    fun startManualDose(amountMicroliters: Long) {
        val state = mutableDraft.value
        if (
            !state.manualDoseEnabled ||
            amountMicroliters <= 0L ||
            amountMicroliters > state.maximumManualDoseMicroliters
        ) {
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
        successEvent: DeviceDosingChannelDetailEvent,
        onFailure: () -> Unit = {}
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
            val success = applyResult(result, successEvent)
            if (!success) onFailure()
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
        else -> {
            mutableDraft.value = mutableDraft.value.copy(operationInProgress = false)
            if (successEvent != null) {
                eventChannel.send(DeviceDosingChannelDetailEvent.OperationFailed)
            }
            false
        }
    }

    private fun applySnapshot(snapshot: DeviceDosingChannelSnapshot) {
        mutableDraft.value = mutableDraft.value.copy(
            routeValid = snapshot.calibrated && DeviceDosingChannelDetailDraftPolicy
                .isValidCalibrationEpochSeconds(snapshot.lastCalibratedAtEpochSeconds),
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
            resetEnabled = snapshot.controls.resetSupported,
            maximumManualDoseMicroliters = snapshot.scheduling.maximumManualDoseMicroliters
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
