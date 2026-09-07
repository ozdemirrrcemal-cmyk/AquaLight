package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.automatic

import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAutomaticTemperatureValidation
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingMutationState

internal fun DeviceCoolingAutomaticSettingsUiState.withUpdatedStartTemperature(
    value: Double
): DeviceCoolingAutomaticSettingsUiState {
    val temperaturePolicy = editorPolicy
    val maximum = editorMaximumSpeedTemperatureC
    val valid = temperaturePolicy != null &&
        maximum != null &&
        DeviceCoolingAutomaticTemperatureValidation.isValidStartTemperature(
            value = value,
            policy = temperaturePolicy,
            maximumSpeedTemperatureC = maximum
        )
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
        DeviceCoolingAutomaticTemperatureValidation.isValidMaximumSpeedTemperature(
            value = value,
            policy = temperaturePolicy,
            startTemperatureC = start
        )
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
