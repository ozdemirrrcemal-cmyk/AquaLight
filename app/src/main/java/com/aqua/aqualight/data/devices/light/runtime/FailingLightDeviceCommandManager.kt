package com.aqua.aqualight.data.devices.light.runtime

/**
 * Production-safe fallback for places that only need read-only runtime state.
 * Any real device command must explicitly inject Esp32LightDeviceCommandManager.
 */
class FailingLightDeviceCommandManager : LightDeviceCommandManager {

    override suspend fun applyManualScene(
        deviceId: Long,
        sceneName: String,
        output: LightRgbwOutput
    ): LightCommandResult = unsupported()

    override suspend fun updateManualOutput(
        deviceId: Long,
        output: LightRgbwOutput
    ): LightCommandResult = unsupported()

    override suspend fun updateManualChannel(
        deviceId: Long,
        semantic: LightChannelSemantic,
        valuePercent: Int
    ): LightCommandResult = unsupported()

    override suspend fun setManualPower(
        deviceId: Long,
        isPowerOn: Boolean
    ): LightCommandResult = unsupported()

    override suspend fun resumeAuto(
        deviceId: Long
    ): LightCommandResult = unsupported()

    private fun unsupported(): LightCommandResult {
        return LightCommandResult.failure(
            "Light command manager is not configured for this screen"
        )
    }
}
