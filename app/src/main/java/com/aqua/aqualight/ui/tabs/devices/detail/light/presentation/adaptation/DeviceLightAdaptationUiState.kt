package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.adaptation

import com.aqua.aqualight.application.devices.light.adaptation.DeviceLightAdaptationSnapshot
import com.aqua.aqualight.application.devices.light.adaptation.DeviceLightAdaptationState
import com.aqua.aqualight.ui.common.devicepresence.DeviceConnectionVisualState

internal data class DeviceLightAdaptationUiState(
    val deviceUid: String = "",
    val connectionVisualState: DeviceConnectionVisualState? = null,
    val snapshot: DeviceLightAdaptationSnapshot? = null,
    val selectedStartPercent: Int = 50,
    val selectedDurationDays: Int = 30,
    val configureAfterCompletion: Boolean = false,
    val contentEnabled: Boolean = false,
    val initialLoading: Boolean = false,
    val operationInProgress: Boolean = false
) {
    val screenState: DeviceLightAdaptationScreenState
        get() = when {
            configureAfterCompletion -> DeviceLightAdaptationScreenState.SETUP
            snapshot?.state == DeviceLightAdaptationState.ACTIVE ->
                DeviceLightAdaptationScreenState.ACTIVE
            snapshot?.state == DeviceLightAdaptationState.COMPLETED ->
                DeviceLightAdaptationScreenState.COMPLETED
            else -> DeviceLightAdaptationScreenState.SETUP
        }

    val canStart: Boolean
        get() = contentEnabled &&
            snapshot?.clockReady == true &&
            !operationInProgress

    val canStop: Boolean
        get() = contentEnabled &&
            snapshot?.state == DeviceLightAdaptationState.ACTIVE &&
            !operationInProgress
}

internal enum class DeviceLightAdaptationScreenState {
    SETUP,
    ACTIVE,
    COMPLETED
}

internal data class DeviceLightAdaptationActions(
    val onStartPercentChanged: (Int) -> Unit,
    val onDurationDaysChanged: (Int) -> Unit,
    val onStartClick: () -> Unit,
    val onStopClick: () -> Unit,
    val onConfigureAgainClick: () -> Unit
)
