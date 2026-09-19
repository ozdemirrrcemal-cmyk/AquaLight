package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.manual

internal data class DeviceLightManualControlActions(
    val channels: DeviceLightManualChannelActions,
    val onPresetClick: (DeviceLightManualPresetId) -> Unit,
    val onLoadClick: () -> Unit,
    val onSaveAsClick: () -> Unit,
    val onPowerOffClick: () -> Unit
)

internal data class DeviceLightManualChannelActions(
    val onChannelValueChanged: (DeviceLightManualChannelId, Int) -> Unit,
    val onChannelValueChangeFinished: (DeviceLightManualChannelId) -> Unit,
    val onChannelStep: (DeviceLightManualChannelId, Int) -> Unit
)
