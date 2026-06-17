package com.aqua.aqualight.data.devices.light.programs.sync

import com.aqua.aqualight.data.devices.api.light.LightProgramWriteChannel
import com.aqua.aqualight.data.devices.api.light.LightProgramWritePoint
import com.aqua.aqualight.data.devices.api.light.LightProgramWriteRequest
import com.aqua.aqualight.data.devices.api.light.LightScheduleChannelState
import com.aqua.aqualight.data.devices.light.programs.device.LightProgramChecksumCalculator

/**
 * Converts a controller-read runtime LP schedule back into the same canonical
 * write contract used by program activation.
 *
 * The app compares checksums of this canonical request with checksums compiled
 * from saved local programs. This keeps dashboard/list sync decisions based on
 * the actual firmware schedule instead of UI-only active flags.
 */
object LightProgramRuntimeScheduleMapper {

    fun toWriteRequest(
        scheduleChannels: List<LightScheduleChannelState>,
        resumeAutoAfterWrite: Boolean = true
    ): LightProgramWriteRequest? {
        val channels = scheduleChannels
            .filter { channel -> channel.points.isNotEmpty() }
            .sortedBy { channel -> channel.index }
            .map { channel ->
                LightProgramWriteChannel(
                    firmwareChannelIndex = channel.index,
                    points = channel.points
                        .sortedBy { point -> point.minuteOfDay }
                        .map { point ->
                            LightProgramWritePoint(
                                minuteOfDay = point.minuteOfDay.coerceIn(0, MINUTES_PER_DAY),
                                percent = point.percent.coerceIn(0, 100)
                            )
                        }
                )
            }

        if (channels.isEmpty()) {
            return null
        }

        return LightProgramWriteRequest(
            channels = channels,
            resumeAutoAfterWrite = resumeAutoAfterWrite
        )
    }

    fun checksum(
        scheduleChannels: List<LightScheduleChannelState>
    ): String? {
        return toWriteRequest(scheduleChannels)
            ?.let { request ->
                LightProgramChecksumCalculator.checksum(request)
            }
    }

    fun totalPointCount(
        scheduleChannels: List<LightScheduleChannelState>
    ): Int {
        return scheduleChannels.sumOf { channel -> channel.points.size }
    }

    private const val MINUTES_PER_DAY = 24 * 60
}
