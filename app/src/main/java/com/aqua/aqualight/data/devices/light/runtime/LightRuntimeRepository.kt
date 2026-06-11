package com.aqua.aqualight.data.devices.light.runtime

class LightRuntimeRepository(
    private val commandManager: LightDeviceCommandManager = FailingLightDeviceCommandManager()
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
	
	suspend fun updateManualChannel(
    deviceId: Long,
    semantic: LightChannelSemantic,
    valuePercent: Int
): LightCommandResult {
    val safeValue = valuePercent.coerceIn(0, 100)

    val result = commandManager.updateManualChannel(
        deviceId = deviceId,
        semantic = semantic,
        valuePercent = safeValue
    )

    if (result.isSuccess) {
        val current = LightManualRuntimeStore.current(deviceId)

        val updatedRed = if (semantic == LightChannelSemantic.RED) {
            safeValue
        } else {
            current.red
        }

        val updatedGreen = if (semantic == LightChannelSemantic.GREEN) {
            safeValue
        } else {
            current.green
        }

        val updatedBlue = if (semantic == LightChannelSemantic.BLUE) {
            safeValue
        } else {
            current.blue
        }

        val updatedWhite = if (semantic == LightChannelSemantic.WHITE) {
            safeValue
        } else {
            current.white
        }

        LightManualRuntimeStore.updateManualOutput(
            deviceId = deviceId,
            red = updatedRed,
            green = updatedGreen,
            blue = updatedBlue,
            white = updatedWhite
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