package com.aqua.aqualight.data.devices.light.runtime

/**
 * Central gate for every value that is allowed to be presented as real device output.
 *
 * A fresh telemetry packet is not enough. UI may present actual/live values only when
 * the device is confirmed online and the telemetry packet is still fresh. Scheduled
 * programs, local automation settings and manual runtime state are targets or modes;
 * they must never be promoted to actual output by themselves.
 */
object LightActualDataPolicy {

    fun hasActualData(
        isOnline: Boolean,
        liveState: LightDeviceLiveState?
    ): Boolean {
        return isOnline && liveState?.hasLiveChannels == true
    }

    fun actualOutputPercent(
        isOnline: Boolean,
        liveState: LightDeviceLiveState?
    ): Int {
        if (!hasActualData(isOnline, liveState)) {
            return 0
        }

        return liveState?.actualOutputPercent?.coerceIn(0, 100) ?: 0
    }

    fun actualOutputText(
        isOnline: Boolean,
        liveState: LightDeviceLiveState?
    ): String {
        return if (hasActualData(isOnline, liveState)) {
            "${actualOutputPercent(isOnline, liveState)}%"
        } else {
            "0%"
        }
    }

    fun actualPowerText(
        isOnline: Boolean,
        liveState: LightDeviceLiveState?
    ): String {
        return if (hasActualData(isOnline, liveState)) {
            liveState?.actualPowerText ?: "-- W"
        } else {
            "-- W"
        }
    }

    fun actualChannelPercent(
        isOnline: Boolean,
        liveState: LightDeviceLiveState?,
        semantic: LightChannelSemantic
    ): Int {
        if (!hasActualData(isOnline, liveState)) {
            return 0
        }

        return when (semantic) {
            LightChannelSemantic.RED,
            LightChannelSemantic.GREEN,
            LightChannelSemantic.BLUE,
            LightChannelSemantic.WHITE -> {
                liveState
                    ?.channelFor(semantic)
                    ?.valuePercent
                    ?.coerceIn(0, 100)
                    ?: 0
            }

            LightChannelSemantic.UNKNOWN -> {
                actualOutputPercent(
                    isOnline = isOnline,
                    liveState = liveState
                )
            }
        }
    }

    fun channelText(
        prefix: String,
        isOnline: Boolean,
        liveState: LightDeviceLiveState?,
        semantic: LightChannelSemantic
    ): String {
        if (!hasActualData(isOnline, liveState)) {
            return "$prefix --"
        }

        return "$prefix ${actualChannelPercent(isOnline, liveState, semantic)}%"
    }
}
