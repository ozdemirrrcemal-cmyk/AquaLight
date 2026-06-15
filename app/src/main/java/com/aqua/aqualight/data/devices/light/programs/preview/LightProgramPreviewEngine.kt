package com.aqua.aqualight.data.devices.light.programs.preview

import com.aqua.aqualight.data.devices.light.programs.compiler.CompiledLightProgramSchedule
import com.aqua.aqualight.data.devices.light.programs.compiler.LightCurveInterpolator
import com.aqua.aqualight.data.devices.light.programs.model.LightCurveChannelValues
import com.aqua.aqualight.data.devices.light.programs.model.LightCurvePoint

object LightProgramPreviewEngine {

    fun frameAt(
        schedule: CompiledLightProgramSchedule,
        elapsedMillis: Long,
        durationMillis: Long
    ): LightProgramPreviewFrame {
        val safeDuration = durationMillis.coerceAtLeast(1L)
        val progress = (elapsedMillis.toDouble() / safeDuration.toDouble())
            .coerceIn(0.0, 1.0)
        val minute = (LightCurveInterpolator.MINUTES_PER_DAY * progress)
            .toInt()
            .coerceIn(0, LightCurveInterpolator.MINUTES_PER_DAY)

        return LightProgramPreviewFrame(
            minuteOfDay = minute,
            time = LightCurvePoint.of(
                hour = if (minute == LightCurveInterpolator.MINUTES_PER_DAY) 24 else minute / 60,
                minute = if (minute == LightCurveInterpolator.MINUTES_PER_DAY) 0 else minute % 60
            ),
            channels = schedule.outputAtMinute(minute),
            progressPercent = (progress * 100.0).toInt().coerceIn(0, 100),
            isFinished = progress >= 1.0
        )
    }
}

data class LightProgramPreviewFrame(
    val minuteOfDay: Int,
    val time: LightCurvePoint,
    val channels: LightCurveChannelValues,
    val progressPercent: Int,
    val isFinished: Boolean
)
