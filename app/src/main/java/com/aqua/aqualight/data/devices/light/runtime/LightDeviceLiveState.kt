package com.aqua.aqualight.data.devices.light.runtime

import kotlin.math.roundToInt

data class LightDeviceLiveState(
    val deviceId: Long,
    val isRefreshing: Boolean = false,
    val deviceTime: LightDeviceTimeState? = null,
    val channels: List<LightDeviceLiveChannelState> = emptyList(),
    val lastUpdatedMillis: Long = 0L,
    val errorMessage: String? = null
) {

    val deviceTimeText: String
        get() = deviceTime?.timeText ?: "--:--"

    val hasDeviceTime: Boolean
        get() = deviceTime != null

    val actualOutputPercent: Int
        get() = channels
            .mapNotNull { channel ->
                channel.valuePercent
            }
            .maxOrNull() ?: 0

    val actualPowerWatts: Double?
        get() {
            val values = channels.mapNotNull { channel ->
                channel.actualWatts
            }

            if (values.isEmpty()) {
                return null
            }

            return values.sum()
        }

    val actualPowerText: String
        get() {
            val watts = actualPowerWatts ?: return "-- W"

            return "${watts.roundToOneDecimal()}W"
        }

    val hasLiveChannels: Boolean
        get() = channels.isNotEmpty()

    fun channelFor(
        semantic: LightChannelSemantic
    ): LightDeviceLiveChannelState? {
        return channels.firstOrNull { channel ->
            channel.semantic == semantic
        }
    }

    private fun Double.roundToOneDecimal(): Double {
        return (this * 10.0).roundToInt() / 10.0
    }

    companion object {

        fun initial(
            deviceId: Long
        ): LightDeviceLiveState {
            return LightDeviceLiveState(
                deviceId = deviceId,
                isRefreshing = false,
                deviceTime = null,
                channels = emptyList(),
                lastUpdatedMillis = 0L,
                errorMessage = null
            )
        }
    }
}