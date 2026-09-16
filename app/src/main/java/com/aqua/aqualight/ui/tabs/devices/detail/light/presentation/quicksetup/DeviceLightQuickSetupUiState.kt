package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.quicksetup

import com.aqua.aqualight.application.aquarium.AquariumCo2Readiness
import com.aqua.aqualight.application.aquarium.AquariumDaylightExposure
import com.aqua.aqualight.application.aquarium.AquariumPlantCoverage
import com.aqua.aqualight.application.aquarium.AquariumShelterAvailability
import com.aqua.aqualight.application.aquarium.AquariumSurfaceGrowth
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanDraft
import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanSnapshot
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightPlanWarning
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightPlantDemand
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupPlan
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupTank

internal const val QUICK_SETUP_WATER_DEPTH_MIN_CM = 5
internal const val QUICK_SETUP_FIXTURE_HEIGHT_MIN_CM = 0
internal const val QUICK_SETUP_FIXTURE_HEIGHT_MAX_CM = 200
internal const val QUICK_SETUP_MINIMUM_END_MINUTE = 8 * 60

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
    val waterDepthCm: Int? = null,
    val fixtureHeightAboveWaterCm: Int? = null,
    val plantDemand: DeviceLightPlantDemand? = null,
    val plantCoverage: AquariumPlantCoverage? = null,
    val co2Readiness: AquariumCo2Readiness? = null,
    val daylightExposure: AquariumDaylightExposure? = null,
    val daylightStartMinute: Int? = null,
    val daylightEndMinute: Int? = null,
    val surfaceGrowth: AquariumSurfaceGrowth? = null,
    val shelterAvailability: AquariumShelterAvailability? = null,
    val programEndMinute: Int? = null,
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
        get() = profileBlocked || plan?.warnings?.any { warning ->
            warning == DeviceLightPlanWarning.NOT_PLANTED_FRESHWATER ||
                warning == DeviceLightPlanWarning.NO_PLANTS
        } == true

    val profileBlocked: Boolean
        get() = tank?.let { current ->
            !current.plantedFreshwater || !current.hasPlants
        } == true

    val hasInstalledPlan: Boolean
        get() = managedPlanSnapshot?.installed == true

    val mode: DeviceLightQuickSetupMode
        get() = when {
            !hasInstalledPlan -> DeviceLightQuickSetupMode.CREATE
            editingInstalledPlan -> DeviceLightQuickSetupMode.EDIT
            else -> DeviceLightQuickSetupMode.ACTIVE
        }

    val requestsWaterDepth: Boolean
        get() = tank?.waterDepthCm == null || mode == DeviceLightQuickSetupMode.EDIT

    val requestsFixtureHeight: Boolean
        get() = tank?.fixtureHeightAboveWaterCm == null ||
            mode == DeviceLightQuickSetupMode.EDIT

    val requestsProgramEnd: Boolean
        get() = programEndMinute == null ||
            programEndMinute < QUICK_SETUP_MINIMUM_END_MINUTE ||
            mode == DeviceLightQuickSetupMode.EDIT

    val requestsDirectDaylightWindow: Boolean
        get() {
            val currentTank = tank ?: return false
            return daylightExposure == AquariumDaylightExposure.DIRECT &&
                (mode == DeviceLightQuickSetupMode.EDIT ||
                    currentTank.daylightExposure != AquariumDaylightExposure.DIRECT ||
                    currentTank.daylightStartMinute == null ||
                    currentTank.daylightEndMinute == null)
        }

    val hasSetupQuestions: Boolean
        get() = requestsWaterDepth || requestsFixtureHeight || requestsProgramEnd ||
            requestsDirectDaylightWindow

    val requestsPlantDemand: Boolean
        get() = tank?.plantDemand == DeviceLightPlantDemand.UNKNOWN

    val requestsPlantCoverage: Boolean
        get() = tank?.plantCoverage == AquariumPlantCoverage.UNKNOWN

    val requestsCo2Readiness: Boolean
        get() {
            val currentTank = tank ?: return false
            return currentTank.co2ComponentPresent &&
                (co2Readiness == null || co2Readiness == AquariumCo2Readiness.UNKNOWN)
        }

    val requestsDaylight: Boolean
        get() = mode == DeviceLightQuickSetupMode.EDIT ||
            tank?.daylightExposure == AquariumDaylightExposure.UNKNOWN

    val requestsSurfaceObservation: Boolean
        get() {
            if (tank == null || mode == DeviceLightQuickSetupMode.ACTIVE) return false
            return surfaceGrowth == null || surfaceGrowth == AquariumSurfaceGrowth.UNKNOWN
        }

    val requestsShelter: Boolean
        get() {
            val currentTank = tank ?: return false
            return currentTank.hasShrimp &&
                (mode == DeviceLightQuickSetupMode.EDIT ||
                    currentTank.shelterAvailability == AquariumShelterAvailability.UNKNOWN ||
                    currentTank.shelterAvailability ==
                    AquariumShelterAvailability.NOT_REQUIRED)
        }

    val hasConditionQuestions: Boolean
        get() = requestsPlantDemand || requestsPlantCoverage || requestsCo2Readiness ||
            requestsDaylight || requestsSurfaceObservation || requestsShelter

    val directDaylightWindowComplete: Boolean
        get() = daylightExposure != AquariumDaylightExposure.DIRECT ||
            (daylightStartMinute != null && daylightEndMinute != null &&
                daylightStartMinute < daylightEndMinute)

    val assessmentComplete: Boolean
        get() {
            val currentTank = tank ?: return false
            return waterDepthCm != null &&
                fixtureHeightAboveWaterCm != null &&
                plantDemand != null &&
                plantDemand != DeviceLightPlantDemand.UNKNOWN &&
                plantCoverage != null &&
                plantCoverage != AquariumPlantCoverage.UNKNOWN &&
                co2Readiness != null &&
                co2Readiness != AquariumCo2Readiness.UNKNOWN &&
                daylightExposure != null &&
                daylightExposure != AquariumDaylightExposure.UNKNOWN &&
                directDaylightWindowComplete &&
                surfaceGrowth != null &&
                surfaceGrowth != AquariumSurfaceGrowth.UNKNOWN &&
                shelterAvailability != null &&
                shelterAvailability != AquariumShelterAvailability.UNKNOWN &&
                programEndMinute != null &&
                programEndMinute >= QUICK_SETUP_MINIMUM_END_MINUTE &&
                (!currentTank.co2ComponentPresent ||
                    co2Readiness != AquariumCo2Readiness.NOT_INSTALLED)
        }

    val missingInputCount: Int
        get() {
            val currentTank = tank ?: return 0
            var count = 0
            if (waterDepthCm == null) count += 1
            if (fixtureHeightAboveWaterCm == null) count += 1
            if (plantDemand == null || plantDemand == DeviceLightPlantDemand.UNKNOWN) count += 1
            if (plantCoverage == null || plantCoverage == AquariumPlantCoverage.UNKNOWN) count += 1
            if (currentTank.co2ComponentPresent &&
                (co2Readiness == null || co2Readiness == AquariumCo2Readiness.UNKNOWN)
            ) {
                count += 1
            }
            if (daylightExposure == null ||
                daylightExposure == AquariumDaylightExposure.UNKNOWN
            ) {
                count += 1
            } else if (!directDaylightWindowComplete) {
                count += 1
            }
            if (surfaceGrowth == null || surfaceGrowth == AquariumSurfaceGrowth.UNKNOWN) count += 1
            if (currentTank.hasShrimp &&
                (shelterAvailability == null ||
                    shelterAvailability == AquariumShelterAvailability.UNKNOWN)
            ) {
                count += 1
            }
            if (programEndMinute == null ||
                programEndMinute < QUICK_SETUP_MINIMUM_END_MINUTE
            ) {
                count += 1
            }
            return count
        }

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
            !profileBlocked &&
            assessmentComplete &&
            plan != null &&
            managedPlanSnapshot != null &&
            !proposalMatchesInstalled &&
            !hasBlockingWarning &&
            !applying
}

internal data class DeviceLightQuickSetupActions(
    val onWaterDepthRequested: () -> Unit,
    val onFixtureHeightRequested: () -> Unit,
    val onProgramEndRequested: () -> Unit,
    val onDaylightStartRequested: () -> Unit,
    val onDaylightEndRequested: () -> Unit,
    val onPlantDemandSelected: (DeviceLightPlantDemand) -> Unit,
    val onPlantCoverageSelected: (AquariumPlantCoverage) -> Unit,
    val onCo2ReadinessSelected: (AquariumCo2Readiness) -> Unit,
    val onDaylightSelected: (AquariumDaylightExposure) -> Unit,
    val onSurfaceGrowthSelected: (AquariumSurfaceGrowth) -> Unit,
    val onShelterSelected: (AquariumShelterAvailability) -> Unit,
    val onToggleDetails: () -> Unit,
    val onEditInstalledPlan: () -> Unit,
    val onCancelEdit: () -> Unit,
    val onApply: () -> Unit
)
