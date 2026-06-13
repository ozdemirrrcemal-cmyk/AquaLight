package com.aqua.aqualight.ui.tabs.devices.detail.light.core.automation.model

data class LightAutomationSettings(
    val deviceId: Long,
    val ownerUid: String = "",
    val moonlight: MoonlightSettings = MoonlightSettings(),
    val cloudSimulation: CloudSimulationSettings = CloudSimulationSettings(),
    val updatedAt: Long = 0L,
    val pendingDeviceSync: Boolean = false
) {
    companion object {
        fun default(
            deviceId: Long,
            ownerUid: String = ""
        ): LightAutomationSettings {
            return LightAutomationSettings(
                deviceId = deviceId.coerceAtLeast(0L),
                ownerUid = ownerUid,
                updatedAt = System.currentTimeMillis()
            )
        }
    }
}
