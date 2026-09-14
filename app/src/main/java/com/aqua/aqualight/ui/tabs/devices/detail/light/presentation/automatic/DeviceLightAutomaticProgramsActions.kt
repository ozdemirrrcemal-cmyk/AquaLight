package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic

internal data class DeviceLightAutomaticProgramsActions(
    val onProgramClick: (String) -> Unit,
    val onEnabledChanged: (String, Boolean) -> Unit,
    val onMoreClick: (String) -> Unit,
    val onAddClick: () -> Unit
)
