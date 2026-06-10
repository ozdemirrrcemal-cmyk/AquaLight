package com.aqua.aqualight.data.devices.light.runtime

data class LightManualRuntimeState(
    val deviceId: Long,
    val mode: LightControlMode = LightControlMode.AUTO,
    val activeSceneName: String? = null,
    val red: Int = 0,
    val green: Int = 0,
    val blue: Int = 0,
    val white: Int = 0,
    val isPowerOn: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
) {

    val isManualMode: Boolean
        get() = mode == LightControlMode.MANUAL ||
            mode == LightControlMode.MANUAL_SCENE

    val isManualScene: Boolean
        get() = mode == LightControlMode.MANUAL_SCENE

    val masterOutputPercent: Int
        get() =
            LightOutputMath.outputPercent(
                red = red,
                green = green,
                blue = blue,
                white = white
            )

    val previewRed: Int
        get() = percentToRgb(red)

    val previewGreen: Int
        get() = percentToRgb(green)

    val previewBlue: Int
        get() = percentToRgb(blue)

    private fun percentToRgb(
        value: Int
    ): Int {
        return ((value.coerceIn(0, 100) / 100f) * 255f).toInt()
    }

    companion object {
        fun auto(
            deviceId: Long
        ): LightManualRuntimeState {
            return LightManualRuntimeState(
                deviceId = deviceId,
                mode = LightControlMode.AUTO,
                activeSceneName = null,
                red = 0,
                green = 0,
                blue = 0,
                white = 0,
                isPowerOn = false
            )
        }
    }
}