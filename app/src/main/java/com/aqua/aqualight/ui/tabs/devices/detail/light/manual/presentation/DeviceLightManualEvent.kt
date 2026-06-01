package com.aqua.aqualight.ui.tabs.devices.detail.light.manual.presentation

sealed interface DeviceLightManualEvent {

    data object RefreshRequested : DeviceLightManualEvent

    data class ManualOutputChanged(
        val masterPercent: Int,
        val redPercent: Int,
        val greenPercent: Int,
        val bluePercent: Int,
        val whitePercent: Int
    ) : DeviceLightManualEvent

    data class ApplyTemporaryRequested(
        val masterPercent: Int,
        val redPercent: Int,
        val greenPercent: Int,
        val bluePercent: Int,
        val whitePercent: Int
    ) : DeviceLightManualEvent

    data class SaveAsPresetRequested(
        val masterPercent: Int,
        val redPercent: Int,
        val greenPercent: Int,
        val bluePercent: Int,
        val whitePercent: Int
    ) : DeviceLightManualEvent
}