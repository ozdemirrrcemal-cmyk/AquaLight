package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.presentation

sealed interface DeviceLightProgramEditorEffect {

    data object CloseScreen : DeviceLightProgramEditorEffect

    data object OpenPreviewDay : DeviceLightProgramEditorEffect

    data class ShowMessage(
        val message: String
    ) : DeviceLightProgramEditorEffect
}