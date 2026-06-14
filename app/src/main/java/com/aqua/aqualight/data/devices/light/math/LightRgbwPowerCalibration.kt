package com.aqua.aqualight.data.devices.light.math

/**
 * Per-RGBW max watt values reported by the device firmware.
 *
 * This is deliberately runtime data. It must come from the controller channels,
 * not from the product catalog, so different channel counts and watt layouts are
 * represented correctly.
 */
data class LightRgbwPowerCalibration(
    val redMaxWatt: Double? = null,
    val greenMaxWatt: Double? = null,
    val blueMaxWatt: Double? = null,
    val whiteMaxWatt: Double? = null
) {

    val maxWatt: Double?
        get() = LightPowerMath.calculateMaxWatt(
            configuredMaxWatt = null,
            channelMaxWatts = listOf(
                redMaxWatt,
                greenMaxWatt,
                blueMaxWatt,
                whiteMaxWatt
            )
        )

    fun currentWatt(
        redPercent: Int,
        greenPercent: Int,
        bluePercent: Int,
        whitePercent: Int
    ): Double? {
        return LightPowerMath.calculateCurrentWatt(
            channels = listOf(
                LightChannelPowerState(
                    maxWatt = redMaxWatt,
                    currentPercent = redPercent
                ),
                LightChannelPowerState(
                    maxWatt = greenMaxWatt,
                    currentPercent = greenPercent
                ),
                LightChannelPowerState(
                    maxWatt = blueMaxWatt,
                    currentPercent = bluePercent
                ),
                LightChannelPowerState(
                    maxWatt = whiteMaxWatt,
                    currentPercent = whitePercent
                )
            )
        )
    }

    fun powerLoadPercent(
        redPercent: Int,
        greenPercent: Int,
        bluePercent: Int,
        whitePercent: Int
    ): Int? {
        return LightPowerMath.powerLoadPercent(
            currentWatt = currentWatt(
                redPercent = redPercent,
                greenPercent = greenPercent,
                bluePercent = bluePercent,
                whitePercent = whitePercent
            ),
            maxWatt = maxWatt
        )
    }

    val hasChannelWattData: Boolean
        get() = maxWatt != null
}
