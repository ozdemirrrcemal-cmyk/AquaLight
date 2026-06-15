package com.aqua.aqualight.data.devices.light.programs.compiler

import com.aqua.aqualight.data.devices.light.programs.model.LightCurveChannelValues

data class CompiledLightProgramPoint(
    val minuteOfDay: Int,
    val channels: LightCurveChannelValues
) {
    val outputPercent: Int
        get() = maxOf(
            channels.red,
            channels.green,
            channels.blue,
            channels.white
        ).coerceIn(0, 100)

    val timeText: String
        get() {
            val minute = minuteOfDay.coerceIn(0, LightCurveInterpolator.MINUTES_PER_DAY)
            if (minute == LightCurveInterpolator.MINUTES_PER_DAY) {
                return "24:00"
            }
            return "%02d:%02d".format(minute / 60, minute % 60)
        }
}
