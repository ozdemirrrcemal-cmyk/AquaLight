package com.aqua.aqualight.data.devices.light.runtime

class LightRuntimeRepository(
    private val commandManager: LightDeviceCommandManager = NoOpLightDeviceCommandManager()
) {

    suspend fun applyManualScene(
        deviceId: Long,
        sceneName: String,
        red: Int,
        green: Int,
        blue: Int,
        white: Int
    ): LightCommandResult {
        val output = LightRgbwOutput(
            red = red,
            green = green,
            blue = blue,
            white = white
        ).normalized()

        val result = commandManager.applyManualScene(
            deviceId = deviceId,
            sceneName = sceneName,
            output = output
        )

        if (result.isSuccess) {
            LightManualRuntimeStore.applyManualScene(
                deviceId = deviceId,
                sceneName = sceneName,
                red = output.red,
                green = output.green,
                blue = output.blue,
                white = output.white
            )
        }

        return result
    }

    suspend fun updateManualOutput(
        deviceId: Long,
        red: Int,
        green: Int,
        blue: Int,
        white: Int
    ): LightCommandResult {
        val output = LightRgbwOutput(
            red = red,
            green = green,
            blue = blue,
            white = white
        ).normalized()

        val result = commandManager.updateManualOutput(
            deviceId = deviceId,
            output = output
        )

        if (result.isSuccess) {
            LightManualRuntimeStore.updateManualOutput(
                deviceId = deviceId,
                red = output.red,
                green = output.green,
                blue = output.blue,
                white = output.white
            )
        }

        return result
    }

    suspend fun setManualPower(
        deviceId: Long,
        isPowerOn: Boolean
    ): LightCommandResult {
        val result = commandManager.setManualPower(
            deviceId = deviceId,
            isPowerOn = isPowerOn
        )

        if (result.isSuccess) {
            LightManualRuntimeStore.setPowerOn(
                deviceId = deviceId,
                isPowerOn = isPowerOn
            )
        }

        return result
    }

    suspend fun resumeAuto(
        deviceId: Long
    ): LightCommandResult {
        val result = commandManager.resumeAuto(
            deviceId = deviceId
        )

        if (result.isSuccess) {
            LightManualRuntimeStore.resumeAuto(
                deviceId = deviceId
            )
        }

        return result
    }
	
	fun observeManualRuntime(
    deviceId: Long
) = LightManualRuntimeStore.observe(deviceId)

fun currentManualRuntime(
    deviceId: Long
) = LightManualRuntimeStore.current(deviceId)
}