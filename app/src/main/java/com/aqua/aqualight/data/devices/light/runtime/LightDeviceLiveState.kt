package com.aqua.aqualight.data.devices.light.runtime

import kotlin.math.roundToInt

data class LightDeviceLiveState(
    val deviceId: Long,
    val isRefreshing: Boolean = false,
    val deviceTime: LightDeviceTimeState? = null,
    val deviceTimeUpdatedMillis: Long = 0L,
    val channels: List<LightDeviceLiveChannelState> = emptyList(),
    val thermalProtection: LightThermalProtectionState =
        LightThermalProtectionState(),
    val cooling: LightCoolingState =
        LightCoolingState(),
    val lastUpdatedMillis: Long = 0L,
    val errorMessage: String? = null
) {

    val deviceTimeText: String
        get() {
            return if (hasDeviceTime) {
                deviceTime?.timeText ?: "--:--"
            } else {
                "--:--"
            }
        }

    val hasDeviceTime: Boolean
        get() {
            if (deviceTime == null) {
                return false
            }

            if (deviceTimeUpdatedMillis <= 0L) {
                return false
            }

            val ageMillis =
                System.currentTimeMillis() - deviceTimeUpdatedMillis

            return ageMillis <= DEVICE_TIME_FRESHNESS_MS
        }

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

        private const val DEVICE_TIME_FRESHNESS_MS = 30_000L

        fun initial(
            deviceId: Long
        ): LightDeviceLiveState {
            return LightDeviceLiveState(
                deviceId = deviceId,
                isRefreshing = false,
                deviceTime = null,
                deviceTimeUpdatedMillis = 0L,
                channels = emptyList(),
                thermalProtection = LightThermalProtectionState(),
                cooling = LightCoolingState(),
                lastUpdatedMillis = 0L,
                errorMessage = null
            )
        }
    }
}