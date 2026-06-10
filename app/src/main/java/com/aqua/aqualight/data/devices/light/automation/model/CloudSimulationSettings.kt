package com.aqua.aqualight.data.devices.light.automation.model

data class CloudSimulationSettings(
    val enabled: Boolean = false,
    val coveragePercent: Int = 15,
    val frequency: CloudFrequency = CloudFrequency.NORMAL
)
