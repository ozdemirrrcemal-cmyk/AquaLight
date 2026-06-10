package com.aqua.aqualight.data.devices.light.runtime

/**
 * Single policy for values that are allowed to be presented as real device output.
 *
 * Scheduled program calculations are targets/previews only. They must never be
 * promoted to actual output when the device is offline or live telemetry is stale.
 */
object LightActualDataPolicy {

    fun actualOutputPercent(
        liveState: LightDeviceLiveState
    ): Int {
        return if (liveState.hasLiveChannels) {
            liveState.actualOutputPercent.coerceIn(0, 100)
        } else {
            0
        }
    }

    fun actualOutputText(
        liveState: LightDeviceLiveState
    ): String {
        return if (liveState.hasLiveChannels) {
            "${actualOutputPercent(liveState)}%"
        } else {
            "--%"
        }
    }

    fun actualChannelPercent(
        liveState: LightDeviceLiveState,
        semantic: LightChannelSemantic
    ): Int {
        if (!liveState.hasLiveChannels) {
            return 0
        }

        return when (semantic) {
            LightChannelSemantic.RED,
            LightChannelSemantic.GREEN,
            LightChannelSemantic.BLUE,
            LightChannelSemantic.WHITE -> {
                liveState.channelFor(semantic)
                    ?.valuePercent
                    ?.coerceIn(0, 100)
                    ?: 0
            }

            LightChannelSemantic.UNKNOWN -> {
                actualOutputPercent(liveState)
            }
        }
    }

    fun hasActualData(
        liveState: LightDeviceLiveState
    ): Boolean {
        return liveState.hasLiveChannels
    }
}
