package com.aqua.aqualight.ui.tabs.devices.detail.cooling.automatic

import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAutomaticCommandResult

internal fun DeviceCoolingAutomaticSettingsUiState.pendingSave(
    boundDeviceUid: String
): PendingAutomaticSettingsSave? = if (!canSave) {
    null
} else {
    boundDeviceUid.takeIf(String::isNotBlank)?.let { deviceUid ->
        val start = draftStartTemperatureC
        val maximum = draftMaximumSpeedTemperatureC
        if (start == null || maximum == null) {
            null
        } else {
            PendingAutomaticSettingsSave(
                deviceUid = deviceUid,
                startTemperatureC = start,
                maximumSpeedTemperatureC = maximum,
                silentModeEnabled = persistedSilentModeEnabled?.let {
                    draftSilentModeEnabled
                }
            )
        }
    }
}

internal fun DeviceCoolingAutomaticSettingsUiState.afterSave(
    request: PendingAutomaticSettingsSave,
    result: DeviceCoolingAutomaticCommandResult
): DeviceCoolingAutomaticSettingsUiState = when (result) {
    DeviceCoolingAutomaticCommandResult.Success -> copy(
        persistedStartTemperatureC = request.startTemperatureC,
        persistedMaximumSpeedTemperatureC = request.maximumSpeedTemperatureC,
        draftStartTemperatureC = request.startTemperatureC,
        draftMaximumSpeedTemperatureC = request.maximumSpeedTemperatureC,
        persistedSilentModeEnabled = request.silentModeEnabled ?: persistedSilentModeEnabled,
        draftSilentModeEnabled = request.silentModeEnabled ?: draftSilentModeEnabled,
        saveState = DeviceCoolingAutomaticSaveState.SAVED,
        saveFailure = null
    )
    is DeviceCoolingAutomaticCommandResult.Failed -> copy(
        saveState = DeviceCoolingAutomaticSaveState.ERROR,
        saveFailure = result.failure
    )
}
