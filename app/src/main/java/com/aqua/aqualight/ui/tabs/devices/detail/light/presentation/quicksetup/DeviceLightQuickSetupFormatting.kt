package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.quicksetup

import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumPlantLightDemand
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupRecommendation
import java.time.LocalDate

internal fun DeviceLightQuickSetupRecommendation.currentPhaseIndex(): Int {
    val today = LocalDate.now().toEpochDay().toInt()
    return phases.indexOfLast { phase -> phase.validFromEpochDay <= today }.coerceAtLeast(0)
}

internal fun Int.toClockText(): String = "%02d:%02d".format(
    this / QUICK_SETUP_MINUTES_PER_HOUR,
    this % QUICK_SETUP_MINUTES_PER_HOUR
)

internal fun AquariumPlantLightDemand.demandResource(): Int = when (this) {
    AquariumPlantLightDemand.LOW -> R.string.device_light_quick_setup_demand_low
    AquariumPlantLightDemand.MEDIUM -> R.string.device_light_quick_setup_demand_medium
    AquariumPlantLightDemand.HIGH -> R.string.device_light_quick_setup_demand_high
}

internal const val QUICK_SETUP_MINUTES_PER_HOUR = 60
internal const val QUICK_SETUP_CO2_PRECHARGE_MINUTES = 120
internal const val QUICK_SETUP_MINUTES_PER_DAY = 1_440
