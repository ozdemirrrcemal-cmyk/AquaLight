package com.aqua.aqualight.ui.tabs.devices.detail.light.presets.model

sealed class DeviceLightPresetsEvent {

    data class ShowMessage(
        val message: String
    ) : DeviceLightPresetsEvent()

    data class ShowError(
        val message: String
    ) : DeviceLightPresetsEvent()

    data object NavigateToManualControl : DeviceLightPresetsEvent()
}