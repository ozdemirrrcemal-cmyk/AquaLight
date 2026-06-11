package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model

data class CloudSimulationSettings(
    val enabled: Boolean = false,
    val coveragePercent: Int = 25,
    val frequency: CloudFrequency = CloudFrequency.NORMAL
)