package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.dashboard

import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.root.CoolingControlMode

/** Event boundary between the Cooling dashboard and its Fragment/ViewModel host. */
internal data class DeviceCoolingDashboardActions(
    val onModeSelected: (CoolingControlMode) -> Unit,
    val onTemperatureHistoryClick: () -> Unit,
    val onSystemStatusClick: () -> Unit,
    val onAutomaticSettingsClick: () -> Unit,
    val onManualSettingsClick: () -> Unit,
    val onProgramSettingsClick: () -> Unit
)
