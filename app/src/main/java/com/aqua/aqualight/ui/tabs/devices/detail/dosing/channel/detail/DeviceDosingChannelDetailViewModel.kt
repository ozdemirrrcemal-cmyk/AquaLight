package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperationResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelDetailDraftPolicy
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DeviceDosingChannelDetailDraft(
    val routeValid: Boolean = false,
    val lastCalibratedAtEpochSeconds: Long = 0L,
    val missedDoseRecoveryEnabled: Boolean = false,
    val missedDoseRecoveryAvailable: Boolean = false,
    val maxManualDoseMl: Double = 0.0,
    val manualDoseAvailable: Boolean = false,
    val channelResetAvailable: Boolean = false,
    val interactionBusy: Boolean = false
)

/** Channel-detail presentation owner backed only by the canonical Dosing channel runtime. */
internal class DeviceDosingChannelDetailViewModel(
    private val operations: DeviceDosingChannelOperations
) : ViewModel() {
    private val mutableDraft = MutableStateFlow(DeviceDosingChannelDetailDraft())
    val draft: StateFlow<DeviceDosingChannelDetailDraft> = mutableDraft.asStateFlow()

    private var bound: BoundChannel? = null
    private var observeJob: Job? = null

    fun bind(deviceUid: String, slotId: String, routeCalibrationEpochSeconds: Long) {
        val target = BoundChannel(deviceUid.trim(), slotId.trim())
        require(target.deviceUid.isNotEmpty() && target.slotId.isNotEmpty())
        if (bound == target) return
        bound = target
        mutableDraft.value = DeviceDosingChannelDetailDraft(
            routeValid = DeviceDosingChannelDetailDraftPolicy
                .isValidCalibrationEpochSeconds(routeCalibrationEpochSeconds),
            lastCalibratedAtEpochSeconds = routeCalibrationEpochSeconds
        )
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            operations.observe(target.deviceUid, target.slotId).collect { snapshot ->
                snapshot?.let(::publish)
            }
        }
        viewModelScope.launch {
            when (val result = operations.refresh(target.deviceUid, target.slotId)) {
                is DeviceDosingChannelOperationResult.Success -> publish(result.snapshot)
                DeviceDosingChannelOperationResult.Unavailable,
                DeviceDosingChannelOperationResult.Failed -> Unit
            }
        }
    }

    fun currentDraft(): DeviceDosingChannelDetailDraft = mutableDraft.value

    fun setMissedDoseRecoveryEnabled(enabled: Boolean) {
        val target = bound ?: return
        val current = mutableDraft.value
        if (!current.missedDoseRecoveryAvailable || current.interactionBusy) return
        viewModelScope.launch {
            setBusy(true)
            try {
                when (
                    val result = operations.setMissedDoseRecoveryEnabled(
                        target.deviceUid,
                        target.slotId,
                        enabled
                    )
                ) {
                    is DeviceDosingChannelOperationResult.Success -> publish(result.snapshot)
                    DeviceDosingChannelOperationResult.Unavailable,
                    DeviceDosingChannelOperationResult.Failed -> Unit
                }
            } finally {
                setBusy(false)
            }
        }
    }

    suspend fun dispenseManualDose(amountMl: Double): Boolean {
        val target = bound ?: return false
        val current = mutableDraft.value
        if (
            current.interactionBusy ||
            !current.manualDoseAvailable ||
            !amountMl.isFinite() ||
            amountMl <= 0.0 ||
            amountMl > current.maxManualDoseMl
        ) {
            return false
        }
        setBusy(true)
        return try {
            when (val result = operations.dispenseManualDose(target.deviceUid, target.slotId, amountMl)) {
                is DeviceDosingChannelOperationResult.Success -> {
                    publish(result.snapshot)
                    true
                }
                DeviceDosingChannelOperationResult.Unavailable,
                DeviceDosingChannelOperationResult.Failed -> false
            }
        } finally {
            setBusy(false)
        }
    }

    suspend fun resetChannel(): Boolean {
        val target = bound ?: return false
        val current = mutableDraft.value
        if (!current.channelResetAvailable || current.interactionBusy) return false
        setBusy(true)
        return try {
            when (val result = operations.resetChannel(target.deviceUid, target.slotId)) {
                is DeviceDosingChannelOperationResult.Success -> {
                    publish(result.snapshot)
                    true
                }
                DeviceDosingChannelOperationResult.Unavailable,
                DeviceDosingChannelOperationResult.Failed -> false
            }
        } finally {
            setBusy(false)
        }
    }

    private fun publish(snapshot: DeviceDosingChannelSnapshot) {
        val program = snapshot.program
        mutableDraft.value = mutableDraft.value.copy(
            routeValid = snapshot.calibrated &&
                DeviceDosingChannelDetailDraftPolicy
                    .isValidCalibrationEpochSeconds(snapshot.lastCalibratedAt),
            lastCalibratedAtEpochSeconds = snapshot.lastCalibratedAt,
            missedDoseRecoveryEnabled = program?.missedDoseRecoveryEnabled == true,
            missedDoseRecoveryAvailable = program != null &&
                snapshot.scheduling.supportsMissedDoseRecovery,
            maxManualDoseMl = snapshot.scheduling.maxManualDoseMl,
            manualDoseAvailable = snapshot.calibrated && !snapshot.active,
            channelResetAvailable = snapshot.scheduling.supportsChannelReset,
            interactionBusy = mutableDraft.value.interactionBusy
        )
    }

    private fun setBusy(busy: Boolean) {
        mutableDraft.value = mutableDraft.value.copy(interactionBusy = busy)
    }

    private data class BoundChannel(val deviceUid: String, val slotId: String)
}
