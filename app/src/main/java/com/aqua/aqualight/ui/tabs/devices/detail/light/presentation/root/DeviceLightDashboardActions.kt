package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.root

internal data class DeviceLightDashboardActions(
    val onQuickSetupClick: () -> Unit,
    val onMenuClick: (DeviceLightMenuDestination) -> Unit,
    val onPlanClick: (DeviceLightPlanDestination) -> Unit
)

internal sealed interface DeviceLightDashboardDestination

internal enum class DeviceLightMenuDestination : DeviceLightDashboardDestination {
    MANUAL_CONTROL,
    AUTOMATIC_PROGRAMS,
    CUSTOM_LIGHT_CURVE,
    ADAPTATION,
    SYSTEM
}

internal sealed interface DeviceLightPlanDestination : DeviceLightDashboardDestination {
    data object AutomaticPrograms : DeviceLightPlanDestination
    data class AutomaticProgramEditor(val programId: String) : DeviceLightPlanDestination
    data object CustomCurveEditor : DeviceLightPlanDestination
}
