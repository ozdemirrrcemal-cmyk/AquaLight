package com.aqua.aqualight.ui.tabs.devices.detail.cooling.automatic

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
    successful: Boolean
): DeviceCoolingAutomaticSettingsUiState = if (successful) {
    copy(
        persistedStartTemperatureC = request.startTemperatureC,
        persistedMaximumSpeedTemperatureC = request.maximumSpeedTemperatureC,
        draftStartTemperatureC = request.startTemperatureC,
        draftMaximumSpeedTemperatureC = request.maximumSpeedTemperatureC,
        persistedSilentModeEnabled = request.silentModeEnabled ?: persistedSilentModeEnabled,
        draftSilentModeEnabled = request.silentModeEnabled ?: draftSilentModeEnabled,
        saveState = DeviceCoolingAutomaticSaveState.SAVED
    )
} else {
    copy(saveState = DeviceCoolingAutomaticSaveState.ERROR)
}
