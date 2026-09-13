package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.root

internal data class DeviceLightDashboardActions(
    val onQuickSetupClick: () -> Unit,
    val onMenuClick: (DeviceLightMenuDestination) -> Unit
)

internal enum class DeviceLightMenuDestination {
    MANUAL_CONTROL,
    AUTOMATIC_PROGRAMS,
    CUSTOM_LIGHT_CURVE,
    ADAPTATION,
    SYSTEM
}
