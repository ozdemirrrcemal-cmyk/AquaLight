package com.aqua.aqualight.data.devices.light.math

import kotlin.math.roundToInt

/**
 * Central electrical power math for light runtime surfaces.
 *
 * The firmware exposes per-channel max watt configuration and current PWM
 * percent. The UI must not calculate watt as outputPercent * fixtureMaxWatt,
 * because RGBW channels can have different electrical limits.
 */
object LightPowerMath {

    fun calculateCurrentWatt(
        channels: Iterable<LightChannelPowerState>
    ): Double? {
        val wattValues = channels.mapNotNull { channel ->
            val maxWatt = channel.maxWatt?.takeIf { it > 0.0 }
                ?: return@mapNotNull null
            val percent = channel.currentPercent?.coerceIn(
                LightOutputMath.MIN_PERCENT,
                LightOutputMath.MAX_PERCENT
            ) ?: return@mapNotNull null

            maxWatt * percent / 100.0
        }

        return wattValues.takeIf { it.isNotEmpty() }?.sum()
    }

    fun calculateMaxWatt(
        configuredMaxWatt: Double?,
        channelMaxWatts: Iterable<Double?>
    ): Double? {
        val configured = configuredMaxWatt?.takeIf { it > 0.0 }
        if (configured != null) {
            return configured
        }

        val channelTotal = channelMaxWatts.mapNotNull { watt ->
            watt?.takeIf { it > 0.0 }
        }.sum()

        return channelTotal.takeIf { it > 0.0 }
    }

    fun powerLoadPercent(
        currentWatt: Double?,
        maxWatt: Double?
    ): Int? {
        val current = currentWatt?.takeIf { it >= 0.0 } ?: return null
        val max = maxWatt?.takeIf { it > 0.0 } ?: return null

        return ((current / max) * 100.0)
            .roundToInt()
            .coerceIn(
                LightOutputMath.MIN_PERCENT,
                LightOutputMath.MAX_PERCENT
            )
    }
}

data class LightChannelPowerState(
    val maxWatt: Double?,
    val currentPercent: Int?
)
