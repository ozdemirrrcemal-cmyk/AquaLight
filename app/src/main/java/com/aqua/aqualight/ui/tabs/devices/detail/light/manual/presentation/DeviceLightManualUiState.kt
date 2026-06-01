package com.aqua.aqualight.ui.tabs.devices.detail.light.manual.presentation

data class DeviceLightManualUiState(
    val isLoading: Boolean = false,
    val isCommandRunning: Boolean = false,
    val masterPercent: Int? = null,
    val redPercent: Int? = null,
    val greenPercent: Int? = null,
    val bluePercent: Int? = null,
    val whitePercent: Int? = null,
    val powerStateLabel: String = "",
    val previewAppearanceLabel: String = "",
    val canApplyToProgram: Boolean = false
)