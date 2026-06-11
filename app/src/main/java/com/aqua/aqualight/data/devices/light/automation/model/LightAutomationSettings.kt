package com.aqua.aqualight.data.devices.light.automation.model

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
