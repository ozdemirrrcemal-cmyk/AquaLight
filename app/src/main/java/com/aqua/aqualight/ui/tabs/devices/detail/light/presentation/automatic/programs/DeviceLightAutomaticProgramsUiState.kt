package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic.programs

import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticChannel
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticProgram
import com.aqua.aqualight.ui.common.devicepresence.DeviceConnectionVisualState

internal data class DeviceLightAutomaticProgramsUiState(
    val deviceUid: String = "",
    val connectionVisualState: DeviceConnectionVisualState? = null,
    val revision: Long = 0L,
    val capacity: Int = 0,
    val channels: List<DeviceLightAutomaticChannel> = emptyList(),
    val programs: List<DeviceLightAutomaticProgram> = emptyList(),
    val contentEnabled: Boolean = false,
    val initialLoading: Boolean = false,
    val operationInProgress: Boolean = false
) {
    val canAdd: Boolean
        get() = contentEnabled && capacity > 0 && programs.size < capacity && !operationInProgress
}
