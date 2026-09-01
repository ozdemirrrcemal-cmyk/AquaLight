package com.aqua.aqualight.ui.tabs.devices.detail.cooling.automatic

/** Event boundary between the automatic-settings UI and its Fragment/ViewModel host. */
internal data class DeviceCoolingAutomaticSettingsActions(
    val onStartTemperatureClick: () -> Unit,
    val onMaximumTemperatureClick: () -> Unit,
    val onSilentModeChanged: (Boolean) -> Unit,
    val onSave: () -> Unit,
    val onRetry: () -> Unit
)
