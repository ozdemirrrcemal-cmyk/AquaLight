package com.aqua.aqualight.data.devices.light.programs.preview

import com.aqua.aqualight.data.devices.light.curve.model.LightCurvePoint
import com.aqua.aqualight.data.devices.light.programs.compiler.LightProgramDevicePointExpander
import com.aqua.aqualight.data.devices.light.programs.compiler.LightProgramDeviceSchedule
import com.aqua.aqualight.data.devices.light.programs.compiler.LightProgramDeviceScheduleEvaluator
import com.aqua.aqualight.data.devices.light.programs.compiler.LightProgramPointExpansionOptions
import com.aqua.aqualight.data.devices.light.programs.model.LightProgramDraft
import kotlin.math.roundToInt

/**
 * Builds preview frames from controller-ready schedule points.
 *
 * This keeps editor preview, firmware upload and future live-preview behavior
 * aligned: the preview never applies a separate curve over the user's draft.
 * It first compiles the draft, then evaluates the compiled LP points exactly
 * like the controller point contract.
 */
object LightProgramPreviewEngine {

    const val DEFAULT_FRAME_INTERVAL_MILLIS = 250L

    fun compileSchedule(
        draft: LightProgramDraft,
        options: LightProgramPointExpansionOptions = LightProgramPointExpansionOptions()
    ): LightProgramDeviceSchedule {
        return LightProgramDevicePointExpander.expand(
            draft = draft,
            options = options
        )
    }

    fun frameAt(
        schedule: LightProgramDeviceSchedule,
        elapsedMillis: Long,
        previewDurationMillis: Long
    ): LightProgramPreviewFrame {
        val safeDuration = previewDurationMillis.coerceAtLeast(1L)
        val safeElapsed = elapsedMillis.coerceIn(0L, safeDuration)
        val ratio = safeElapsed / safeDuration.toFloat()
        val minute = (MINUTES_PER_DAY * ratio)
            .roundToInt()
            .coerceIn(0, MINUTES_PER_DAY)

        return LightProgramPreviewFrame(
            progressPercent = (ratio * 100f)
                .roundToInt()
                .coerceIn(0, 100),
            simulatedMinuteOfDay = minute,
            simulatedTime = pointForMinute(minute),
            outputValues = LightProgramDeviceScheduleEvaluator.outputAtMinute(
                schedule = schedule,
                minuteOfDay = minute
            )
        )
    }

    private fun pointForMinute(
        minuteOfDay: Int
    ): LightCurvePoint {
        val safeMinute = minuteOfDay.coerceIn(0, MINUTES_PER_DAY)
        if (safeMinute == MINUTES_PER_DAY) {
            return LightCurvePoint.of(24, 0)
        }

        return LightCurvePoint.of(
            hour = safeMinute / 60,
            minute = safeMinute % 60
        )
    }

    private const val MINUTES_PER_DAY = 24 * 60
}
