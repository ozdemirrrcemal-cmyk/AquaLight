package com.aqua.aqualight.data.devices.light.programs.device

import com.aqua.aqualight.data.devices.api.light.LightProgramWriteRequest
import java.util.zip.CRC32

/**
 * Stable checksum over the exact active schedule payload.
 *
 * This deliberately avoids JSON string checksuming because object key ordering
 * can change. The canonical form is deterministic: sorted channels, sorted
 * points and normalized percent/minute values.
 */
object LightProgramChecksumCalculator {

    fun checksum(
        request: LightProgramWriteRequest
    ): String {
        val canonical = canonicalString(request)
        val crc = CRC32()
        crc.update(canonical.toByteArray(Charsets.UTF_8))
        return "crc32:%08x".format(crc.value)
    }

    fun canonicalString(
        request: LightProgramWriteRequest
    ): String {
        return buildString {
            append("resumeAuto=")
            append(request.resumeAutoAfterWrite)
            append(';')

            request.channels
                .sortedBy { channel -> channel.firmwareChannelIndex }
                .forEach { channel ->
                    append("ch=")
                    append(channel.firmwareChannelIndex)
                    append(':')

                    channel.points
                        .sortedBy { point -> point.minuteOfDay }
                        .forEach { point ->
                            append(point.minuteOfDay.coerceIn(0, MINUTES_PER_DAY))
                            append('=')
                            append(point.percent.coerceIn(0, 100))
                            append(',')
                        }
                    append(';')
                }
        }
    }

    private const val MINUTES_PER_DAY = 24 * 60
}
