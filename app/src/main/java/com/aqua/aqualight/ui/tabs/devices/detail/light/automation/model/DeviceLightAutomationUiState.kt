package com.aqua.aqualight.ui.tabs.devices.detail.light.automation.model

import com.aqua.aqualight.data.devices.light.automation.model.CloudSimulationSettings
import com.aqua.aqualight.data.devices.light.automation.model.MoonlightSettings

data class DeviceLightAutomationUiState(
    val moonlight: MoonlightSettings = MoonlightSettings(),
    val cloudSimulation: CloudSimulationSettings = CloudSimulationSettings(),
    val pendingDeviceSync: Boolean = false,
    val updatedAt: Long = 0L
) {
    val moonlightStatusText: String get() = if (moonlight.enabled) "ON" else "OFF"
    val moonlightSummaryText: String get() = if (moonlight.enabled) "${moonlight.intensityPercent}% • Until ${moonlight.endTime.label}" else "Independent night output automation"
    val cloudStatusText: String get() = if (cloudSimulation.enabled) "ON" else "OFF"
    val cloudSummaryText: String get() = if (cloudSimulation.enabled) "Coverage ${cloudSimulation.coveragePercent}%" else "Prepared for firmware runtime support"
}
