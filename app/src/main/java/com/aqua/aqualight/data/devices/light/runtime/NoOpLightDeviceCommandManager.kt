package com.aqua.aqualight.data.devices.light.runtime

class NoOpLightDeviceCommandManager : LightDeviceCommandManager {

    override suspend fun applyManualScene(
        deviceId: Long,
        sceneName: String,
        output: LightRgbwOutput
    ): LightCommandResult {
        return LightCommandResult.success()
    }

    override suspend fun updateManualOutput(
        deviceId: Long,
        output: LightRgbwOutput
    ): LightCommandResult {
        return LightCommandResult.success()
    }

    override suspend fun setManualPower(
        deviceId: Long,
        isPowerOn: Boolean
    ): LightCommandResult {
        return LightCommandResult.success()
    }

    override suspend fun resumeAuto(
        deviceId: Long
    ): LightCommandResult {
        return LightCommandResult.success()
    }
}