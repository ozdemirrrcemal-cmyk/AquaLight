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
    val liveDataUpdatedMillis: Long = 0L,
    val isLiveDataFresh: Boolean = false,
    val lastUpdatedMillis: Long = 0L,
    val errorMessage: String? = null,
    val dataSource: LightLiveDataSource = LightLiveDataSource.EMPTY
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

    val hasAuthoritativeDeviceTime: Boolean
        get() = hasDeviceTime && dataSource == LightLiveDataSource.LIVE

    val hasAuthoritativeContact: Boolean
        get() = hasFreshLiveData || hasAuthoritativeDeviceTime

    val hasDisplayChannels: Boolean
        get() = channels.isNotEmpty()

    val hasCachedDisplayData: Boolean
        get() = dataSource == LightLiveDataSource.CACHE && channels.isNotEmpty()

    val isShowingCachedData: Boolean
        get() = hasCachedDisplayData && !hasFreshLiveData

    val displayOutputPercent: Int
        get() {
            if (!hasDisplayChannels) {
                return 0
            }

            return channels
                .mapNotNull { channel ->
                    channel.valuePercent
                }
                .maxOrNull() ?: 0
        }

    val displayPowerWatts: Double?
        get() {
            if (!hasDisplayChannels) {
                return null
            }

            val values = channels.mapNotNull { channel ->
                channel.actualWatts
            }

            if (values.isEmpty()) {
                return null
            }

            return values.sum()
        }

    val displayPowerText: String
        get() {
            val watts = displayPowerWatts ?: return "-- W"

            return "${watts.roundToOneDecimal()}W"
        }

    val actualOutputPercent: Int
        get() {
            if (!hasFreshLiveData) {
                return 0
            }

            return channels
                .mapNotNull { channel ->
                    channel.valuePercent
                }
                .maxOrNull() ?: 0
        }

    val actualPowerWatts: Double?
        get() {
            if (!hasFreshLiveData) {
                return null
            }

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
        get() = hasFreshLiveData && channels.isNotEmpty()

    val hasManualOverrideTelemetry: Boolean
        get() = hasFreshLiveData && channels.any { channel ->
            channel.hasManualOverrideTelemetry
        }

    val hasManualOverride: Boolean
        get() = hasFreshLiveData && channels.any { channel ->
            channel.isManualOverrideActive
        }

    fun manualChannelValuePercent(
        semantic: LightChannelSemantic
    ): Int? {
        return channelFor(
            semantic = semantic
        )?.effectiveManualValuePercent
    }


    val hasFreshLiveData: Boolean
        get() {
            if (!isLiveDataFresh) {
                return false
            }

            if (liveDataUpdatedMillis <= 0L) {
                return false
            }

            val ageMillis =
                System.currentTimeMillis() - liveDataUpdatedMillis

            return ageMillis <= LIVE_DATA_FRESHNESS_MS
        }

    fun channelFor(
        semantic: LightChannelSemantic
    ): LightDeviceLiveChannelState? {
        if (!hasFreshLiveData) {
            return null
        }

        return displayChannelFor(
            semantic = semantic
        )
    }

    fun displayChannelFor(
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
        private const val LIVE_DATA_FRESHNESS_MS = 12_000L

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
                liveDataUpdatedMillis = 0L,
                isLiveDataFresh = false,
                lastUpdatedMillis = 0L,
                errorMessage = null,
                dataSource = LightLiveDataSource.EMPTY
            )
        }
    }
}