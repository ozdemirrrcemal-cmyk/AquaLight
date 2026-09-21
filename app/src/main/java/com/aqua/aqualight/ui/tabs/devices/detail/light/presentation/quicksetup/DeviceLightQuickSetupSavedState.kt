package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.quicksetup

import androidx.lifecycle.SavedStateHandle

/** Process-recreation state for the user-authored Quick Setup draft only. */
internal class DeviceLightQuickSetupSavedState(
    private val handle: SavedStateHandle
) {
    fun bindDevice(deviceUid: String): DeviceLightQuickSetupUiState {
        val restoredDeviceUid = handle.get<String>(KEY_DEVICE_UID)
        if (restoredDeviceUid != null && restoredDeviceUid != deviceUid) clearDraft()
        handle[KEY_DEVICE_UID] = deviceUid
        return restoreDraft().copy(deviceUid = deviceUid)
    }

    fun saveWaterHeight(value: String) {
        handle[KEY_WATER_HEIGHT] = value
    }

    fun saveFixtureHeight(value: String) {
        handle[KEY_FIXTURE_HEIGHT] = value
    }

    fun saveFirstLightMinute(value: Int) {
        handle[KEY_FIRST_LIGHT_MINUTE] = value
    }

    fun saveCo2Precharged(value: Boolean) {
        handle[KEY_CO2_PRECHARGED] = value
    }

    fun restoredStage(co2Present: Boolean): DeviceLightQuickSetupStage {
        val restored = handle.get<String>(KEY_STAGE)
            ?.let { stored -> runCatching { DeviceLightQuickSetupStage.valueOf(stored) }.getOrNull() }
            ?: DeviceLightQuickSetupStage.PROFILE
        return when (restored) {
            DeviceLightQuickSetupStage.PROFILE,
            DeviceLightQuickSetupStage.WATER_HEIGHT,
            DeviceLightQuickSetupStage.FIXTURE_HEIGHT,
            DeviceLightQuickSetupStage.LIGHT_TIME,
            DeviceLightQuickSetupStage.REVIEW -> restored
            DeviceLightQuickSetupStage.CO2_CONFIRMATION -> if (co2Present) {
                restored
            } else {
                DeviceLightQuickSetupStage.LIGHT_TIME
            }
            DeviceLightQuickSetupStage.CALCULATING,
            DeviceLightQuickSetupStage.APPLYING,
            DeviceLightQuickSetupStage.LIVE -> DeviceLightQuickSetupStage.PROFILE
        }
    }

    fun persistStage(stage: DeviceLightQuickSetupStage) {
        if (stage in TRANSIENT_STAGES) return
        handle[KEY_STAGE] = stage.name
    }

    private fun restoreDraft(): DeviceLightQuickSetupUiState {
        val defaults = DeviceLightQuickSetupUiState()
        return defaults.copy(
            waterHeightText = handle.get<String>(KEY_WATER_HEIGHT).orEmpty(),
            fixtureHeightText = handle.get<String>(KEY_FIXTURE_HEIGHT).orEmpty(),
            firstLightOnMinuteOfDay =
                handle.get<Int>(KEY_FIRST_LIGHT_MINUTE) ?: defaults.firstLightOnMinuteOfDay,
            co2Precharged = handle.get<Boolean>(KEY_CO2_PRECHARGED) ?: false
        )
    }

    private fun clearDraft() {
        handle.remove<String>(KEY_WATER_HEIGHT)
        handle.remove<String>(KEY_FIXTURE_HEIGHT)
        handle.remove<Int>(KEY_FIRST_LIGHT_MINUTE)
        handle.remove<Boolean>(KEY_CO2_PRECHARGED)
        handle.remove<String>(KEY_STAGE)
    }

    private companion object {
        const val KEY_DEVICE_UID = "lightQuickSetup.deviceUid"
        const val KEY_WATER_HEIGHT = "lightQuickSetup.waterHeight"
        const val KEY_FIXTURE_HEIGHT = "lightQuickSetup.fixtureHeight"
        const val KEY_FIRST_LIGHT_MINUTE = "lightQuickSetup.firstLightMinute"
        const val KEY_CO2_PRECHARGED = "lightQuickSetup.co2Precharged"
        const val KEY_STAGE = "lightQuickSetup.stage"
        val TRANSIENT_STAGES = setOf(
            DeviceLightQuickSetupStage.CALCULATING,
            DeviceLightQuickSetupStage.APPLYING,
            DeviceLightQuickSetupStage.LIVE
        )
    }
}
