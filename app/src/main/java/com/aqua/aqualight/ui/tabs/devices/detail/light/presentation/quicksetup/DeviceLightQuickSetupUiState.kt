package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.quicksetup

import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanDraft
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanSnapshot
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightAlgaeLevel
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightAmbientLight
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightPlanWarning
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupPlan
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupTank

internal const val QUICK_SETUP_AQUARIUM_HEIGHT_MIN_CM = 10
internal const val QUICK_SETUP_AQUARIUM_HEIGHT_MAX_CM = 100
internal const val QUICK_SETUP_AUTOMATIC_END_MINUTE = 22 * 60

internal enum class DeviceLightQuickSetupMode {
    CREATE,
    EDIT,
    ACTIVE
}

internal data class DeviceLightQuickSetupUiState(
    val deviceUid: String = "",
    val tank: DeviceLightQuickSetupTank? = null,
    val managedPlanSnapshot: DeviceLightManagedPlanSnapshot? = null,
    val plan: DeviceLightQuickSetupPlan? = null,
    val todayEpochDay: Long = 0L,
    val ambientLight: DeviceLightAmbientLight? = null,
    val algaeLevel: DeviceLightAlgaeLevel? = null,
    val editingInstalledPlan: Boolean = false,
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

    val hasInstalledPlan: Boolean
        get() = managedPlanSnapshot?.installed == true

    val mode: DeviceLightQuickSetupMode
        get() = when {
            !hasInstalledPlan -> DeviceLightQuickSetupMode.CREATE
            editingInstalledPlan -> DeviceLightQuickSetupMode.EDIT
            else -> DeviceLightQuickSetupMode.ACTIVE
        }

    val assessmentComplete: Boolean
        get() = ambientLight != null && algaeLevel != null

    val proposalMatchesInstalled: Boolean
        get() {
            val proposal = plan ?: return false
            val installed = managedPlanSnapshot ?: return false
            if (!installed.installed) return false
            return proposal.toManagedPlanDraft() == DeviceLightManagedPlanDraft(
                initialStartPercent = installed.initialStartPercent,
                phases = installed.phases
            )
        }

    val canApply: Boolean
        get() = contentEnabled &&
            mode != DeviceLightQuickSetupMode.ACTIVE &&
            assessmentComplete &&
            plan != null &&
            managedPlanSnapshot != null &&
            !proposalMatchesInstalled &&
            !hasBlockingWarning &&
            !applying
}

internal data class DeviceLightQuickSetupActions(
    val onAmbientLightSelected: (DeviceLightAmbientLight) -> Unit,
    val onAlgaeLevelSelected: (DeviceLightAlgaeLevel) -> Unit,
    val onToggleDetails: () -> Unit,
    val onEditInstalledPlan: () -> Unit,
    val onCancelEdit: () -> Unit,
    val onApply: () -> Unit
)
