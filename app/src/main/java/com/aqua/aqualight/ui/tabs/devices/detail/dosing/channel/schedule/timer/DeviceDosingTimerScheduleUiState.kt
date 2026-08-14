package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.timer

import androidx.compose.runtime.Immutable

@Immutable
internal data class DeviceDosingTimerScheduleUiState(
    val doses: List<DeviceDosingTimerDose>,
    val maxEventsPerChannel: Int,
    val validationMessage: String? = null,
    val actionEnabled: Boolean = doses.isNotEmpty()
) {
    val addEnabled: Boolean
        get() = doses.size < maxEventsPerChannel
}

internal sealed interface DeviceDosingTimerScheduleAction {
    data object Add : DeviceDosingTimerScheduleAction
    data class Edit(val index: Int) : DeviceDosingTimerScheduleAction
    data class Remove(val index: Int) : DeviceDosingTimerScheduleAction
    data object Save : DeviceDosingTimerScheduleAction
}
