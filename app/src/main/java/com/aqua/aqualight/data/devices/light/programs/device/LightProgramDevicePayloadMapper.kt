package com.aqua.aqualight.data.devices.light.programs.device

import com.aqua.aqualight.data.devices.api.light.LightProgramWriteChannel
import com.aqua.aqualight.data.devices.api.light.LightProgramWritePoint
import com.aqua.aqualight.data.devices.api.light.LightProgramWriteRequest
import com.aqua.aqualight.data.devices.light.programs.compiler.LightProgramDeviceSchedule

/**
 * Converts the domain compiler output into the firmware write contract.
 */
object LightProgramDevicePayloadMapper {

    fun toPayload(
        schedule: LightProgramDeviceSchedule,
        resumeAutoAfterWrite: Boolean = true
    ): LightProgramDevicePayload {
        val request = LightProgramWriteRequest(
            channels = schedule.channels
                .sortedBy { channel -> channel.firmwareChannelIndex }
                .map { channel ->
                    LightProgramWriteChannel(
                        firmwareChannelIndex = channel.firmwareChannelIndex,
                        points = channel.points
                            .sortedBy { point -> point.minuteOfDay }
                            .map { point ->
                                LightProgramWritePoint(
                                    minuteOfDay = point.minuteOfDay.coerceIn(0, MINUTES_PER_DAY),
                                    percent = point.percent.coerceIn(0, 100)
                                )
                            }
                    )
                },
            resumeAutoAfterWrite = resumeAutoAfterWrite
        )

        return LightProgramDevicePayload(
            request = request,
            checksum = LightProgramChecksumCalculator.checksum(request)
        )
    }

    private const val MINUTES_PER_DAY = 24 * 60
}
