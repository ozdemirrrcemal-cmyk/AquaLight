package com.aqua.aqualight.data.devices.light.programs.compiler

import com.aqua.aqualight.data.devices.api.light.LightChannelValues
import com.aqua.aqualight.data.devices.api.light.LightProgram
import com.aqua.aqualight.data.devices.light.programs.model.LightCurveChannelValues
import com.aqua.aqualight.data.devices.light.programs.model.LightCurveTransitionMode
import com.aqua.aqualight.data.devices.light.programs.model.RepeatMode

data class CompiledLightProgramSchedule(
    val programId: String,
    val programName: String,
    val startMinute: Int,
    val peakStartMinute: Int,
    val peakEndMinute: Int,
    val endMinute: Int,
    val peakChannels: LightCurveChannelValues,
    val repeatMode: RepeatMode,
    val repeatDays: Set<Int>,
    val transitionMode: LightCurveTransitionMode,
    val points: List<CompiledLightProgramPoint>
) {
    val durationMinutes: Int
        get() = (endMinute - startMinute).coerceAtLeast(0)

    val peakOutputPercent: Int
        get() = maxOf(
            peakChannels.red,
            peakChannels.green,
            peakChannels.blue,
            peakChannels.white
        ).coerceIn(0, 100)

    fun outputAtMinute(
        minute: Int
    ): LightCurveChannelValues {
        if (points.isEmpty()) {
            return LightCurveChannelValues(red = 0, green = 0, blue = 0, white = 0)
        }

        val safeMinute = minute.coerceIn(0, LightCurveInterpolator.MINUTES_PER_DAY)
        val previous = points.lastOrNull { point -> point.minuteOfDay <= safeMinute }
        val next = points.firstOrNull { point -> point.minuteOfDay >= safeMinute }

        return when {
            previous == null -> points.first().channels
            next == null -> points.last().channels
            previous.minuteOfDay == next.minuteOfDay -> previous.channels
            else -> interpolate(
                previous = previous,
                next = next,
                minute = safeMinute
            )
        }.normalized()
    }

    fun toApiProgram(
        isActive: Boolean
    ): LightProgram {
        return LightProgram(
            id = programId,
            name = programName,
            isActive = isActive,
            startMinute = startMinute,
            peakStartMinute = peakStartMinute,
            peakEndMinute = peakEndMinute,
            endMinute = endMinute,
            channelValues = LightChannelValues(
                red = peakChannels.red,
                green = peakChannels.green,
                blue = peakChannels.blue,
                white = peakChannels.white
            ).normalized(),
            repeatDays = repeatDays
        )
    }

    private fun interpolate(
        previous: CompiledLightProgramPoint,
        next: CompiledLightProgramPoint,
        minute: Int
    ): LightCurveChannelValues {
        val progress = (minute - previous.minuteOfDay).toDouble() /
            (next.minuteOfDay - previous.minuteOfDay).toDouble()

        return LightCurveChannelValues(
            red = interpolateChannel(previous.channels.red, next.channels.red, progress),
            green = interpolateChannel(previous.channels.green, next.channels.green, progress),
            blue = interpolateChannel(previous.channels.blue, next.channels.blue, progress),
            white = interpolateChannel(previous.channels.white, next.channels.white, progress)
        )
    }

    private fun interpolateChannel(
        previous: Int,
        next: Int,
        progress: Double
    ): Int {
        return (previous + (next - previous) * progress)
            .toInt()
            .coerceIn(0, 100)
    }
}
