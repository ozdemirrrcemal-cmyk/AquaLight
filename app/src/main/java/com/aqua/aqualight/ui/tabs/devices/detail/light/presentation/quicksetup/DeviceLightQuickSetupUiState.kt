package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.quicksetup

import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanSnapshot
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightPlanWarning
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupPlan
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupTank

internal const val QUICK_SETUP_AQUARIUM_HEIGHT_MIN_CM = 10
internal const val QUICK_SETUP_AQUARIUM_HEIGHT_MAX_CM = 100
internal const val QUICK_SETUP_AUTOMATIC_END_MINUTE = 22 * 60

internal data class DeviceLightQuickSetupUiState(
    val deviceUid: String = "",
    val tank: DeviceLightQuickSetupTank? = null,
    val managedPlanSnapshot: DeviceLightManagedPlanSnapshot? = null,
    val plan: DeviceLightQuickSetupPlan? = null,
    val todayEpochDay: Long = 0L,
    val detailsExpanded: Boolean = false,
    val initialLoading: Boolean = false,
    val applying: Boolean = false,
    val contentEnabled: Boolean = false
) {
    val tankDay: Long?
        get() = tank?.setupDateEpochDay?.let { setup ->
            (todayEpochDay - setup + 1L).coerceAtLeast(1L)
        }

    val hasBlockingWarning: Boolean
        get() = plan?.warnings?.any { warning ->
            warning == DeviceLightPlanWarning.NOT_PLANTED_FRESHWATER ||
                warning == DeviceLightPlanWarning.NO_PLANTS
        } == true

    val canApply: Boolean
        get() = contentEnabled &&
            plan != null &&
            managedPlanSnapshot != null &&
            !hasBlockingWarning &&
            !applying
}

internal data class DeviceLightQuickSetupActions(
    val onToggleDetails: () -> Unit,
    val onApply: () -> Unit
)
