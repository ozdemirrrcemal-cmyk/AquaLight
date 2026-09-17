package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.root

import androidx.annotation.StringRes
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlMode
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightHeroSnapshot
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightOutputCondition

internal data class DeviceLightHeroPresentation(
    @StringRes val titleRes: Int,
    @StringRes val modeRes: Int,
    @StringRes val outputRes: Int,
    @StringRes val healthRes: Int,
    val healthTone: DeviceLightHeroHealthTone,
    val estimatedPowerWatts: Double?,
    val estimatedColorTemperatureKelvin: Int?
)

internal enum class DeviceLightHeroHealthTone {
    HEALTHY,
    ATTENTION,
    UNAVAILABLE
}

internal fun DeviceLightHeroSnapshot.toHeroPresentation() = DeviceLightHeroPresentation(
    titleRes = outputActive.toTitleRes(),
    modeRes = mode.toModeRes(),
    outputRes = outputCondition.toOutputRes(),
    healthRes = outputHealthy.toHealthRes(),
    healthTone = outputHealthy.toHealthTone(),
    estimatedPowerWatts = estimatedPowerWatts,
    estimatedColorTemperatureKelvin = estimatedColorTemperatureKelvin
)

@StringRes
private fun Boolean?.toTitleRes(): Int = when (this) {
    true -> R.string.device_light_hero_title_on
    false -> R.string.device_light_hero_title_off
    null -> R.string.device_light_hero_title_unavailable
}

@StringRes
private fun DeviceLightControlMode?.toModeRes(): Int = when (this) {
    DeviceLightControlMode.MANUAL -> R.string.device_light_hero_mode_manual
    DeviceLightControlMode.AUTOMATIC -> R.string.device_light_hero_mode_automatic
    DeviceLightControlMode.CUSTOM -> R.string.device_light_hero_mode_custom
    null -> R.string.device_light_hero_mode_unavailable
}

@StringRes
private fun DeviceLightOutputCondition?.toOutputRes(): Int = when (this) {
    DeviceLightOutputCondition.ACTIVE -> R.string.device_light_hero_output_steady
    DeviceLightOutputCondition.SCHEDULED_OFF ->
        R.string.device_light_hero_output_scheduled_off
    DeviceLightOutputCondition.ALL_CHANNELS_ZERO ->
        R.string.device_light_hero_output_channels_zero
    DeviceLightOutputCondition.CLOCK_UNAVAILABLE ->
        R.string.device_light_hero_output_clock_unavailable
    DeviceLightOutputCondition.THERMAL_PROTECTION ->
        R.string.device_light_hero_output_thermal_protection
    DeviceLightOutputCondition.POWER_LIMITED ->
        R.string.device_light_hero_output_power_limited
    DeviceLightOutputCondition.HARDWARE_FAULT ->
        R.string.device_light_hero_output_hardware_fault
    null -> R.string.device_light_hero_output_unavailable
}

@StringRes
private fun Boolean?.toHealthRes(): Int = when (this) {
    true -> R.string.device_light_hero_output_healthy
    false -> R.string.device_light_hero_output_attention
    null -> R.string.device_light_hero_output_health_unavailable
}

private fun Boolean?.toHealthTone(): DeviceLightHeroHealthTone = when (this) {
    true -> DeviceLightHeroHealthTone.HEALTHY
    false -> DeviceLightHeroHealthTone.ATTENTION
    null -> DeviceLightHeroHealthTone.UNAVAILABLE
}
