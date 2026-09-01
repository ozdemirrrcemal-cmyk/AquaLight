package com.aqua.aqualight.ui.tabs.devices.detail.cooling.automatic

import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAutomaticTemperaturePolicy
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.common.CoolingMutationState

internal fun DeviceCoolingAutomaticSettingsUiState.withUpdatedStartTemperature(
    value: Double
): DeviceCoolingAutomaticSettingsUiState {
    val temperaturePolicy = editorPolicy ?: return this
    val maximum = editorMaximumSpeedTemperatureC ?: return this
    return if (value.isValidStart(temperaturePolicy, maximum)) {
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
    val temperaturePolicy = editorPolicy ?: return this
    val start = editorStartTemperatureC ?: return this
    return if (value.isValidMaximum(temperaturePolicy, start)) {
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
