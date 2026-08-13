package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.custom

import androidx.compose.runtime.Immutable

@Immutable
internal data class DeviceDosingCustomScheduleUiState(
    val dailyDoseMicroliters: Long,
    val periods: List<DeviceDosingCustomPeriod>,
    val maxPeriods: Int,
    val maxDoseCount: Int,
    val validationMessage: String? = null,
    val actionEnabled: Boolean = dailyDoseMicroliters > 0L &&
        periods.isNotEmpty() &&
        maxPeriods > 0 &&
        maxDoseCount > 0 &&
        periods.size <= maxPeriods &&
        DeviceDosingCustomScheduleContract.totalDoseCount(periods) <= maxDoseCount
)

internal sealed interface DeviceDosingCustomScheduleAction {
    data object Add : DeviceDosingCustomScheduleAction
    data class Edit(val index: Int) : DeviceDosingCustomScheduleAction
    data class Remove(val index: Int) : DeviceDosingCustomScheduleAction
    data object Save : DeviceDosingCustomScheduleAction
}
