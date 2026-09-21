package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.quicksetup

import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightPlanSnapshot
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightManagedPlanSnapshot
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupBlockReason
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupCo2Readiness
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupContext
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupInput
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupPlantProfile
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupRecommendation

internal enum class DeviceLightQuickSetupStage {
    PROFILE,
    WATER_HEIGHT,
    FIXTURE_HEIGHT,
    LIGHT_TIME,
    CO2_CONFIRMATION,
    CALCULATING,
    REVIEW,
    APPLYING,
    LIVE
}

internal data class DeviceLightQuickSetupUiState(
    val deviceUid: String = "",
    val stage: DeviceLightQuickSetupStage = DeviceLightQuickSetupStage.PROFILE,
    val loading: Boolean = true,
    val context: DeviceLightQuickSetupContext? = null,
    val plantProfile: DeviceLightQuickSetupPlantProfile? = null,
    val waterHeightText: String = "",
    val fixtureHeightText: String = "",
    val firstLightOnMinuteOfDay: Int = DEFAULT_FIRST_LIGHT_MINUTE,
    val co2Precharged: Boolean = false,
    val recommendation: DeviceLightQuickSetupRecommendation? = null,
    val managedPlan: DeviceLightManagedPlanSnapshot? = null,
    val livePlan: DeviceLightPlanSnapshot? = null,
    val blockReason: DeviceLightQuickSetupBlockReason? = null,
    val reviewRequiredAfterStale: Boolean = false
) {
    val co2Present: Boolean
        get() = context?.co2Present == true

    val stepNumber: Int
        get() = stage.ordinal + 1

    val totalSteps: Int
        get() = DeviceLightQuickSetupStage.entries.size
}

internal sealed interface DeviceLightQuickSetupAction {
    data class WaterHeightChanged(val value: String) : DeviceLightQuickSetupAction
    data class FixtureHeightChanged(val value: String) : DeviceLightQuickSetupAction
    data class FirstLightTimeChanged(val minuteOfDay: Int) : DeviceLightQuickSetupAction
    data class Co2PrechargedChanged(val enabled: Boolean) : DeviceLightQuickSetupAction
    data object Next : DeviceLightQuickSetupAction
    data object Back : DeviceLightQuickSetupAction
    data object Apply : DeviceLightQuickSetupAction
    data object Retry : DeviceLightQuickSetupAction
    data object Edit : DeviceLightQuickSetupAction
    data object DisablePlan : DeviceLightQuickSetupAction
}

internal fun DeviceLightQuickSetupUiState.toInputOrNull(): DeviceLightQuickSetupInput? {
    val context = context ?: return null
    val water = waterHeightText.toIntOrNull() ?: return null
    val fixture = fixtureHeightText.toIntOrNull() ?: return null
    val co2 = when {
        !context.co2Present -> DeviceLightQuickSetupCo2Readiness.NOT_PRESENT
        co2Precharged -> DeviceLightQuickSetupCo2Readiness.PRESENT_PRECHARGED
        else -> DeviceLightQuickSetupCo2Readiness.PRESENT_NOT_PRECHARGED
    }
    return DeviceLightQuickSetupInput(
        waterHeightCm = water,
        fixtureHeightAboveWaterCm = fixture,
        firstLightOnMinuteOfDay = firstLightOnMinuteOfDay,
        co2Readiness = co2
    )
}

internal fun sanitizeMeasurementInput(value: String): String =
    value.filter(Char::isDigit).take(MAX_MEASUREMENT_DIGITS)

private const val DEFAULT_FIRST_LIGHT_MINUTE = 10 * 60
private const val MAX_MEASUREMENT_DIGITS = 3
