package com.aqua.aqualight.data.devices.light.runtime

interface LightDeviceCommandManager {

    suspend fun applyManualScene(
        deviceId: Long,
        sceneName: String,
        output: LightRgbwOutput
    ): LightCommandResult

    suspend fun updateManualOutput(
        deviceId: Long,
        output: LightRgbwOutput
    ): LightCommandResult

    suspend fun updateManualChannel(
        deviceId: Long,
        semantic: LightChannelSemantic,
        valuePercent: Int
    ): LightCommandResult

    suspend fun setManualPower(
        deviceId: Long,
        isPowerOn: Boolean
    ): LightCommandResult

    suspend fun resumeAuto(
        deviceId: Long
    ): LightCommandResult
}