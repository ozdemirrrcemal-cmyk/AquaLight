package com.aqua.aqualight.ui.tabs.devices.detail.cooling.automatic

/** Complete, validated payload waiting to cross the automatic-settings write boundary. */
internal data class PendingAutomaticSettingsSave(
    val deviceUid: String,
    val startTemperatureC: Double,
    val maximumSpeedTemperatureC: Double,
    val silentModeEnabled: Boolean?
)
