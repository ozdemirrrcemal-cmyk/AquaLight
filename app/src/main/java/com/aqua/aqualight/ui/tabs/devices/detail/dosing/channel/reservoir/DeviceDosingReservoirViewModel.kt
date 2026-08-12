package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.reservoir

import androidx.lifecycle.ViewModel
import com.aqua.aqualight.application.devices.DeviceDosingReservoirDraftPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DeviceDosingReservoirDraft(
    val reservoirCapacityMl: Double = DeviceDosingReservoirDraftPolicy.DEFAULT_CAPACITY_ML,
    val trackingEnabled: Boolean = false,
    val lowLevelAlertEnabled: Boolean = true
)

/** Single presentation-state owner for the firmware-independent reservoir draft. */
internal class DeviceDosingReservoirViewModel : ViewModel() {
    private val mutableDraft = MutableStateFlow(DeviceDosingReservoirDraft())
    val draft: StateFlow<DeviceDosingReservoirDraft> = mutableDraft.asStateFlow()
    private var initialized = false

    fun bindInitial(initial: DeviceDosingReservoirDraft?) {
        if (initialized) return
        initialized = true
        mutableDraft.value = initial ?: DeviceDosingReservoirDraft()
    }

    fun currentDraft(): DeviceDosingReservoirDraft = mutableDraft.value

    fun setTrackingEnabled(enabled: Boolean) {
        mutableDraft.value = mutableDraft.value.copy(trackingEnabled = enabled)
    }

    fun setLowLevelAlertEnabled(enabled: Boolean) {
        mutableDraft.value = mutableDraft.value.copy(lowLevelAlertEnabled = enabled)
    }

    fun setCapacityMl(capacityMl: Double) {
        val valid = DeviceDosingReservoirDraftPolicy.validCapacityOrNull(capacityMl) ?: return
        mutableDraft.value = mutableDraft.value.copy(reservoirCapacityMl = valid)
    }
}
