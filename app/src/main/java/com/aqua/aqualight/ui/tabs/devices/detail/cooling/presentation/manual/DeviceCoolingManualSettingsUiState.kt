package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.manual

import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlFailure
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlMode
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingManualFanCapabilities
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingDataState
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingMutationState
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.authoritativeValueOrNull
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.isCurrentAuthoritative
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.root.CoolingControlPresentation

data class DeviceCoolingManualSettingsUiState(
    val deviceUid: String = "",
    val controlState: CoolingDataState<
        CoolingControlPresentation,
        DeviceCoolingControlFailure
        > = CoolingDataState.Initial,
    val mutationState: CoolingMutationState<DeviceCoolingControlFailure> =
        CoolingMutationState.Idle
) {
    private val presentation: CoolingControlPresentation?
        get() = controlState.authoritativeValueOrNull

    val targetPercent: Int?
        get() = presentation?.manualFanPercent

    val capabilities: DeviceCoolingManualFanCapabilities?
        get() = presentation?.manualFanCapabilities

    val isManualMode: Boolean
        get() = presentation?.selectedMode == DeviceCoolingControlMode.MANUAL

    val operationInProgress: Boolean
        get() = mutationState == CoolingMutationState.Saving

    val canWrite: Boolean
        get() = controlState.isCurrentAuthoritative &&
            isManualMode &&
            mutationState != CoolingMutationState.Saving &&
            targetPercent != null &&
            capabilities?.writable == true &&
            capabilities?.stepPercent != null
}
