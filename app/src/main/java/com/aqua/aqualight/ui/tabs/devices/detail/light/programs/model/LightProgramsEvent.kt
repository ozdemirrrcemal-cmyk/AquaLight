package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model

sealed class LightProgramsEvent {

    data class ShowMessage(
        val message: String
    ) : LightProgramsEvent()

    data class ShowError(
        val message: String
    ) : LightProgramsEvent()
}