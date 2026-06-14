package com.aqua.aqualight.ui.tabs.devices.detail.light.automation.model

import com.aqua.aqualight.ui.tabs.devices.detail.light.core.automation.model.CloudFrequency
import com.aqua.aqualight.ui.tabs.devices.detail.light.core.automation.model.CloudSimulationSettings
import com.aqua.aqualight.ui.tabs.devices.detail.light.core.automation.model.MoonlightSettings

/**
 * UI state for independent light automations.
 *
 * The screen deliberately exposes concise summary text only. Detailed controls stay in
 * bottom sheets so this page remains a clean automation hub.
 */
data class DeviceLightAutomationUiState(
    val moonlight: MoonlightSettings = MoonlightSettings(),
    val cloudSimulation: CloudSimulationSettings = CloudSimulationSettings(),
    val pendingDeviceSync: Boolean = false,
    val updatedAt: Long = 0L
) {
    val moonlightStatusText: String get() = if (moonlight.enabled) "ON" else "OFF"

    val moonlightSummaryText: String
        get() = if (moonlight.enabled) {
            "${moonlight.intensityPercent}% intensity · Ends ${moonlight.endTime.label}"
        } else {
            "Tap to configure night output"
        }

    val cloudStatusText: String get() = if (cloudSimulation.enabled) "ON" else "OFF"

    val cloudSummaryText: String
        get() = if (cloudSimulation.enabled) {
            "${cloudSimulation.coveragePercent}% coverage · ${cloudSimulation.frequency.label}"
        } else {
            "Tap to configure cloud dimming"
        }
}

private val CloudFrequency.label: String
    get() = when (this) {
        CloudFrequency.RARE -> "Rare"
        CloudFrequency.NORMAL -> "Normal"
        CloudFrequency.FREQUENT -> "Frequent"
    }
