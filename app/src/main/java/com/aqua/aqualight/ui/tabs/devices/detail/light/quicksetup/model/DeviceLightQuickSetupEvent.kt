package com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.model

sealed interface DeviceLightQuickSetupEvent {

    data class ShowMessage(
        val message: String
    ) : DeviceLightQuickSetupEvent

    data class ShowError(
        val message: String
    ) : DeviceLightQuickSetupEvent

    data object NavigateBack : DeviceLightQuickSetupEvent
}