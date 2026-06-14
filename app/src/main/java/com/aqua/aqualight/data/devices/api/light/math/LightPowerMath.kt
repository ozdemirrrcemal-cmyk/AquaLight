package com.aqua.aqualight.data.devices.light.math

import kotlin.math.roundToInt

/**
 * Central electrical power math for light runtime surfaces.
 *
 * The firmware exposes every LED channel's configured max watt and current PWM
 * percent. The app must calculate power from those per-channel values. It must
 * not use catalog watt, and it must not use outputPercent * fixtureMaxWatt.
 */
object LightPowerMath {

    fun calculateCurrentWatt(
        channels: Iterable<LightChannelPowerState>
    ): Double? {
        var hasAnyKnownChannel = false
        var totalWatt = 0.0

        channels.forEach { channel ->
            val percent = channel.currentPercent
                ?.coerceIn(
                    LightOutputMath.MIN_PERCENT,
                    LightOutputMath.MAX_PERCENT
                )
                ?: return@forEach

            val maxWatt = channel.maxWatt?.takeIf { it > 0.0 }

            if (percent <= LightOutputMath.MIN_PERCENT) {
                if (maxWatt != null) {
                    hasAnyKnownChannel = true
                }
                return@forEach
            }

            if (maxWatt == null) {
                return null
            }

            hasAnyKnownChannel = true
            totalWatt += maxWatt * percent / 100.0
        }

        return totalWatt.takeIf { hasAnyKnownChannel }
    }

    /**
     * Runtime max watt is the sum of the actual LED channel max watt values the
     * device reports. The group WMax field is only a fallback for legacy payloads
     * that do not expose channel watt values.
     */
    fun calculateMaxWatt(
        configuredMaxWatt: Double?,
        channelMaxWatts: Iterable<Double?>
    ): Double? {
        val channelTotal = channelMaxWatts.mapNotNull { watt ->
            watt?.takeIf { it > 0.0 }
        }.sum()

        if (channelTotal > 0.0) {
            return channelTotal
        }

        return configuredMaxWatt?.takeIf { it > 0.0 }
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
