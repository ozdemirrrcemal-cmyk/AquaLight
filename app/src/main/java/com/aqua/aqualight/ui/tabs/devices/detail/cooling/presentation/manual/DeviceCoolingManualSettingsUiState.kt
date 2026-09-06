package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.manual

import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlFailure
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlMode
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingManualFanCapabilities
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingControlPresentation
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingDataState
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingMutationState
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.authoritativeValueOrNull
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.isCurrentAuthoritative

data class DeviceCoolingManualSettingsUiState(
    val deviceUid: String = "",
    val controlState: CoolingDataState<
        CoolingControlPresentation,
        DeviceCoolingControlFailure
        > = CoolingDataState.Initial,
    val mutationState: CoolingMutationState<DeviceCoolingControlFailure> =
        CoolingMutationState.Idle,
    val draftTargetPercent: Int? = null
) {
    private val presentation: CoolingControlPresentation?
        get() = controlState.authoritativeValueOrNull

    val authoritativeTargetPercent: Int?
        get() = presentation?.manualFanPercent

    val targetPercent: Int?
        get() = draftTargetPercent ?: authoritativeTargetPercent

    val capabilities: DeviceCoolingManualFanCapabilities?
        get() = presentation?.manualFanCapabilities

    val isManualMode: Boolean
        get() = presentation?.selectedMode == DeviceCoolingControlMode.MANUAL

    val canWrite: Boolean
        get() = controlState.isCurrentAuthoritative &&
            isManualMode &&
            targetPercent != null &&
            capabilities?.writable == true &&
            capabilities?.stepPercent != null
}
