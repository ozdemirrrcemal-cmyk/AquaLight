package com.aqua.aqualight.data.devices.light.programs.compiler

import com.aqua.aqualight.data.devices.light.curve.model.LightCurveTransitionMode

/**
 * Firmware/API-neutral program output produced from a LightProgramDraft.
 *
 * The editor owns the user intent: four anchor times, channel targets and the
 * selected transition mode. This contract owns the controller-ready points.
 *
 * Current ESP32 firmware receives concrete LLight.Data[index].LP point lists.
 * It does not store a transition-mode field, so Smooth/Natural are expanded
 * into multiple time/value points by Android before sending.
 *
 * Future firmware/API can switch to NATIVE_TRANSITION without changing the
 * program editor screen.
 */
data class LightProgramDeviceSchedule(
    val transitionMode: LightCurveTransitionMode,
    val strategy: LightProgramDeviceTransitionStrategy,
    val channels: List<LightProgramDeviceChannelSchedule>
) {
    val totalPointCount: Int
        get() = channels.sumOf { channel -> channel.points.size }
}

data class LightProgramDeviceChannelSchedule(
    val channel: LightProgramDeviceChannel,
    val firmwareChannelIndex: Int,
    val points: List<LightProgramDevicePoint>
)

data class LightProgramDevicePoint(
    val minuteOfDay: Int,
    val percent: Int
)

enum class LightProgramDeviceChannel(
    val firmwareChannelIndex: Int
) {
    /** Current ESP32 LLight/LPWMChanelLED Data index 0. */
    WHITE(0),

    /** Current ESP32 LLight/LPWMChanelLED Data index 1. */
    RED(1),

    /** Current ESP32 LLight/LPWMChanelLED Data index 2. */
    GREEN(2),

    /** Current ESP32 LLight/LPWMChanelLED Data index 3. */
    BLUE(3)
}

enum class LightProgramDeviceTransitionStrategy {
    /**
     * Current firmware mode: convert Smooth/Natural into concrete time/value
     * points because the controller only interpolates between stored points.
     */
    EXPANDED_POINTS,

    /**
     * Future firmware mode: keep only four user anchors and let firmware/API
     * apply transition mode natively.
     */
    NATIVE_TRANSITION
}
