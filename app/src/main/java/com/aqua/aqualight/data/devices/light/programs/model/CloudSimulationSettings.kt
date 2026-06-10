package com.aqua.aqualight.data.devices.light.programs.model

data class CloudSimulationSettings(
    val enabled: Boolean = false,
    val coveragePercent: Int = 25,
    val frequency: CloudFrequency = CloudFrequency.NORMAL
)