package com.aqua.aqualight.ui.tabs.devices.detail.light.manual.model

sealed interface ManualLightEvent {

    data class ShowMessage(
        val message: String
    ) : ManualLightEvent

    data class ShowError(
        val message: String
    ) : ManualLightEvent

    data object ShowSavePresetSheet : ManualLightEvent
}