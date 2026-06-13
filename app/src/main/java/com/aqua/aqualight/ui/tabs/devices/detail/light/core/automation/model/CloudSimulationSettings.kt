package com.aqua.aqualight.ui.tabs.devices.detail.light.core.automation.model

data class CloudSimulationSettings(
    val enabled: Boolean = false,
    val coveragePercent: Int = 15,
    val frequency: CloudFrequency = CloudFrequency.NORMAL
)
