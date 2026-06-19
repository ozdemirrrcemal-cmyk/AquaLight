package com.aqua.aqualight.ui.tabs.devices.detail.light.settings.model

sealed interface DeviceLightSettingsEvent {

    data class ShowMessage(
        val message: String
    ) : DeviceLightSettingsEvent

    data class ShowWarning(
        val message: String
    ) : DeviceLightSettingsEvent

    data class ShowError(
        val message: String
    ) : DeviceLightSettingsEvent

    data class SetLoading(
        val isLoading: Boolean
    ) : DeviceLightSettingsEvent
}