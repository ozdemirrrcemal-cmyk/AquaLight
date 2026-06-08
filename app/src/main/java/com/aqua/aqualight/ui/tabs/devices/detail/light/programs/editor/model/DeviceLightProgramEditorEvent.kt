package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model

sealed interface DeviceLightProgramEditorEvent {

    data class ShowMessage(
        val message: String
    ) : DeviceLightProgramEditorEvent

    data class ShowError(
        val message: String
    ) : DeviceLightProgramEditorEvent

    data class SetLoading(
        val isLoading: Boolean
    ) : DeviceLightProgramEditorEvent

    data object NavigateBack : DeviceLightProgramEditorEvent
}