package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.detail

import androidx.lifecycle.ViewModel
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelDetailDraftPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DeviceDosingChannelDetailDraft(
    val routeValid: Boolean = false,
    val lastCalibratedAtEpochSeconds: Long = 0L,
    val missedDoseRecoveryEnabled: Boolean = false
)

/** Single presentation-state owner for channel detail before firmware mutations are connected. */
internal class DeviceDosingChannelDetailViewModel : ViewModel() {
    private val mutableDraft = MutableStateFlow(DeviceDosingChannelDetailDraft())
    val draft: StateFlow<DeviceDosingChannelDetailDraft> = mutableDraft.asStateFlow()
    private var boundEpochSeconds: Long? = null

    fun bind(lastCalibratedAtEpochSeconds: Long, restoredMissedDoseRecoveryEnabled: Boolean) {
        if (boundEpochSeconds == lastCalibratedAtEpochSeconds) return
        boundEpochSeconds = lastCalibratedAtEpochSeconds
        mutableDraft.value = DeviceDosingChannelDetailDraft(
            routeValid = DeviceDosingChannelDetailDraftPolicy
                .isValidCalibrationEpochSeconds(lastCalibratedAtEpochSeconds),
            lastCalibratedAtEpochSeconds = lastCalibratedAtEpochSeconds,
            missedDoseRecoveryEnabled = restoredMissedDoseRecoveryEnabled
        )
    }

    fun currentDraft(): DeviceDosingChannelDetailDraft = mutableDraft.value

    fun setMissedDoseRecoveryEnabled(enabled: Boolean) {
        mutableDraft.value = mutableDraft.value.copy(missedDoseRecoveryEnabled = enabled)
    }
}
