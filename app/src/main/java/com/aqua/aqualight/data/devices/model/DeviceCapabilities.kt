package com.aqua.aqualight.data.devices.model

data class DeviceCapabilities(
    val light: Boolean = false,
    val manualLight: Boolean = false,
    val lightProgram: Boolean = false,
    val lightPresets: Boolean = false,
    val lightSimulation: Boolean = false,
    val fan: Boolean = false,
    val cooling: Boolean = false,
    val temperature: Boolean = false,
    val standaloneTimer: Boolean = false,
    val dosing: Boolean = false,
    val timeSync: Boolean = false,
    val ota: Boolean = false
)
