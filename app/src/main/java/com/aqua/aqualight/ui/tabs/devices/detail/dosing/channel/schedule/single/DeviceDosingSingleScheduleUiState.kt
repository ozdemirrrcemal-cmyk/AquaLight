package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.single

import androidx.compose.runtime.Immutable

@Immutable
internal data class DeviceDosingSingleScheduleUiState(
    val dailyDoseMicroliters: Long,
    val startTimeMs: Long,
    val actionEnabled: Boolean = true
)
