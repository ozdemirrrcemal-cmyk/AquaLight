package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.program

import com.aqua.aqualight.application.devices.cooling.program.CoolingProgramTimeSelection
import com.aqua.aqualight.application.devices.cooling.program.CoolingProgramTimeSelections
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingDataState
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingMutationState

fun DeviceCoolingProgramSettingsViewModel.startTimeSelection(
    slotIndex: Int
): CoolingProgramTimeSelection? {
    val state = uiState.value
    val policy = state.policy
    return if (state.isEditable && policy != null) {
        CoolingProgramTimeSelections.forStartTime(
            slots = state.slots,
            policy = policy,
            slotIndex = slotIndex
        )
    } else {
        null
    }
}

fun DeviceCoolingProgramSettingsViewModel.endTimeSelection(
    slotIndex: Int
): CoolingProgramTimeSelection? {
    val state = uiState.value
    val policy = state.policy
    return if (state.isEditable && policy != null) {
        CoolingProgramTimeSelections.forEndTime(
            slots = state.slots,
            policy = policy,
            slotIndex = slotIndex
        )
    } else {
        null
    }
}

internal val DeviceCoolingProgramSettingsUiState.isEditable: Boolean
    get() = (dataState is CoolingDataState.Content || dataState is CoolingDataState.Empty) &&
        mutationState != CoolingMutationState.Saving
