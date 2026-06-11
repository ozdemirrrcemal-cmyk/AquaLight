package com.aqua.aqualight.data.devices.light.automation.model

data class LightAutomationSettings(
    val deviceId: Long,
    val moonlight: MoonlightSettings = MoonlightSettings(),
    val cloudSimulation: CloudSimulationSettings = CloudSimulationSettings(),
    val updatedAt: Long = 0L,
    val pendingDeviceSync: Boolean = false
) { companion object { fun default(deviceId: Long) = LightAutomationSettings(deviceId = deviceId.coerceAtLeast(0L), updatedAt = System.currentTimeMillis()) } }
