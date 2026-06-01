package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation

sealed interface DeviceLightEvent {

    data object RefreshRequested : DeviceLightEvent

    data class ProgramEnabledChanged(
        val enabled: Boolean
    ) : DeviceLightEvent

    data class TemporaryModeRequested(
        val sceneKey: String,
        val durationMinutes: Int?,
        val untilNextEvent: Boolean
    ) : DeviceLightEvent

    data object RestoreAutoRequested : DeviceLightEvent
}