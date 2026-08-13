package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.hourly

import androidx.compose.runtime.Immutable

@Immutable
internal data class DeviceDosingHourlyScheduleUiState(
    val dailyDoseMicroliters: Long,
    val startTimeMs: Long,
    val actionEnabled: Boolean = true
)
