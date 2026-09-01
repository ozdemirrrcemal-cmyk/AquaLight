package com.aqua.aqualight.ui.tabs.devices.detail.cooling.automatic

import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAutomaticTemperaturePolicy
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.common.CoolingMutationState

internal fun DeviceCoolingAutomaticSettingsUiState.withUpdatedStartTemperature(
    value: Double
): DeviceCoolingAutomaticSettingsUiState {
    val temperaturePolicy = editorPolicy
    val maximum = editorMaximumSpeedTemperatureC
    val valid = temperaturePolicy != null &&
        maximum != null &&
        value.isValidStart(temperaturePolicy, maximum)
    return if (valid) {
        copy(
            draftStartTemperatureC = value,
            mutationState = CoolingMutationState.Idle
        )
    } else {
        this
    }
}

internal fun DeviceCoolingAutomaticSettingsUiState.withUpdatedMaximumTemperature(
    value: Double
): DeviceCoolingAutomaticSettingsUiState {
    val temperaturePolicy = editorPolicy
    val start = editorStartTemperatureC
    val valid = temperaturePolicy != null &&
        start != null &&
        value.isValidMaximum(temperaturePolicy, start)
    return if (valid) {
        copy(
            draftMaximumSpeedTemperatureC = value,
            mutationState = CoolingMutationState.Idle
        )
    } else {
        this
    }
}

internal fun DeviceCoolingAutomaticSettingsUiState.withUpdatedSilentMode(
    enabled: Boolean
): DeviceCoolingAutomaticSettingsUiState = if (
    !silentModeEditable || draftSilentModeEnabled == enabled
) {
    this
} else {
    copy(
        draftSilentModeEnabled = enabled,
        mutationState = CoolingMutationState.Idle
    )
}

private fun Double.isValidStart(
    policy: DeviceCoolingAutomaticTemperaturePolicy,
    maximum: Double
): Boolean = isFinite() &&
    this in policy.startMinimumC..policy.startMaximumC &&
    maximum - this >= policy.minimumGapC - TEMPERATURE_EPSILON

private fun Double.isValidMaximum(
    policy: DeviceCoolingAutomaticTemperaturePolicy,
    start: Double
): Boolean = isFinite() &&
    this in policy.maximumSpeedMinimumC..policy.maximumSpeedMaximumC &&
    this - start >= policy.minimumGapC - TEMPERATURE_EPSILON

private const val TEMPERATURE_EPSILON = 0.000_001
