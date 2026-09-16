package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.quicksetup

import com.aqua.aqualight.application.devices.light.automation.DeviceLightManagedPlanSnapshot
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightAmbientLevel
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightPlantDemand
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightPlantDensity
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupPlan
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupTank

internal const val QUICK_SETUP_WATER_DEPTH_MIN_CM = 10
internal const val QUICK_SETUP_WATER_DEPTH_MAX_CM = 100
internal const val QUICK_SETUP_FIXTURE_HEIGHT_MIN_CM = 0
internal const val QUICK_SETUP_FIXTURE_HEIGHT_MAX_CM = 60
internal const val QUICK_SETUP_PPFD_MIN = 20
internal const val QUICK_SETUP_PPFD_MAX = 500
internal const val QUICK_SETUP_LIGHTS_OFF_MINUTE_MIN = 1_200
internal const val QUICK_SETUP_LIGHTS_OFF_MINUTE_MAX = 1_410
internal const val QUICK_SETUP_DEFAULT_WATER_DEPTH_CM = 35
internal const val QUICK_SETUP_DEFAULT_LIGHTS_OFF_MINUTE = 1_320
internal const val QUICK_SETUP_WATER_SURFACE_CLEARANCE_CM = 5

internal enum class DeviceLightQuickSetupStep {
    TANK_DATA,
    PREFERENCES,
    PLAN
}

internal sealed interface DeviceLightQuickSetupPreferenceChange {
    data class PlantDemand(val value: DeviceLightPlantDemand) :
        DeviceLightQuickSetupPreferenceChange

    data class PlantDensity(val value: DeviceLightPlantDensity) :
        DeviceLightQuickSetupPreferenceChange

    data class WaterDepth(val value: Int) : DeviceLightQuickSetupPreferenceChange
    data class FixtureHeight(val value: Int) : DeviceLightQuickSetupPreferenceChange
    data class AmbientLevel(val value: DeviceLightAmbientLevel) :
        DeviceLightQuickSetupPreferenceChange

    data class Co2Ready(val value: Boolean) : DeviceLightQuickSetupPreferenceChange
    data class ActiveSoil(val value: Boolean) : DeviceLightQuickSetupPreferenceChange
    data class LightsOffMinute(val value: Int) : DeviceLightQuickSetupPreferenceChange
    data class MeasuredPpfd(val value: Int?) : DeviceLightQuickSetupPreferenceChange
}

internal data class DeviceLightQuickSetupUiState(
    val deviceUid: String = "",
    val step: DeviceLightQuickSetupStep = DeviceLightQuickSetupStep.TANK_DATA,
    val tank: DeviceLightQuickSetupTank? = null,
    val managedPlanSnapshot: DeviceLightManagedPlanSnapshot? = null,
    val plantDemand: DeviceLightPlantDemand = DeviceLightPlantDemand.MEDIUM,
    val plantDensity: DeviceLightPlantDensity = DeviceLightPlantDensity.MEDIUM,
    val waterDepthCm: Int = QUICK_SETUP_DEFAULT_WATER_DEPTH_CM,
    val fixtureHeightCm: Int? = null,
    val ambientLevel: DeviceLightAmbientLevel = DeviceLightAmbientLevel.LOW,
    val co2Ready: Boolean = false,
    val activeSoil: Boolean = false,
    val preferredLightsOffMinute: Int = QUICK_SETUP_DEFAULT_LIGHTS_OFF_MINUTE,
    val measuredFullProfilePpfd: Int? = null,
    val plan: DeviceLightQuickSetupPlan? = null,
    val todayEpochDay: Long = 0L,
    val initialLoading: Boolean = false,
    val applying: Boolean = false,
    val contentEnabled: Boolean = false
) {
    val tankDay: Long?
        get() = tank?.setupDateEpochDay?.let { setup ->
            (todayEpochDay - setup + 1L).coerceAtLeast(1L)
        }

    val canContinueTankData: Boolean
        get() = contentEnabled && fixtureHeightCm != null && !applying

    val canCalculate: Boolean
        get() = canContinueTankData &&
            preferredLightsOffMinute >= QUICK_SETUP_LIGHTS_OFF_MINUTE_MIN

    val canApply: Boolean
        get() = contentEnabled && plan != null && managedPlanSnapshot != null && !applying
}

internal data class DeviceLightQuickSetupActions(
    val onPlantDemandChanged: (DeviceLightPlantDemand) -> Unit,
    val onPlantDensityChanged: (DeviceLightPlantDensity) -> Unit,
    val onWaterDepthChanged: (Int) -> Unit,
    val onFixtureHeightChanged: (Int) -> Unit,
    val onAmbientLevelChanged: (DeviceLightAmbientLevel) -> Unit,
    val onCo2ReadyChanged: (Boolean) -> Unit,
    val onActiveSoilChanged: (Boolean) -> Unit,
    val onLightsOffMinuteChanged: (Int) -> Unit,
    val onMeasuredPpfdChanged: (Int?) -> Unit,
    val onContinue: () -> Unit,
    val onBackStep: () -> Unit,
    val onCalculate: () -> Unit,
    val onApply: () -> Unit
)
