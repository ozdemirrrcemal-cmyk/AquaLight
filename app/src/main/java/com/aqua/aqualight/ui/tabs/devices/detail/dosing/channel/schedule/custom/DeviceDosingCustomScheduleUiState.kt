package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.custom

import androidx.compose.runtime.Immutable

@Immutable
internal data class DeviceDosingCustomScheduleUiState(
    val dailyDoseMicroliters: Long,
    val periods: List<DeviceDosingCustomPeriod>,
    val maxEventsPerChannel: Int,
    val maxPeriodsPerChannel: Int,
    val validationMessage: String? = null,
    val actionEnabled: Boolean = dailyDoseMicroliters > 0L && periods.isNotEmpty()
) {
    val addEnabled: Boolean
        get() = periods.size < maxPeriodsPerChannel &&
            DeviceDosingCustomScheduleContract.totalDoseCount(periods) < maxEventsPerChannel
}

internal sealed interface DeviceDosingCustomScheduleAction {
    data object Add : DeviceDosingCustomScheduleAction
    data class Edit(val index: Int) : DeviceDosingCustomScheduleAction
    data class Remove(val index: Int) : DeviceDosingCustomScheduleAction
    data object Save : DeviceDosingCustomScheduleAction
}
