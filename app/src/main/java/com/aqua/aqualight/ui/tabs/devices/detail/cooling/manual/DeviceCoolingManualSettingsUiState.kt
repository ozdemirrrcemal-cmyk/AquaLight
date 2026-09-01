package com.aqua.aqualight.ui.tabs.devices.detail.cooling.manual

import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlFailure
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingManualFanCapabilities
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.common.CoolingDataState
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.common.CoolingMutationState
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.common.authoritativeValueOrNull
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.common.isCurrentAuthoritative
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.root.CoolingControlPresentation

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

    val canWrite: Boolean
        get() = controlState.isCurrentAuthoritative &&
            mutationState != CoolingMutationState.Saving &&
            targetPercent != null &&
            capabilities?.writable == true &&
            capabilities?.stepPercent != null
}
