package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic.programs

import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticChannel
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticProgram
import com.aqua.aqualight.ui.common.devicepresence.DeviceConnectionVisualState
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.DeviceLightOperationLoadingState

internal data class DeviceLightAutomaticProgramsUiState(
    val deviceUid: String = "",
    val connectionVisualState: DeviceConnectionVisualState? = null,
    val revision: Long = 0L,
    val capacity: Int = 0,
    val channels: List<DeviceLightAutomaticChannel> = emptyList(),
    val programs: List<DeviceLightAutomaticProgram> = emptyList(),
    val contentEnabled: Boolean = false,
    val firmwareWriteAuthoritative: Boolean = false,
    override val initialLoading: Boolean = false,
    override val operationInProgress: Boolean = false
) : DeviceLightOperationLoadingState {
    val canAdd: Boolean
        get() = contentEnabled && firmwareWriteAuthoritative && capacity > 0 &&
            programs.size < capacity && !operationInProgress

    val canMutate: Boolean
        get() = contentEnabled && firmwareWriteAuthoritative && !operationInProgress
}
