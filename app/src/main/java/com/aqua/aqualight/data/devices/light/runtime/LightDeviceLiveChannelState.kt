package com.aqua.aqualight.data.devices.light.runtime

import kotlin.math.roundToInt

data class LightDeviceLiveChannelState(
    val semantic: LightChannelSemantic,
    val pwmIndex: String,
    val lightIndex: String,
    val gpioPwm: String,
    val name: String,
    val color: Long,
    val regime: String,
    val vNow: Double?,
    val maxWatts: Double?
) {

    val valuePercent: Int?
        get() = vNow
            ?.coerceIn(0.0, 100.0)
            ?.roundToInt()
            ?.coerceIn(0, 100)

    val actualWatts: Double?
        get() {
            val safePercent = valuePercent
                ?: return null

            val safeMaxWatts = maxWatts
                ?.takeIf { value ->
                    value > 0.0
                }
                ?: return null

            return safeMaxWatts * (safePercent / 100.0)
        }
}