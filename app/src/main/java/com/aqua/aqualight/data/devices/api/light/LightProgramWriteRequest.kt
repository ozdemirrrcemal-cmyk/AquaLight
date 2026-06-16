package com.aqua.aqualight.data.devices.api.light

/**
 * Firmware-facing request for writing one active Light program schedule.
 *
 * This model intentionally contains only controller-safe facts: channel index,
 * concrete LP points and whether the controller should resume automatic mode
 * after the upload. User-facing concepts such as draft name, transition labels,
 * repeat UI and local sync state stay in the programs domain layer.
 */
data class LightProgramWriteRequest(
    val channels: List<LightProgramWriteChannel>,
    val resumeAutoAfterWrite: Boolean = true
) {
    val totalPointCount: Int
        get() = channels.sumOf { channel -> channel.points.size }
}

data class LightProgramWriteChannel(
    val firmwareChannelIndex: Int,
    val points: List<LightProgramWritePoint>
)

data class LightProgramWritePoint(
    val minuteOfDay: Int,
    val percent: Int
)
