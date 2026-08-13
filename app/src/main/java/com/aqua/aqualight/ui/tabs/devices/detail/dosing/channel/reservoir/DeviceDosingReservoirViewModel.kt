package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.reservoir

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperationResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingReservoirDraftPolicy
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DeviceDosingReservoirDraft(
    val reservoirCapacityMl: Double? = null,
    val trackingEnabled: Boolean = false,
    val remainingMl: Double? = null,
    val remainingPercent: Double? = null,
    val accountingCertain: Boolean = true,
    val saveEnabled: Boolean = false,
    val refillAvailable: Boolean = false,
    val busy: Boolean = false
)

/** Reservoir presentation owner backed by the canonical firmware reservoir state. */
internal class DeviceDosingReservoirViewModel(
    private val operations: DeviceDosingChannelOperations
) : ViewModel() {
    private val mutableDraft = MutableStateFlow(DeviceDosingReservoirDraft())
    val draft: StateFlow<DeviceDosingReservoirDraft> = mutableDraft.asStateFlow()

    private var bound: BoundChannel? = null
    private var dirty = false
    private var observeJob: Job? = null

    fun bind(deviceUid: String, slotId: String, restoredDraft: DeviceDosingReservoirDraft?) {
        val target = BoundChannel(deviceUid.trim(), slotId.trim())
        require(target.deviceUid.isNotEmpty() && target.slotId.isNotEmpty())
        if (bound == target) return
        bound = target
        dirty = restoredDraft != null
        restoredDraft?.let { mutableDraft.value = it.copy(busy = false) }
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

    fun currentDraft(): DeviceDosingReservoirDraft = mutableDraft.value

    fun setTrackingEnabled(enabled: Boolean) {
        if (mutableDraft.value.busy) return
        dirty = true
        mutableDraft.value = mutableDraft.value.copy(trackingEnabled = enabled)
        updateActionAvailability()
    }

    fun setCapacityMl(capacityMl: Double) {
        if (mutableDraft.value.busy) return
        val valid = DeviceDosingReservoirDraftPolicy.validCapacityOrNull(capacityMl) ?: return
        dirty = true
        mutableDraft.value = mutableDraft.value.copy(reservoirCapacityMl = valid)
        updateActionAvailability()
    }

    suspend fun save(): Boolean {
        val target = bound ?: return false
        val state = mutableDraft.value
        if (!state.saveEnabled || state.busy) return false
        val capacity = if (state.trackingEnabled) {
            DeviceDosingReservoirDraftPolicy.validCapacityOrNull(state.reservoirCapacityMl ?: return false)
                ?: return false
        } else {
            null
        }
        setBusy(true)
        return try {
            when (
                val result = operations.saveReservoir(
                    target.deviceUid,
                    target.slotId,
                    trackingEnabled = state.trackingEnabled,
                    capacityMl = capacity
                )
            ) {
                is DeviceDosingChannelOperationResult.Success -> {
                    dirty = false
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

    suspend fun refill(): Boolean {
        val target = bound ?: return false
        val state = mutableDraft.value
        if (!state.refillAvailable || state.busy) return false
        setBusy(true)
        return try {
            when (val result = operations.refillReservoir(target.deviceUid, target.slotId)) {
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
        val reservoir = snapshot.reservoir
        val current = mutableDraft.value
        mutableDraft.value = current.copy(
            reservoirCapacityMl = if (dirty) current.reservoirCapacityMl else reservoir.capacityMl,
            trackingEnabled = if (dirty) current.trackingEnabled else reservoir.trackingEnabled,
            remainingMl = reservoir.remainingMl,
            remainingPercent = reservoir.remainingPercent,
            accountingCertain = reservoir.accountingCertain,
            refillAvailable = reservoir.trackingEnabled &&
                reservoir.capacityMl != null &&
                !snapshot.active,
            busy = current.busy
        )
        updateActionAvailability()
    }

    private fun updateActionAvailability() {
        val state = mutableDraft.value
        val validConfiguration = if (state.trackingEnabled) {
            state.reservoirCapacityMl?.let(DeviceDosingReservoirDraftPolicy::validCapacityOrNull) != null
        } else {
            true
        }
        mutableDraft.value = state.copy(
            saveEnabled = dirty && validConfiguration && !state.busy,
            refillAvailable = state.refillAvailable && !state.busy
        )
    }

    private fun setBusy(busy: Boolean) {
        mutableDraft.value = mutableDraft.value.copy(busy = busy)
        updateActionAvailability()
    }

    private data class BoundChannel(val deviceUid: String, val slotId: String)
}
