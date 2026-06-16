package com.aqua.aqualight.data.devices.light.programs.compiler

import com.aqua.aqualight.data.devices.light.curve.model.LightCurveChannelValues
import kotlin.math.roundToInt

/**
 * Evaluates the concrete controller point schedule at a minute of day.
 *
 * This mirrors the current ESP32 contract: the controller stores concrete LP
 * points per channel and transitions linearly between neighboring points. The
 * dashboard and editor preview must evaluate the compiled points instead of
 * re-applying Smooth/Natural metadata, otherwise the app would display a curve
 * different from the controller output.
 */
object LightProgramDeviceScheduleEvaluator {

    fun outputAtMinute(
        schedule: LightProgramDeviceSchedule,
        minuteOfDay: Int
    ): LightCurveChannelValues {
        val safeMinute = minuteOfDay.coerceIn(0, MINUTES_PER_DAY)

        var white = 0
        var red = 0
        var green = 0
        var blue = 0

        schedule.channels.forEach { channelSchedule ->
            val percent = percentAtMinute(
                points = channelSchedule.points,
                minuteOfDay = safeMinute
            )

            when (channelSchedule.channel) {
                LightProgramDeviceChannel.WHITE -> white = percent
                LightProgramDeviceChannel.RED -> red = percent
                LightProgramDeviceChannel.GREEN -> green = percent
                LightProgramDeviceChannel.BLUE -> blue = percent
            }
        }

        return LightCurveChannelValues(
            red = red,
            green = green,
            blue = blue,
            white = white
        ).normalized()
    }

    private fun percentAtMinute(
        points: List<LightProgramDevicePoint>,
        minuteOfDay: Int
    ): Int {
        if (points.isEmpty()) {
            return 0
        }

        val sortedPoints = points
            .sortedBy { point -> point.minuteOfDay }
            .dedupeSameMinuteKeepingLast()

        val first = sortedPoints.first()
        if (minuteOfDay <= first.minuteOfDay) {
            return first.percent.coerceIn(0, 100)
        }

        val last = sortedPoints.last()
        if (minuteOfDay >= last.minuteOfDay) {
            return last.percent.coerceIn(0, 100)
        }

        val nextIndex = sortedPoints.indexOfFirst { point ->
            point.minuteOfDay >= minuteOfDay
        }
        if (nextIndex <= 0) {
            return first.percent.coerceIn(0, 100)
        }

        val previous = sortedPoints[nextIndex - 1]
        val next = sortedPoints[nextIndex]

        if (minuteOfDay == next.minuteOfDay) {
            return next.percent.coerceIn(0, 100)
        }

        val duration = next.minuteOfDay - previous.minuteOfDay
        if (duration <= 0) {
            return next.percent.coerceIn(0, 100)
        }

        val t = (minuteOfDay - previous.minuteOfDay) / duration.toFloat()
        val value = previous.percent + ((next.percent - previous.percent) * t)
        return value.roundToInt().coerceIn(0, 100)
    }

    private fun List<LightProgramDevicePoint>.dedupeSameMinuteKeepingLast(): List<LightProgramDevicePoint> {
        val byMinute = linkedMapOf<Int, LightProgramDevicePoint>()
        forEach { point ->
            byMinute[point.minuteOfDay.coerceIn(0, MINUTES_PER_DAY)] = point.copy(
                minuteOfDay = point.minuteOfDay.coerceIn(0, MINUTES_PER_DAY),
                percent = point.percent.coerceIn(0, 100)
            )
        }
        return byMinute.values.toList()
    }

    private const val MINUTES_PER_DAY = 24 * 60
}
